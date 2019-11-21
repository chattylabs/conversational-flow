package chattylabs.conversations;

import android.app.Application;
import android.content.Context;
import android.media.MediaPlayer;
import android.os.ConditionVariable;
import android.speech.tts.TextToSpeech;
import android.text.TextUtils;

import androidx.annotation.Keep;
import androidx.annotation.RawRes;
import androidx.annotation.WorkerThread;

import com.google.api.gax.core.FixedExecutorProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.texttospeech.v1beta1.AudioConfig;
import com.google.cloud.texttospeech.v1beta1.AudioEncoding;
import com.google.cloud.texttospeech.v1beta1.ListVoicesResponse;
import com.google.cloud.texttospeech.v1beta1.SsmlVoiceGender;
import com.google.cloud.texttospeech.v1beta1.SynthesisInput;
import com.google.cloud.texttospeech.v1beta1.SynthesizeSpeechResponse;
import com.google.cloud.texttospeech.v1beta1.TextToSpeechClient;
import com.google.cloud.texttospeech.v1beta1.TextToSpeechSettings;
import com.google.cloud.texttospeech.v1beta1.VoiceSelectionParams;
import com.google.protobuf.ByteString;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import chattylabs.android.commons.HtmlUtils;
import chattylabs.android.commons.StringUtils;
import chattylabs.android.commons.Tag;
import chattylabs.android.commons.internal.ILogger;

public final class GoogleSpeechSynthesizer extends BaseSpeechSynthesizer {
    private static final String TAG = Tag.make("GoogleSpeechSynthesizer");

    // Resources
    private final Application application;
    private final ConditionVariable mCondVar = new ConditionVariable();
    private BaseSynthesizerUtteranceListener utteranceListener;
    private TextToSpeechClient tts;
    private VoiceSelectionParams voice;
    private AudioConfig audioConfig;

    @Keep
    public GoogleSpeechSynthesizer(Application application,
                                   ComponentConfig configuration,
                                   AndroidAudioManager audioManager,
                                   AndroidBluetooth bluetooth,
                                   ILogger logger) {
        super(configuration, audioManager, bluetooth, logger);
        this.release();
        this.application = application;
    }

    @WorkerThread
    @Override
    public void checkStatus(SynthesizerListener.OnStatusChecked listener) {
        logger.i(TAG, "checkStatus and check language");
        // At this stage, we only want to check whether the api works and the language is available
        try (TextToSpeechClient ttsClient = generateFromRawFile(
                application, getConfiguration().getGoogleCredentialsResourceFile())) {
            ListVoicesResponse response = ttsClient.listVoices(getDefaultLanguageCode());
            if (response.getVoicesCount() > 0) {
                listener.execute(SynthesizerListener.Status.AVAILABLE);
            } else {
                shutdown();
                listener.execute(SynthesizerListener.Status.LANGUAGE_NOT_SUPPORTED_ERROR);
            }
        } catch (Exception e) {
            logger.logException(e);
            shutdown();
            listener.execute(SynthesizerListener.Status.NOT_AVAILABLE_ERROR);
        }
    }

    @Override
    public void prune() {
        if (isEmpty()) shutdown();
        else {
            logger.w(TAG, "prune tts...");
            stop();
            destroyTTS();
        }
    }

    @Override
    public void shutdown() {
        logger.w(TAG, "shutting down");
        stop();
        release();
    }

    private TextToSpeechClient generateFromRawFile(Context context,
                                                   @RawRes int rawResourceId) throws IOException {
        final InputStream stream = context.getResources().openRawResource(rawResourceId);
        return TextToSpeechClient.create(TextToSpeechSettings.newBuilder()
                .setExecutorProvider(FixedExecutorProvider.create(Executors.newScheduledThreadPool(
                        Math.max(2, Math.min(Runtime.getRuntime().availableProcessors() - 1, 4))
                )))
                .setCredentialsProvider(() -> GoogleCredentials.fromStream(stream)).build());
    }

    private void destroyTTS() {
        if (!isTtsNull()) {
            try {
                tts.close();
                tts.shutdown();
                tts.awaitTermination(2, TimeUnit.SECONDS);
                logger.v(TAG, "destroyed");
            } catch (Exception ignored) {
            }
            tts = null;
        }
    }

    @Override
    void prepare(SynthesizerListener.OnPrepared onPrepared) {
        if (isTtsNull()) {
            setReady(false);
            logger.i(TAG, "creating new instance of Google TextToSpeechClient.class");
            try {
                setReady(true);
                this.tts = generateFromRawFile(
                        application, getConfiguration().getGoogleCredentialsResourceFile());
                this.audioConfig = AudioConfig.newBuilder().setAudioEncoding(AudioEncoding.MP3).build();
                setupLanguage();
                utteranceListener = new BaseSynthesizerUtteranceListener(application, this);
                logger.i(TAG, "Google TextToSpeechClient.class new instance created");
                onPrepared.execute(SynthesizerListener.Status.SUCCESS);
            } catch (Exception e) {
                logger.logException(e);
                prune();
                onPrepared.execute(SynthesizerListener.Status.ERROR);
            }
        } else if (isReady()) {
            setupLanguage();
            onPrepared.execute(SynthesizerListener.Status.SUCCESS);
        }
    }

    @Override
    void executeOnEngineReady(String utteranceId, String text) {
        String finalText = HtmlUtils.from(text).toString();

        for (TextFilter filter : getFilters()) {
            logger.v(TAG, "[%s] - apply filter: %s", utteranceId, filter);
            finalText = filter.apply(finalText);
        }

        if (finalText.length() > TextToSpeech.getMaxSpeechInputLength()) {
            String[] split = StringUtils.split(finalText, TextToSpeech.getMaxSpeechInputLength());
            for (String item : split) {
                play(utteranceId, item);
            }
        } else {
            play(utteranceId, finalText);
        }
    }

    @Override
    void playSilence(String utteranceId, long durationInMillis) {
        utteranceListener.onStart(utteranceId);
        mCondVar.block(durationInMillis);
        utteranceListener.onDone(utteranceId);
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
    public void stop() {
        logger.w(TAG, "stopping");
        super.stop();
        destroyTTS();
        getAudioManager().abandonAudioFocus(getConfiguration().isAudioExclusiveRequiredForSynthesizer());
    }

    @Override
    public void release() {
        super.release();
        voice = null;
        audioConfig = null;
    }

    private String getErrorType(int error) {
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

    private void setupLanguage() {
        this.voice = VoiceSelectionParams.newBuilder()
                .setLanguageCode(getDefaultLanguageCode())
                .setSsmlGender(SsmlVoiceGender.NEUTRAL).build();
    }

    private String getDefaultLanguageCode() {
        Locale speechLanguage = getConfiguration().getSpeechLanguage();
        final Locale locale = speechLanguage != null ? speechLanguage : Locale.getDefault();
        final StringBuilder language = new StringBuilder(locale.getLanguage());
        final String country = locale.getCountry();
        if (!TextUtils.isEmpty(country)) {
            language.append("-");
            language.append(country);
        }
        return language.toString();
    }

    private void play(String utteranceId, String text) {
        logger.i(TAG, "[%s] - reading out loud: \"%s\"", utteranceId, text);
        utteranceListener.onStart(utteranceId);
        prepare(status -> {
            if (status == SynthesizerListener.Status.SUCCESS) {

                SynthesisInput input = SynthesisInput.newBuilder()
                        .setText(text)
                        .build();
                SynthesizeSpeechResponse response = tts.synthesizeSpeech(input, voice, audioConfig);
                //destroyTTS(); ??

                // Get the audio contents from the response
                ByteString audioContents = response.getAudioContent();

                logger.d(TAG, "audio: %s", audioContents);

                try {
                    FileOutputStream fos = new FileOutputStream(createTempFile(application));
                    fos.write(audioContents.toByteArray());
                    fos.close();
                    handleSynthesizedFile(application, utteranceListener, utteranceId);
                } catch (Exception ex) {
                    logger.logException(ex);
                }

            } else {
                logger.e(TAG, "[%s] - internal playTextNow status ERROR ", utteranceId);
                utteranceListener.onError(utteranceId, SynthesizerListener.Status.ERROR);
            }
        });
    }
}
