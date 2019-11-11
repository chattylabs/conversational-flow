package chattylabs.conversations;

import android.app.Application;
import android.content.Context;
import android.media.MediaPlayer;
import android.os.ConditionVariable;
import android.speech.tts.TextToSpeech;

import androidx.annotation.Keep;
import androidx.annotation.RawRes;
import androidx.annotation.WorkerThread;

import android.text.TextUtils;

import com.chattylabs.android.commons.HtmlUtils;
import com.chattylabs.android.commons.StringUtils;
import com.chattylabs.android.commons.Tag;
import com.chattylabs.android.commons.internal.ILogger;
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

public final class GoogleSpeechSynthesizer extends BaseSpeechSynthesizer {

    private static final int MAX_SPEECH_TIME_SEC = 60;
    private static final String TAG = Tag.make("GoogleSpeechSynthesizer");

    // Resources
    private final Application application;
    private TextToSpeechClient tts;
    private VoiceSelectionParams voice;
    private AudioConfig audioConfig;
    private final ConditionVariable mCondVar = new ConditionVariable();

    @Keep
    public GoogleSpeechSynthesizer(Application application,
                            ComponentConfig configuration,
                            AndroidAudioManager audioManager,
                            AndroidBluetooth bluetooth,
                            ILogger logger) {
        super(configuration, audioManager, bluetooth, logger, TAG);
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
                listener.execute(SynthesizerListener.Status.LANGUAGE_NOT_SUPPORTED_ERROR);
            }
        } catch (Exception e) {
            logger.logException(e);
            shutdown();
            listener.execute(SynthesizerListener.Status.NOT_AVAILABLE_ERROR);
        }
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

    @Override
    SynthesizerUtteranceListener createDelegateUtteranceListener() {
        return new BaseSynthesizerUtteranceListener(this,
                BaseSynthesizerUtteranceListener.Mode.DELEGATE, TAG);
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
        destroyTts();
    }

    private void destroyTts() {
        if (!isTtsNull()) {
            try {
                tts.close();
                tts.shutdown();
                tts.awaitTermination(2, TimeUnit.SECONDS);
                logger.v(TAG, "destroyed");
            } catch (Exception ignored) {}
            tts = null;
        }
    }

    @Override
    public void shutdown() {
        logger.w(TAG, "shutting down");
        this.stop();
        release();
    }

    @Override
    public void release() {
        super.release();
        tts = null;
        voice = null;
        audioConfig = null;
    }

    @Override
    void prepare(SynthesizerListener.OnPrepared onPrepared) {
        if (isTtsNull()) {
            setReady(false);
            logger.i(TAG, "creating new instance of TextToSpeechClient.class");
            try {
                logger.i(TAG, "new instance created");
                setReady(true);
                this.tts = generateFromRawFile(
                        application, getConfiguration().getGoogleCredentialsResourceFile());
                this.audioConfig = AudioConfig.newBuilder().setAudioEncoding(AudioEncoding.MP3).build();
                setupLanguage();
                setSynthesizerUtteranceListener(createBaseUtteranceListener());
                onPrepared.execute(SynthesizerListener.Status.SUCCESS);
            } catch (Exception e) {
                logger.logException(e);
                shutdown();
                onPrepared.execute(SynthesizerListener.Status.ERROR);
            }
        } else if (isReady()) {
            setupLanguage();
            onPrepared.execute(SynthesizerListener.Status.SUCCESS);
        }
    }

    private void setupLanguage() {
        this.voice = VoiceSelectionParams.newBuilder()
                .setLanguageCode(getDefaultLanguageCode())
                .setSsmlGender(SsmlVoiceGender.NEUTRAL).build();
    }

    private SynthesizerUtteranceListener createBaseUtteranceListener() {
        return new BaseSynthesizerUtteranceListener(this, TAG) {

            @Override
            protected String getErrorType(int error) {
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
        };
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

    @Override
    void executeOnEngineReady(String utteranceId, String text) {
        //noinspection ConstantConditions
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
        getSynthesizerUtteranceListener().onStart(utteranceId);
        mCondVar.block(durationInMillis);
        getSynthesizerUtteranceListener().onDone(utteranceId);
    }

    private void play(String utteranceId, String text) {
        logger.i(TAG, "[%s] - reading out loud: \"%s\"", utteranceId, text);
        getSynthesizerUtteranceListener().onStart(utteranceId);
        prepare(status -> {
            if (status == SynthesizerListener.Status.SUCCESS) {

                SynthesisInput input = SynthesisInput.newBuilder()
                        .setText(text)
                        .build();
                SynthesizeSpeechResponse response = tts.synthesizeSpeech(input, voice, audioConfig);
                //destroyTts(); ??

                // Get the audio contents from the response
                ByteString audioContents = response.getAudioContent();

                logger.d(TAG, "audio: %s", audioContents);

                try {
                    FileOutputStream fos = new FileOutputStream(createTempFile(application));
                    fos.write(audioContents.toByteArray());
                    fos.close();

                    handleSynthesizedFile(application, getSynthesizerUtteranceListener(), utteranceId);
                } catch (Exception ex) {
                    logger.logException(ex);
                }

            } else {
                logger.e(TAG, "[%s] - internal playText status ERROR ", utteranceId);
                getSynthesizerUtteranceListener().onError(utteranceId, SynthesizerListener.Status.ERROR);
            }
        });
    }
}
