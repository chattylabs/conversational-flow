package com.chattylabs.sdk.android.voice;

import android.app.Application;
import android.media.MediaPlayer;
import android.speech.tts.TextToSpeech;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import com.chattylabs.sdk.android.common.HtmlUtils;
import com.chattylabs.sdk.android.common.StringUtils;
import com.chattylabs.sdk.android.common.Tag;
import com.chattylabs.sdk.android.common.internal.ILogger;
import com.google.cloud.texttospeech.v1beta1.AudioConfig;
import com.google.cloud.texttospeech.v1beta1.AudioEncoding;
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
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Locale;

import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.OnSynthesizerInitialised;
import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.SYNTHESIZER_AVAILABLE;
import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.SYNTHESIZER_NOT_AVAILABLE_ERROR;
import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.SynthesizerListenerContract;

public final class GoogleSpeechSynthesizer extends BaseSpeechSynthesizer {

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
                            MediaPlayer mediaPlayer,
                            BluetoothSco bluetoothSco,
                            ILogger logger) {
        super(configuration, audioHandler, bluetoothSco, logger);
        this.application = application;
        this.mediaPlayer = mediaPlayer;
        this.release();
    }

    @Override
    public void setup(OnSynthesizerInitialised onSynthesizerInitialised) {
        try (TextToSpeechClient ttsClient = TextToSpeechClient.create()) {
            this.tts = ttsClient;
            this.audioConfig = AudioConfig.newBuilder().setAudioEncoding(AudioEncoding.MP3).build();
            //TODO: Check for language
            onSynthesizerInitialised.execute(SYNTHESIZER_AVAILABLE);
        } catch (Exception e) {
            shutdown();
            onSynthesizerInitialised.execute(SYNTHESIZER_NOT_AVAILABLE_ERROR);
        }
    }

    @Override
    public <T extends SynthesizerListenerContract> void playSilence(long durationInMillis, String queueId, T... listeners) {

    }

    @Override
    public <T extends SynthesizerListenerContract> void playSilence(long durationInMillis, T... listeners) {

    }

    @Override
    UtteranceListener createUtteranceListener(@NonNull SynthesizerListenerContract... listeners) {
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
    void initTts(TextToSpeech.OnInitListener onInitListener,
                 OnSynthesizerInitialised onCheckLanguageInit) {
        if (isTtsNull()) {
            setReady(false);
            utteranceListener = initUtterancesListener(onCheckLanguageInit);
            // Audio !
            //tts.setOnUtteranceProgressListener(utteranceListener);
            logger.i(TAG, "GOOGLE TTS - creating new instance");
            try (TextToSpeechClient ttsClient = TextToSpeechClient.create()) {
                logger.i(TAG, "TTS - new instance created");
                setReady(true);
                this.tts = ttsClient;
                this.voice = VoiceSelectionParams.newBuilder()
                        .setLanguageCode(getDefaultLanguageCode())
                        .setSsmlGender(SsmlVoiceGender.NEUTRAL)
                        .build();
                this.audioConfig = AudioConfig.newBuilder().setAudioEncoding(AudioEncoding.MP3).build();
                mediaPlayer.setOnCompletionListener(mp -> {
                    getUtteranceListener().
                });
                onInitListener.onInit(status);
            } catch (Exception e) {
                shutdown();
                onSynthesizerInitialised.execute(SYNTHESIZER_NOT_AVAILABLE_ERROR);
            }
        }
        else if (isReady()) {
            onInitListener.onInit(TextToSpeech.SUCCESS);
        }
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

    private void checkLanguage(OnSynthesizerInitialised onInit, boolean fromUtterance) {

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
        }, null);
    }
}
