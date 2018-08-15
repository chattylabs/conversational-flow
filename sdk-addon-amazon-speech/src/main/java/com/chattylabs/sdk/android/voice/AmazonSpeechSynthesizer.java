package com.chattylabs.sdk.android.voice;

import android.annotation.TargetApi;
import android.app.Application;
import android.media.MediaPlayer;
import android.os.Build;
import android.support.annotation.NonNull;

import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.mobile.client.AWSMobileClient;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.polly.AmazonPollyPresigningClient;
import com.amazonaws.services.polly.model.DescribeVoicesRequest;
import com.amazonaws.services.polly.model.DescribeVoicesResult;
import com.amazonaws.services.polly.model.LanguageCode;
import com.amazonaws.services.polly.model.OutputFormat;
import com.amazonaws.services.polly.model.SynthesizeSpeechPresignRequest;
import com.amazonaws.services.polly.model.Voice;
import com.chattylabs.sdk.android.common.internal.ILogger;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

public class AmazonSpeechSynthesizer extends BaseSpeechSynthesizer {

    private static final int MAX_SPEECH_TIME_SEC = 60;
    private static final String TAG = "AMAZON_POLLY";

    private final Application mApplication;
    private MediaPlayer mMediaPlayer;
    private AWSMobileClient.InitializeBuilder mAmazonMobileClient;
    private AmazonPollyPresigningClient mAmazonSpeechClient;
    private Voice mDefaultVoices;

    public AmazonSpeechSynthesizer(Application application,
                                   ComponentConfig configuration,
                                   AndroidAudioManager audioManager,
                                   BluetoothSco bluetoothSco,
                                   ILogger logger) {
        super(configuration, audioManager, bluetoothSco, logger);
        mApplication = application;
    }

    @Override
    String getTag() {
        return TAG;
    }

    @Override
    void prepare(SynthesizerListener.OnPrepared onSynthesizerPrepared) {
        if (isTtsNull()) {
            // TODO: need to set it from config. in this case AWSMobileClient should not even need to be initialized.
            final CognitoCachingCredentialsProvider credentialsProvider = new CognitoCachingCredentialsProvider(
                    mApplication.getApplicationContext(),
                    "", // Identity pool ID
                    Regions.US_EAST_2
            );

            setSynthesizerUtteranceListener(createUtterancesListener());
            mAmazonSpeechClient = new AmazonPollyPresigningClient(credentialsProvider);
            onSynthesizerPrepared.execute(SynthesizerListener.Status.SUCCESS);
        } else {
            onSynthesizerPrepared.execute(SynthesizerListener.Status.SUCCESS);
        }
    }

    private SynthesizerUtteranceListener createUtterancesListener() {
        return new AmazonSpeechSynthesizerAdapter() {
            private long timestamp;
            private TimerTask task;
            private Timer timer;

            @Override
            public void clearTimeout(String utteranceId) {
                logger.v(getTag(), "AMAZON TTS[%s] - utterance timeout cleared", utteranceId);
                if (task != null) task.cancel();
                if (timer != null) timer.cancel();
            }

            @Override
            public void startTimeout(String utteranceId) {
                logger.i(getTag(), "AMAZON TTS[%s] - started timeout", utteranceId);
                timer = new Timer();
                task = new TimerTask() {
                    @Override
                    public void run() {
                        if (!isTtsSpeaking()) {
                            logger.e(getTag(), "AMAZON TTS[%s] - is null or not speaking && reached timeout",
                                    utteranceId);
                            stop();
                            onError(utteranceId, SynthesizerListener.Status.TIMEOUT);
                        } else {
                            if ((System.currentTimeMillis() - timestamp) > TimeUnit.SECONDS.toMillis(MAX_SPEECH_TIME_SEC)) {
                                logger.e(getTag(), "AMAZON TTS[%s] - exceeded %s seconds", utteranceId,
                                        MAX_SPEECH_TIME_SEC);
                                stop();
                                onError(utteranceId, SynthesizerListener.Status.TIMEOUT);
                            } else {
                                clearTimeout(utteranceId);
                                startTimeout(utteranceId);
                            }
                        }
                    }
                };
                timer.schedule(task, TimeUnit.SECONDS.toMillis(10));
            }

            @Override
            public void onStart(String utteranceId) {
                logger.v(getTag(), "AMAZON TTS[%s] - on start", utteranceId);

                startTimeout(utteranceId);
                timestamp = System.currentTimeMillis();

                if (getListenersMap().size() > 0) {
                    SynthesizerUtteranceListener listener = getListenersMap().get(utteranceId);
                    if (listener != null) {
                        listener.onStart(utteranceId);
                    }
                }
            }

            @Override
            public void onDone(String utteranceId) {
                clearTimeout(utteranceId);
                logger.v(getTag(), "AMAZON TTS[%s] - on done <%s> - check for Empty Queue", utteranceId, getCurrentQueueId());
                moveToNextQueueIfNeeded();
                if (isEmpty()) {
                    stop();
                    logger.i(getTag(), "AMAZON TTS[%s] - on done <%s> - Stream Finished", utteranceId, getCurrentQueueId());
                }
                setSpeaking(false);
                if (getListenersMap().size() > 0) {
                    SynthesizerUtteranceListener listener = removeListener(utteranceId);
                    logger.v(getTag(), "AMAZON TTS[%s] - on done <%s> - execute listener.onDone", utteranceId, getCurrentQueueId());
                    if (listener != null) {
                        listener.onDone(utteranceId);
                    }
                }
            }

            @Override
            @TargetApi(Build.VERSION_CODES.LOLLIPOP)
            public void onError(String utteranceId, int errorCode) {
                clearTimeout(utteranceId);
                logger.e(getTag(), "AMAZON TTS[%s] - on error <%s> -> stop timeout", utteranceId, getCurrentQueueId());
                // TODO: Print Error for error code.
//                logger.e(getTag(), "AMAZON TTS[%s] - error code: %s", utteranceId, getErrorType(errorCode));
                logger.e(getTag(), "AMAZON TTS[%s] - error code: %s", utteranceId, "" + errorCode);
                moveToNextQueueIfNeeded();
                if (isEmpty()) {
                    stop();
                    logger.i(getTag(), "AMAZON TTS[%s] - ERROR <%s> - Stream Finished", utteranceId, getCurrentQueueId());
                }
                setSpeaking(false);
                if (getListenersMap().size() > 0 && getListenersMap().containsKey(utteranceId)) {
                    SynthesizerUtteranceListener listener = removeListener(utteranceId);
                    shutdown();
                    if (listener != null) {
                        listener.onError(utteranceId, errorCode);
                    }
                } else shutdown();
            }
        };
    }

    @Override
    void executeOnTtsReady(String utteranceId, String text, HashMap<String, String> params) {
        prepare(synthesizerStatus -> {
            final URL audioUrl = mAmazonSpeechClient.getPresignedSynthesizeSpeechUrl(
                    new SynthesizeSpeechPresignRequest().withVoiceId(mDefaultVoices.getId())
                            .withOutputFormat(OutputFormat.Mp3)
                            .withLanguageCode(LanguageCode.EnGB)
                            .withText(text)
            );

            try {
                mMediaPlayer = new MediaPlayer();
                mMediaPlayer.setDataSource(audioUrl.toString());
                mMediaPlayer.setOnCompletionListener(mediaPlayer -> {
                    closeMediaPlayer();
                    onListenersAvailable(utteranceId, Operation.REMOVE,
                            listener -> listener.onDone(utteranceId));
                });
                mMediaPlayer.setOnErrorListener((mediaPlayer, what, extra) -> {
                    closeMediaPlayer();
                    onListenersAvailable(utteranceId, Operation.REMOVE,
                            listener -> listener.onError(utteranceId, extra));
                    return true;
                });
                mMediaPlayer.setOnPreparedListener(mediaPlayer -> {
                    onListenersAvailable(utteranceId, Operation.GET, listener -> listener.onStart(utteranceId));
                    mMediaPlayer.start();
                });
                mMediaPlayer.prepare();

            } catch (IOException ex) {
                closeMediaPlayer();
            }
        });
    }

    private void onListenersAvailable(String utteranceId,
                                      Operation operation,
                                      OnUtteranceListenerAvailable callback) {
        final SynthesizerUtteranceListener utteranceListener = operation.get(utteranceId, getListenersMap());
        if (utteranceListener != null) {
            callback.onAvailable(utteranceListener);
        }
    }

    private void closeMediaPlayer() {
        if (mMediaPlayer != null) {
            try {
                mMediaPlayer.stop();
            } catch (IllegalStateException ex) {
                // Ignore this exception...
            }
            mMediaPlayer.release();
        }
        mMediaPlayer = null;
    }

    @Override
    void playSilence(String utteranceId, long durationInMillis) {
        // Not required now
    }

    @Override
    HashMap<String, String> buildParams(String uId, String s) {
        return null;
    }

    @Override
    boolean isTtsNull() {
        return mAmazonSpeechClient == null;
    }

    @Override
    boolean isTtsSpeaking() {
        return mMediaPlayer != null && mMediaPlayer.isPlaying();
    }

    @Override
    SynthesizerUtteranceListener createUtteranceListener(SynthesizerListener[] listeners) {
        return new AmazonSpeechSynthesizerAdapter() {
            @Override
            public void onStart(String utteranceId) {
                _getOnStartedListener().execute(utteranceId);
            }

            @Override
            public void onDone(String utteranceId) {
                _getOnDoneListener().execute(utteranceId);
            }

            @Override
            public void onError(String utteranceId, int errorCode) {
                _getOnErrorListener().execute(utteranceId, errorCode);
            }
        };
    }

    @Override
    public void setup(SynthesizerListener.OnSetup onSynthesizerSetup) {
        prepare(synthesizerStatus -> {
            // TODO: Language should be derived from config
            final DescribeVoicesResult voicesResult = mAmazonSpeechClient.describeVoices(new DescribeVoicesRequest()
                    .withLanguageCode(LanguageCode.EnGB));
            if (!voicesResult.getVoices().isEmpty()) {
                mDefaultVoices = voicesResult.getVoices().get(0);
                onSynthesizerSetup.execute(SynthesizerListener.Status.SUCCESS);
            } else {
                onSynthesizerSetup.execute(SynthesizerListener.Status.LANGUAGE_NOT_SUPPORTED_ERROR);
            }
        });
    }

    @Override
    public void stop() {
        super.stop();
        closeMediaPlayer();
    }

    @Override
    public void shutdown() {
        closeMediaPlayer();
        if (mAmazonSpeechClient != null) {
            mAmazonSpeechClient.shutdown();
        }
        mAmazonSpeechClient = null;
        mAmazonMobileClient = null;
    }

    private interface OnUtteranceListenerAvailable {
        void onAvailable(SynthesizerUtteranceListener listener);
    }

    private enum Operation {
        GET {
            @Override
            SynthesizerUtteranceListener get(String utteranceId,
                                             @NonNull Map<String, SynthesizerUtteranceListener> listenerMap) {
                return listenerMap.get(utteranceId);
            }
        },
        REMOVE {
            @Override
            SynthesizerUtteranceListener get(String utteranceId,
                                             @NonNull Map<String, SynthesizerUtteranceListener> listenerMap) {
                return listenerMap.remove(utteranceId);
            }
        };

        abstract SynthesizerUtteranceListener get(String utteranceId,
                                                  @NonNull Map<String, SynthesizerUtteranceListener> listenerMap);
    }
}