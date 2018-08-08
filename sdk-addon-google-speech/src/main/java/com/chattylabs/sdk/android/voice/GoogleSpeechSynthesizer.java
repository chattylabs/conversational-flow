package com.chattylabs.sdk.android.voice;

import android.annotation.TargetApi;
import android.app.Application;
import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.ConditionVariable;
import android.speech.tts.TextToSpeech;
import android.support.annotation.NonNull;
import android.support.annotation.RawRes;
import android.support.annotation.WorkerThread;
import android.text.TextUtils;

import com.chattylabs.sdk.android.common.HtmlUtils;
import com.chattylabs.sdk.android.common.StringUtils;
import com.chattylabs.sdk.android.common.Tag;
import com.chattylabs.sdk.android.common.internal.ILogger;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.core.FixedExecutorProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.texttospeech.v1beta1.AudioConfig;
import com.google.cloud.texttospeech.v1beta1.AudioEncoding;
import com.google.cloud.texttospeech.v1beta1.ListVoicesResponse;
import com.google.cloud.texttospeech.v1beta1.SsmlVoiceGender;
import com.google.cloud.texttospeech.v1beta1.SynthesisInput;
import com.google.cloud.texttospeech.v1beta1.SynthesizeSpeechResponse;
import com.google.cloud.texttospeech.v1beta1.TextToSpeechClient;
import com.google.cloud.texttospeech.v1beta1.TextToSpeechSettings;
import com.google.cloud.texttospeech.v1beta1.VoiceSelectionParams;
import com.google.cloud.texttospeech.v1beta1.stub.TextToSpeechStubSettings;
import com.google.protobuf.ByteString;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class GoogleSpeechSynthesizer extends BaseSpeechSynthesizer {

    private static final int MAX_SPEECH_TIME_SEC = 60;

    private final String TAG = Tag.make("GoogleSpeechSynthesizer");

    // Resources
    private final Application application;
    private TextToSpeechClient tts;
    private VoiceSelectionParams voice;
    private AudioConfig audioConfig;
    private MediaPlayer mediaPlayer;
    private final ConditionVariable mCondVar = new ConditionVariable();
    private boolean completed; //released
    private int extraCode; //released

    GoogleSpeechSynthesizer(Application application,
                            ComponentConfig configuration,
                            AndroidAudioManager audioManager,
                            BluetoothSco bluetoothSco,
                            ILogger logger) {
        super(configuration, audioManager, bluetoothSco, logger);
        this.application = application;
        this.release();
    }

    @WorkerThread
    @Override
    public void setup(SynthesizerListener.OnSetup onSynthesizerSetup) {
        logger.i(TAG, "GOOGLE TTS - setup and check language");
        try (TextToSpeechClient ttsClient = generateFromRawFile(
                application, getConfiguration().getGoogleCredentialsResourceFile())) {
            ListVoicesResponse response = ttsClient.listVoices(getDefaultLanguageCode());
            ttsClient.shutdownNow();
            ttsClient.awaitTermination(2, TimeUnit.SECONDS);
            if (response.getVoicesCount() > 0) {
                onSynthesizerSetup.execute(SynthesizerListener.Status.AVAILABLE);
            } else {
                onSynthesizerSetup.execute(SynthesizerListener.Status.LANGUAGE_NOT_SUPPORTED_ERROR);
            }
        } catch (Exception e) {
            logger.logException(e);
            shutdown();
            onSynthesizerSetup.execute(SynthesizerListener.Status.NOT_AVAILABLE_ERROR);
        }
    }

    private TextToSpeechClient generateFromRawFile(Context context, @RawRes int rawResourceId) throws IOException {
        final InputStream stream = context.getResources().openRawResource(rawResourceId);
        final GoogleCredentials credentials = ServiceAccountCredentials.fromStream(stream)
                .createScoped(TextToSpeechStubSettings.getDefaultServiceScopes());
        return TextToSpeechClient.create(TextToSpeechSettings.newBuilder()
                .setExecutorProvider(
                        FixedExecutorProvider.create(
                            Executors.newScheduledThreadPool(
                                Math.max(2, Math.min(Runtime.getRuntime().availableProcessors() - 1, 4))
                            )
                        )
                )
                .setCredentialsProvider(
                        FixedCredentialsProvider.create(credentials)
                ).build());
    }

    @Override
    SynthesizerUtteranceListener createUtteranceListener(@NonNull SynthesizerListener... listeners) {
        return new GoogleSpeechSynthesizerAdapter()
        {
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
    boolean isTtsNull() {
        return tts == null;
    }

    @Override
    boolean isTtsSpeaking() {
        return mediaPlayer != null && mediaPlayer.isPlaying();
    }

    @Override
    String getTag() {
        return TAG;
    }

    @Override
    public void stop() {
        logger.w(TAG, "GOOGLE TTS - stopping");
        super.stop();
        destroyTts();
        finishPlayer();
    }

    private void finishPlayer() {
        try {
            if (mediaPlayer != null) mediaPlayer.stop();
            logger.v(TAG, "GOOGLE TTS - stopped");
        } catch (IllegalStateException ex) {
            // Do nothing, the player is already stopped
        }
        mCondVar.open();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    @Override
    public void shutdown() {
        logger.w(TAG, "GOOGLE TTS - shutting down");
        this.stop();
        release();
    }

    private void destroyTts() {
        if (!isTtsNull()) {
            try {
                tts.close();
                tts.shutdown();
                tts.awaitTermination(2 ,TimeUnit.SECONDS);
                logger.v(TAG, "GOOGLE TTS - destroyed");
            } catch (Exception ignored) {}
            tts = null;
        }
    }

    @Override
    public void  release() {
        super.release();
        tts = null;
        voice = null;
        audioConfig = null;
        completed = true;
        extraCode = 0;
    }

    @Override
    HashMap<String, String> buildParams(@NonNull String utteranceId, @NonNull String audioStream) {
        return null;
    }

    @Override
    void initTts(SynthesizerListener.OnInitialised onSynthesizerInitialised) {
        if (isTtsNull()) {
            setReady(false);
            logger.i(TAG, "GOOGLE TTS - creating new instance of TextToSpeechClient.class");
            try (TextToSpeechClient ttsClient = generateFromRawFile(
                    application, getConfiguration().getGoogleCredentialsResourceFile())) {
                logger.i(TAG, "GOOGLE TTS - new instance created");
                setReady(true);
                this.tts = ttsClient;
                this.audioConfig = AudioConfig.newBuilder().setAudioEncoding(AudioEncoding.MP3).build();
                setupLanguage();
                setSynthesizerUtteranceListener(createUtterancesListener());
                onSynthesizerInitialised.execute(SynthesizerListener.Status.SUCCESS);
            } catch (Exception e) {
                logger.logException(e);
                shutdown();
                onSynthesizerInitialised.execute(SynthesizerListener.Status.ERROR);
            }
        }
        else if (isReady()) {
            setupLanguage();
            onSynthesizerInitialised.execute(SynthesizerListener.Status.SUCCESS);
        }
    }

    private void setupLanguage() {
        this.voice = VoiceSelectionParams.newBuilder()
                .setLanguageCode(getDefaultLanguageCode())
                .setSsmlGender(SsmlVoiceGender.NEUTRAL).build();
    }

    private SynthesizerUtteranceListener createUtterancesListener() {
        return new GoogleSpeechSynthesizerAdapter() {
            private long timestamp;
            private TimerTask task;
            private Timer timer;

            @Override
            public void clearTimeout(String utteranceId) {
                logger.v(getTag(), "GOOGLE TTS[%s] - utterance timeout cleared", utteranceId);
                if (task != null) task.cancel();
                if (timer != null) timer.cancel();
            }

            @Override
            public void startTimeout(String utteranceId) {
                logger.i(getTag(), "ANDROID TTS[%s] - started timeout", utteranceId);
                timer = new Timer();
                task = new TimerTask() {
                    @Override
                    public void run() {
                        if (!isTtsSpeaking()) {
                            logger.e(getTag(), "GOOGLE TTS[%s] - is null or not speaking && reached timeout", utteranceId);
                            stop();
                            onError(utteranceId, SynthesizerListener.Status.TIMEOUT);
                        }
                        else {
                            if ((System.currentTimeMillis() - timestamp) > TimeUnit.SECONDS.toMillis(MAX_SPEECH_TIME_SEC)) {
                                logger.e(getTag(), "GOOGLE TTS[%s] - exceeded %s seconds", utteranceId, MAX_SPEECH_TIME_SEC);
                                stop();
                                onError(utteranceId, SynthesizerListener.Status.TIMEOUT);
                            }
                            else {
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
                logger.v(getTag(), "GOOGLE TTS[%s] - on start", utteranceId);

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
                logger.v(getTag(), "GOOGLE TTS[%s] - on done <%s> - check for Empty Queue", utteranceId, getCurrentQueueId());
                moveToNextQueueIfNeeded();
                if (isEmpty()) {
                    stop();
                    logger.i(getTag(), "GOOGLE TTS[%s] - on done <%s> - Stream Finished", utteranceId, getCurrentQueueId());
                }
                setSpeaking(false);
                if (getListenersMap().size() > 0) {
                    SynthesizerUtteranceListener listener = removeListener(utteranceId);
                    logger.v(getTag(), "GOOGLE TTS[%s] - on done <%s> - execute listener.onDone", utteranceId, getCurrentQueueId());
                    if (listener != null) {
                        listener.onDone(utteranceId);
                    }
                }
            }

            @Override
            @TargetApi(Build.VERSION_CODES.LOLLIPOP)
            public void onError(String utteranceId, int errorCode) {
                clearTimeout(utteranceId);
                logger.e(getTag(), "GOOGLE TTS[%s] - on error <%s> -> stop timeout", utteranceId, getCurrentQueueId());
                logger.e(getTag(), "GOOGLE TTS[%s] - error code: %s", utteranceId, getErrorType(errorCode));
                moveToNextQueueIfNeeded();
                if (isEmpty()) {
                    stop();
                    logger.i(getTag(), "GOOGLE TTS[%s] - ERROR <%s> - Stream Finished", utteranceId, getCurrentQueueId());
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

    private String getDefaultLanguageCode() {
        final Locale locale = Locale.getDefault();
        final StringBuilder language = new StringBuilder(locale.getLanguage());
        final String country = locale.getCountry();
        if (!TextUtils.isEmpty(country)) {
            language.append("-");
            language.append(country);
        }
        return language.toString();
    }

    @Override
    void executeOnTtsReady(String utteranceId, String text, HashMap<String, String> params) {
        //noinspection ConstantConditions
        String finalText = HtmlUtils.from(text).toString();

        for (TextFilter filter : getFilters()) {
            logger.v(TAG, "GOOGLE TTS[%s] - apply filter: %s", utteranceId, filter);
            finalText = filter.apply(finalText);
        }

        if (finalText.length() > TextToSpeech.getMaxSpeechInputLength()) {
            String[] split = StringUtils.split(finalText, TextToSpeech.getMaxSpeechInputLength());
            for (String item : split) {
                play(utteranceId, item, params);
            }
        }
        else {
            play(utteranceId, finalText, params);
        }
    }

    @Override
    void playSilence(String utteranceId, long durationInMillis) {
        getSynthesizerUtteranceListener()._getOnStartedListener().execute(utteranceId);
        mCondVar.block(durationInMillis);
        getSynthesizerUtteranceListener()._getOnDoneListener().execute(utteranceId);
    }

    @WorkerThread
    private void play(String utteranceId, String text, HashMap<String, String> params) {
        logger.i(TAG, "GOOGLE TTS[%s] - reading out loud: \"%s\"", utteranceId, text);
        getSynthesizerUtteranceListener().onStart(utteranceId);
        initTts(status -> {
            if (status == SynthesizerListener.Status.SUCCESS) {
                mCondVar.close();
                completed = false;

                SynthesisInput input = SynthesisInput.newBuilder()
                        .setText(text)
                        .build();
                SynthesizeSpeechResponse response = tts.synthesizeSpeech(input, voice, audioConfig);
                destroyTts();

                // Get the audio contents from the response
                ByteString audioContents = response.getAudioContent();

                logger.d(getTag(), "audio: %s", audioContents);
                logger.d(getTag(), "audio string: %s", audioContents.toStringUtf8());

                extraCode = -1;

                try {
                    File tempMp3 = File.createTempFile("output", "mp3",
                            application.getCacheDir());
                    tempMp3.deleteOnExit();
                    //noinspection ResultOfMethodCallIgnored
                    tempMp3.setReadable(true);
                    FileOutputStream fos = new FileOutputStream(tempMp3);
                    fos.write(audioContents.toByteArray());
                    fos.close();

                    mediaPlayer = MediaPlayer.create(application, Uri.fromFile(tempMp3));
                    if (mediaPlayer == null) {
                        getSynthesizerUtteranceListener().onError(utteranceId, SynthesizerListener.Status.UNKNOWN_ERROR);
                        return;
                    }

                    mediaPlayer.setOnCompletionListener(mp -> {
                        completed = true;
                        mCondVar.open();
                    });
                    mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                        extraCode = extra;
                        mCondVar.open();
                        return true;
                    });
                    mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                    mediaPlayer.start();
                    mCondVar.block();
                    finishPlayer();
                } catch (Exception ex) {
                    logger.logException(ex);
                    mCondVar.open();
                }

                if (completed) {
                    getSynthesizerUtteranceListener().onDone(utteranceId);
                } else {
                    // TODO: When I release the timeout, since I already run onError, it might be called twice
                    getSynthesizerUtteranceListener().onError(utteranceId, extraCode);
                }
            }
            else {
                logger.e(TAG, "GOOGLE TTS[%s] - internal playText status ERROR ", utteranceId);
                getSynthesizerUtteranceListener().onError(utteranceId, SynthesizerListener.Status.ERROR);
            }
        });
    }

    public static String getErrorType(int error) {
        switch (error) {
            case MediaPlayer.MEDIA_ERROR_IO:
                return "MEDIA_ERROR_IO";
            case MediaPlayer.MEDIA_ERROR_MALFORMED:
                return "MEDIA_ERROR_MALFORMED";
            case MediaPlayer.MEDIA_ERROR_UNSUPPORTED:
                return "MEDIA_ERROR_UNSUPPORTED";
            case MediaPlayer.MEDIA_ERROR_TIMED_OUT:
                return "MEDIA_ERROR_TIMED_OUT";
            default:
                return "ERROR_UNKNOWN";
        }
    }
}
