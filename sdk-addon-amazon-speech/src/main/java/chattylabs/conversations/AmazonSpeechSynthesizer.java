package chattylabs.conversations;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.media.MediaPlayer;
import android.os.ConditionVariable;

import androidx.annotation.NonNull;

import com.amazonaws.services.polly.AmazonPollyPresigningClient;
import com.amazonaws.services.polly.model.DescribeVoicesRequest;
import com.amazonaws.services.polly.model.DescribeVoicesResult;
import com.amazonaws.services.polly.model.LanguageCode;
import com.amazonaws.services.polly.model.OutputFormat;
import com.amazonaws.services.polly.model.SynthesizeSpeechPresignRequest;
import com.amazonaws.services.polly.model.Voice;

import java.io.IOException;
import java.net.URL;
import java.util.Map;

import chattylabs.android.commons.Tag;
import chattylabs.android.commons.internal.ILogger;
import kotlin.jvm.functions.Function1;

public final class AmazonSpeechSynthesizer extends BaseSpeechSynthesizer {
    private static final String TAG = Tag.make("AmazonSpeechSynthesizer");

    private final Application mApplication;
    private final LanguageCode mLanguageCode;
    private final ConditionVariable mCondVar = new ConditionVariable();
    private BaseSynthesizerUtteranceListener utteranceListener;
    private MediaPlayer mMediaPlayer;
    private AmazonPollyPresigningClient mAmazonSpeechClient;
    private Voice mDefaultVoices;

    public AmazonSpeechSynthesizer(Application application,
                                   ComponentConfig configuration,
                                   AndroidAudioManager audioManager,
                                   AndroidBluetooth bluetooth,
                                   ILogger logger) {
        super(configuration, audioManager, bluetooth, logger);
        mApplication = application;
        mLanguageCode = LanguageUtil.getDeviceLanguageCode(configuration.getSpeechLanguage());
    }

    @Override
    public void getSpeechDuration(Context context, String text, Function1<Integer, Void> callback) {

    }

    private void prepare(SynthesizerListener.OnPrepared onSynthesizerPrepared) {
        if (isTtsNull()) {
//            final AWSConfiguration configuration = AWSConfiguration.getConfiguration(mApplication.getResources()
//                    .openRawResource(R.raw.aws_configuration));
//
//            final CognitoCachingCredentialsProvider credentialsProvider = new CognitoCachingCredentialsProvider(
//                    mApplication.getApplicationContext(),
//                    configuration.getPoolId(),
//                    Regions.fromName(configuration.getRegion())
//            );

//            utteranceListener = new BaseSynthesizerUtteranceListener(application, this);
//            mAmazonSpeechClient = new AmazonPollyPresigningClient(credentialsProvider);
//            onSynthesizerPrepared.execute(SynthesizerListener.Status.SUCCESS);
        } else {
            onSynthesizerPrepared.execute(SynthesizerListener.Status.SUCCESS);
        }
    }

    @Override
    void executeOnEngineReady(String utteranceId, String text) {
        prepare(synthesizerStatus -> {

            if (synthesizerStatus != SynthesizerListener.Status.SUCCESS) {
                return;
            }

            String finalText = text;

            for (Filter filter : getFilters()) {
                logger.v(TAG, "[%s] - apply filter: %s", utteranceId, filter);
                finalText = filter.apply(finalText);
            }

            final URL audioUrl = mAmazonSpeechClient.getPresignedSynthesizeSpeechUrl(
                    new SynthesizeSpeechPresignRequest().withVoiceId(mDefaultVoices.getId())
                            .withOutputFormat(OutputFormat.Mp3)
                            .withLanguageCode(mLanguageCode)
                            .withText(finalText)
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

    @Override
    void playSilence(String utteranceId, long durationInMillis) {
        utteranceListener.onStart(utteranceId);
        mCondVar.block(durationInMillis);
        utteranceListener.onDone(utteranceId);
    }

    private boolean isTtsNull() {
        return mAmazonSpeechClient == null;
    }

    @Override
    boolean isTtsSpeaking() {
        return mMediaPlayer != null && mMediaPlayer.isPlaying();
    }



    @Override
    public void stop() {
        super.stop();
        closeMediaPlayer();
    }

    @Override
    void forceDestroyTTS() {
        // no implementation
    }

    private void onListenersAvailable(String utteranceId,
                                      Operation operation,
                                      OnUtteranceListenerAvailable callback) {
//        final SynthesizerUtteranceListener utteranceListener = operation.get(utteranceId, getListeners());
//        if (utteranceListener != null) {
//            callback.onAvailable(utteranceListener);
//        }
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

    @Override public void setVoice(String gender) {

    }

    @Override public void setDefaultVoice() {

    }

    @Override
    public void checkStatus(SynthesizerListener.OnStatusChecked listener) {
        prepare(synthesizerStatus -> {
            final DescribeVoicesResult voicesResult = mAmazonSpeechClient.describeVoices(new DescribeVoicesRequest()
                    .withLanguageCode(mLanguageCode));
            if (!voicesResult.getVoices().isEmpty()) {
                mDefaultVoices = voicesResult.getVoices().get(0);
                listener.execute(SynthesizerListener.Status.SUCCESS);
            } else {
                listener.execute(SynthesizerListener.Status.LANGUAGE_NOT_SUPPORTED_ERROR);
            }
        });
    }

    @Override public void loadInstallation(Activity activity, SynthesizerListener.OnStatusChecked listener) {

    }

    @Override
    public void prune() {

    }

    @Override
    public void shutdown() {
        closeMediaPlayer();
        if (mAmazonSpeechClient != null) {
            mAmazonSpeechClient.shutdown();
        }
        mAmazonSpeechClient = null;
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

    private interface OnUtteranceListenerAvailable {
        void onAvailable(SynthesizerUtteranceListener listener);
    }
}