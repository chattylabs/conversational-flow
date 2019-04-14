package chattylabs.conversations;

import android.app.Application;
import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.ConditionVariable;
import android.speech.tts.TextToSpeech;
import androidx.annotation.NonNull;
import androidx.annotation.RawRes;
import androidx.annotation.WorkerThread;
import android.text.TextUtils;

import com.chattylabs.android.commons.HtmlUtils;
import com.chattylabs.android.commons.StringUtils;
import com.chattylabs.android.commons.Tag;
import com.chattylabs.android.commons.internal.ILogger;
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
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class GoogleSpeechSynthesizer extends BaseSpeechSynthesizer {

    private static final String TAG = Tag.make("GoogleSpeechSynthesizer");
    private static final String LOG_LABEL = "GOOGLE TTS";

    // Resources
    private final Application application;
    private TextToSpeechClient tts;
    private VoiceSelectionParams voice;
    private AudioConfig audioConfig;
    private MediaPlayer mediaPlayer;
    private final ConditionVariable mCondVar = new ConditionVariable();
    private boolean completed; //released
    private int extraCode; //released

    public GoogleSpeechSynthesizer(Application application,
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
    public void checkStatus(SynthesizerListener.OnStatusChecked listener) {
        logger.i(TAG, "GOOGLE TTS - checkStatus and check language");
        try (TextToSpeechClient ttsClient = generateFromRawFile(
                application, getConfiguration().getGoogleCredentialsResourceFile())) {
            ListVoicesResponse response = ttsClient.listVoices(getDefaultLanguageCode());
            ttsClient.shutdownNow();
            ttsClient.awaitTermination(2, TimeUnit.SECONDS);
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
        return new BaseSynthesizerUtteranceListener(this) {
            @Override
            String getTtsLogLabel() {
                return LOG_LABEL;
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
                tts.awaitTermination(2, TimeUnit.SECONDS);
                logger.v(TAG, "GOOGLE TTS - destroyed");
            } catch (Exception ignored) {
            }
            tts = null;
        }
    }

    @Override
    public void release() {
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
    void prepare(SynthesizerListener.OnPrepared onSynthesizerPrepared) {
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
                onSynthesizerPrepared.execute(SynthesizerListener.Status.SUCCESS);
            } catch (Exception e) {
                logger.logException(e);
                shutdown();
                onSynthesizerPrepared.execute(SynthesizerListener.Status.ERROR);
            }
        } else if (isReady()) {
            setupLanguage();
            onSynthesizerPrepared.execute(SynthesizerListener.Status.SUCCESS);
        }
    }

    private void setupLanguage() {
        this.voice = VoiceSelectionParams.newBuilder()
                .setLanguageCode(getDefaultLanguageCode())
                .setSsmlGender(SsmlVoiceGender.NEUTRAL).build();
    }

    private SynthesizerUtteranceListener createUtterancesListener() {
        return new BaseSynthesizerUtteranceListener(this,
                BaseSynthesizerUtteranceListener.Mode.DELEGATE) {

            @Override
            String getTtsLogLabel() {
                return LOG_LABEL;
            }

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
        } else {
            play(utteranceId, finalText, params);
        }
    }

    @Override
    void playSilence(String utteranceId, long durationInMillis) {
        getSynthesizerUtteranceListener().onStart(utteranceId);
        mCondVar.block(durationInMillis);
        getSynthesizerUtteranceListener().onDone(utteranceId);
    }

    private void play(String utteranceId, String text, HashMap<String, String> params) {
        logger.i(TAG, "GOOGLE TTS[%s] - reading out loud: \"%s\"", utteranceId, text);
        getSynthesizerUtteranceListener().onStart(utteranceId);
        prepare(status -> {
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
            } else {
                logger.e(TAG, "GOOGLE TTS[%s] - internal playText status ERROR ", utteranceId);
                getSynthesizerUtteranceListener().onError(utteranceId, SynthesizerListener.Status.ERROR);
            }
        });
    }
}
