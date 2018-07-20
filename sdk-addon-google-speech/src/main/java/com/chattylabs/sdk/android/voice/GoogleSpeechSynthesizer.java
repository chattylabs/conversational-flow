package com.chattylabs.sdk.android.voice;

import android.annotation.TargetApi;
import android.app.Application;
import android.media.MediaPlayer;
import android.os.Build;
import android.speech.tts.TextToSpeech;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;
import android.text.TextUtils;

import com.chattylabs.sdk.android.common.HtmlUtils;
import com.chattylabs.sdk.android.common.StringUtils;
import com.chattylabs.sdk.android.common.Tag;
import com.chattylabs.sdk.android.common.internal.ILogger;
import com.google.cloud.texttospeech.v1beta1.AudioConfig;
import com.google.cloud.texttospeech.v1beta1.AudioEncoding;
import com.google.cloud.texttospeech.v1beta1.ListVoicesResponse;
import com.google.cloud.texttospeech.v1beta1.SsmlVoiceGender;
import com.google.cloud.texttospeech.v1beta1.SynthesisInput;
import com.google.cloud.texttospeech.v1beta1.SynthesizeSpeechResponse;
import com.google.cloud.texttospeech.v1beta1.TextToSpeechClient;
import com.google.cloud.texttospeech.v1beta1.VoiceSelectionParams;
import com.google.protobuf.ByteString;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.*;
import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.OnSynthesizerInitialised;
import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.SynthesizerListener;

public final class GoogleSpeechSynthesizer extends BaseSpeechSynthesizer {

    private static final int MAX_SPEECH_TIME = 60;

    private final String TAG = Tag.make("GoogleSpeechSynthesizer");

    // Resources
    private final Application application;
    private TextToSpeechClient tts;
    private VoiceSelectionParams voice;
    private AudioConfig audioConfig;
    private MediaPlayer mediaPlayer;

    GoogleSpeechSynthesizer(Application application,
                            VoiceConfig configuration,
                            AndroidAudioHandler audioHandler,
                            BluetoothSco bluetoothSco,
                            MediaPlayer mediaPlayer,
                            ILogger logger) {
        super(configuration, audioHandler, bluetoothSco, logger);
        this.application = application;
        this.mediaPlayer = mediaPlayer;
        this.release();
    }

    @WorkerThread
    @Override
    public void setup(OnSynthesizerSetup onSynthesizerSetup) {
        logger.i(TAG, "TTS - setup and check language");
        try (TextToSpeechClient ttsClient = TextToSpeechClient.create()) {
            ListVoicesResponse response = ttsClient.listVoices(getDefaultLanguageCode());
            if (response.getVoicesCount() > 0) {
                onSynthesizerSetup.execute(SynthesizerListener.AVAILABLE);
            } else {
                onSynthesizerSetup.execute(SynthesizerListener.LANGUAGE_NOT_SUPPORTED_ERROR);
            }
        } catch (Exception e) {
            shutdown();
            onSynthesizerSetup.execute(SynthesizerListener.NOT_AVAILABLE_ERROR);
        }
    }

    @Override
    public <T extends SynthesizerListener> void playSilence(long durationInMillis, String queueId, T... listeners) {

    }

    @Override
    public <T extends SynthesizerListener> void playSilence(long durationInMillis, T... listeners) {

    }

    @Override
    UtteranceListener createUtteranceListener(@NonNull SynthesizerListener... listeners) {
        return new GoogleSpeechSynthesizerAdapter()
        {
            @Override
            public void onStart(String utteranceId) {
                getOnStartedListener().execute(utteranceId);
            }

            @Override
            public void onDone(String utteranceId) {
                getOnDoneListener().execute(utteranceId);
            }

            @Override
            public void onError(String utteranceId, int errorCode) {
                getOnErrorListener().execute(utteranceId, errorCode);
            }
        };
    }

    @Override
    boolean isTtsNull() {
        return tts == null;
    }

    @Override
    boolean isTtsSpeaking() {
        return !isTtsNull() && mediaPlayer.isPlaying();
    }

    @Override
    String getTag() {
        return TAG;
    }

    @Override
    public void shutdown() {
        logger.w(TAG, "TTS - shutting down");
        this.stop();
        if (!isTtsNull()) {
            try {
                logger.v(TAG, "TTS - shutting down");
                tts.shutdown();
            } catch (Exception ignored) {}
            logger.v(TAG, "TTS - destroyed");
        }
        // Release and reset all resources
        release();
    }

    @Override
    public void stop() {
        super.stop();
        logger.w(TAG, "TTS - Stopping..");
        // Shutdown text to speech
        if (!isTtsNull()) {
            try {
                mediaPlayer.stop();
                logger.v(TAG, "TTS - TextToSpeech stopped");
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void release() {
        super.release();
        mediaPlayer.release();
        tts = null;
        voice = null;
        audioConfig = null;
    }

    @Override
    HashMap<String, String> buildParams(@NonNull String utteranceId, @NonNull String audioStream) {
        return null;
    }

    @Override
    void initTts(OnSynthesizerInitialised onSynthesizerInitialised) {
        if (isTtsNull()) {
            setReady(false);
            // Audio !
            logger.i(TAG, "GOOGLE TTS - creating new instance");
            try (TextToSpeechClient ttsClient = TextToSpeechClient.create()) {
                logger.i(TAG, "TTS - new instance created");
                setReady(true);
                this.tts = ttsClient;
                this.audioConfig = AudioConfig.newBuilder().setAudioEncoding(AudioEncoding.MP3).build();
                this.voice = VoiceSelectionParams.newBuilder()
                        .setLanguageCode(getDefaultLanguageCode())
                        .setSsmlGender(SsmlVoiceGender.NEUTRAL)
                        .build();
                setUtteranceListener(createUtterancesListener());
                onSynthesizerInitialised.execute(SynthesizerListener.SUCCESS);
            } catch (Exception e) {
                shutdown();
                onSynthesizerInitialised.execute(SynthesizerListener.ERROR);
            }
        }
        else if (isReady()) {
            onSynthesizerInitialised.execute(SynthesizerListener.SUCCESS);
        }
    }

    private UtteranceListener createUtterancesListener() {
        return new GoogleSpeechSynthesizerAdapter() {
            private long timestamp;
            private TimerTask task;
            private Timer timer;

            @Override
            public void clearTimeout() {
                logger.i(getTag(), "TTS - utterance timeout cleared!");
                if (task != null) task.cancel();
                if (timer != null) timer.cancel();
            }

            @Override
            public void startTimeout(String utteranceId) {
                logger.i(getTag(), "TTS - started timeout! - " + utteranceId);
                timer = new Timer();
                task = new TimerTask() {
                    @Override
                    public void run() {
                        if (isTtsNull() || !isTtsSpeaking()) {
                            logger.e(getTag(), "TTS - is null or not speaking && reached timeout! - " + utteranceId);
                            stop();
                            onDone(utteranceId);
                        }
                        else {
                            if ((System.currentTimeMillis() - timestamp) > TimeUnit.SECONDS.toMillis(MAX_SPEECH_TIME)) {
                                logger.e(getTag(), "TTS - exceeded " + MAX_SPEECH_TIME + " sec! - " + utteranceId);
                                stop();
                                onDone(utteranceId);
                            }
                            else {
                                clearTimeout();
                                startTimeout(utteranceId);
                            }
                        }
                    }
                };
                timer.schedule(task, TimeUnit.SECONDS.toMillis(10));
            }

            @Override
            public void onStart(String utteranceId) {
                logger.v(getTag(), "TTS - on start -> utterance - " + utteranceId + " - listener size: " + getListenersMap().size());

                startTimeout(utteranceId);
                timestamp = System.currentTimeMillis();

                if (getListenersMap().size() > 0) {
                    UtteranceListener listener = getListenersMap().get(utteranceId);
                    if (listener != null) {
                        listener.onStart(utteranceId);
                    }
                }
            }

            @Override
            public void onDone(String utteranceId) {
                logger.v(getTag(), "TTS - check For Empty Queue <" + getCurrentQueueId() + "> - " + utteranceId);
                clearTimeout();
                setSpeaking(false);
                checkForEmptyCurrentQueue();
                if (isCurrentQueueEmpty()) {
                    stop();
                    logger.i(getTag(), "TTS - Stream Finished. - " + utteranceId);
                }
                if (getListenersMap().size() > 0) {
                    UtteranceListener listener;
                    synchronized (lock) {
                        listener = getListenersMap().remove(utteranceId);
                    }
                    logger.v(getTag(), "TTS - Execute listener onDone <" + getCurrentQueueId() + "> - " + utteranceId);
                    listener.onDone(utteranceId);
                }
            }

            @Override
            @TargetApi(Build.VERSION_CODES.LOLLIPOP)
            public void onError(String utteranceId, int errorCode) {
                clearTimeout();
                logger.e(getTag(), "TTS - on error -> stop timeout -> utterance - " + utteranceId + " - listener size: " + getListenersMap().size());
                logger.e(getTag(), "TTS - error code: " + getErrorType(errorCode) + " - " + utteranceId);
                setSpeaking(false);
                checkForEmptyCurrentQueue();
                if (isCurrentQueueEmpty()) {
                    stop();
                    logger.i(getTag(), "TTS - ERROR - Stream Finished. - " + utteranceId);
                }
                if (getListenersMap().size() > 0) {
                    UtteranceListener listener;
                    synchronized (lock) {
                        listener = getListenersMap().remove(utteranceId);
                    }
                    if (listener != null) {
                        listener.onError(utteranceId, errorCode);
                    }
                }
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
            logger.v(TAG, "TTS - apply filter: " + filter + " - " + utteranceId);
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

    }

    private void play(String utteranceId, String text, HashMap<String, String> params) {
        logger.i(TAG, "TTS - reading out loud: \"" + text + "\" - " + utteranceId);
        initTts(status -> {
            if (status == TextToSpeech.SUCCESS) {
                SynthesisInput input = SynthesisInput.newBuilder()
                        .setText(text)
                        .build();
                SynthesizeSpeechResponse response = tts.synthesizeSpeech(input, voice, audioConfig);

                // Get the audio contents from the response
                ByteString audioContents = response.getAudioContent();

                try {
                    // create temp file that will hold byte array
                    File tempMp3 = File.createTempFile("output", "mp3",
                            application.getCacheDir());
                    tempMp3.deleteOnExit();
                    FileOutputStream fos = new FileOutputStream(tempMp3);
                    fos.write(audioContents.toByteArray());
                    fos.close();

                    mediaPlayer.reset();
                    FileInputStream fis = new FileInputStream(tempMp3);
                    mediaPlayer.setDataSource(fis.getFD());

                    mediaPlayer.setOnCompletionListener(mp -> {
                        getUtteranceListener().onDone(utteranceId);
                    });
                    mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                        if (what == MediaPlayer.MEDIA_ERROR_SERVER_DIED) {
                            mediaPlayer.reset();
                            mediaPlayer.release();
                            // TODO: re-instantiate mediaPlayer?
                        }
                        getUtteranceListener().onError(utteranceId, extra);
                        return true;
                    });
                    mediaPlayer.setOnPreparedListener(mp -> {
                        getUtteranceListener().onStart(utteranceId);
                    });

                    mediaPlayer.prepare();
                    mediaPlayer.start();
                } catch (IOException ex) {
                    logger.logException(ex);
                    shutdown();
                }
            }
            else {
                logger.e(TAG, "TTS - playText status ERROR - " + utteranceId);
                shutdown();
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
