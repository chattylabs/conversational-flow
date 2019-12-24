package chattylabs.conversations;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;

import androidx.annotation.Keep;

import java.io.File;
import java.util.Locale;

import chattylabs.android.commons.StringUtils;
import chattylabs.android.commons.Tag;
import chattylabs.android.commons.internal.ILogger;
import kotlin.collections.ArraysKt;
import kotlin.collections.CollectionsKt;
import kotlin.jvm.functions.Function1;

import static chattylabs.conversations.SynthesizerListener.Status.AVAILABLE;
import static chattylabs.conversations.SynthesizerListener.Status.ERROR;
import static chattylabs.conversations.SynthesizerListener.Status.LANGUAGE_NOT_SUPPORTED_ERROR;
import static chattylabs.conversations.SynthesizerListener.Status.NOT_AVAILABLE_ERROR;
import static chattylabs.conversations.SynthesizerListener.Status.SUCCESS;
import static chattylabs.conversations.SynthesizerListener.Status.UNKNOWN_ERROR;

public final class AndroidSpeechSynthesizer extends BaseSpeechSynthesizer {
    private static final String TAG = Tag.make("AndroidSpeechSynthesizer");

    private static final String CHECKING_UTTERANCE_ID = "<CHECKING_UTTERANCE_ID>";
    private static final String TESTING_STRING = "%%TESTING_STRING%%";

    // Resources
    private Application application;
    private TextToSpeech tts; // released
    private BaseSynthesizerUtteranceListener utteranceListener; // released

    @Keep
    public AndroidSpeechSynthesizer(Application application,
                                    ComponentConfig configuration,
                                    AndroidAudioManager audioManager,
                                    AndroidBluetooth bluetooth,
                                    ILogger logger) {
        super(configuration, audioManager, bluetooth, logger);
        this.release();
        this.application = application;
    }

    @Override
    public void checkStatus(SynthesizerListener.OnStatusChecked listener) {
        logger.i(TAG, "check TTS status");
        try {
            prepare(status -> checkTTS(listener));
        } catch (Exception e) {
            logger.logException(e);
            shutdown();
            listener.execute(NOT_AVAILABLE_ERROR);
        }
    }

    @Override
    public void prune() {
        if (isOnQueue() && isQueueEmpty()) shutdown();
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

    private void checkTTS(SynthesizerListener.OnStatusChecked listener) {
        try {
            // Try speaking an empty text!
            SynthesizerListener onDone = (SynthesizerListener.OnDone) utteranceId -> {
                logger.v(TAG, "[%s] - on done <%s>", utteranceId, getCurrentQueueId());
                getAudioManager().abandonAudioFocus(getConfiguration().isAudioExclusiveRequiredForSynthesizer());
                listener.execute(AVAILABLE);
            };
            SynthesizerListener onError = (SynthesizerListener.OnError) (utteranceId, errorCode) -> {
                determineError(listener, errorCode);
            };
            super.playText(TESTING_STRING, DEFAULT_QUEUE_ID, CHECKING_UTTERANCE_ID, onDone, onError);
        } catch (Exception e) {
            logger.e(TAG, "error when checking TTS: %s", e.getMessage());
            // Otherwise it reports the TextToSpeechStatus to the Callback
            determineError(listener, TextToSpeech.ERROR_NOT_INSTALLED_YET);
        }
    }

    @Override
    public void loadInstallation(Activity activity, SynthesizerListener.OnStatusChecked listener) {
        final Locale speechLanguage = getConfiguration().getSpeechLanguage();
        final TextToSpeech[] _tts = { null };
        TextToSpeech.OnInitListener ttsListener = status -> {
                int result = _tts[0].isLanguageAvailable(speechLanguage);
                _tts[0].shutdown();
                if (ArraysKt.contains(new int[] {TextToSpeech.LANG_AVAILABLE,
                                                 TextToSpeech.LANG_COUNTRY_AVAILABLE,
                                                 TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE}, result)) {
                    if (isOnQueue() && isQueueEmpty()) shutdown();
                    else getAudioManager().abandonAudioFocus(getConfiguration().isAudioExclusiveRequiredForSynthesizer());
                    listener.execute(AVAILABLE);
                } else {
                    shutdown();
                    logger.e(TAG, "LANGUAGE_NOT_SUPPORTED_ERROR");
                    listener.execute(LANGUAGE_NOT_SUPPORTED_ERROR);
                }
        };
        application.registerActivityLifecycleCallbacks(new ActivityLifecycleCallback() {
            @Override
            public void onActivityResumed(Activity activity) {
                application.unregisterActivityLifecycleCallbacks(this);
                _tts[0] = new TextToSpeech(application, ttsListener);
            }
        });
        Intent newIntent = new Intent(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
        activity.startActivity(newIntent);
    }

    private void determineError(SynthesizerListener.OnStatusChecked listener, int errorCode) {
        Locale speechLanguage = getConfiguration().getSpeechLanguage();

        int result = tts.isLanguageAvailable(speechLanguage);

        shutdown();
        if (result == TextToSpeech.LANG_MISSING_DATA
            || result == TextToSpeech.LANG_NOT_SUPPORTED
            || errorCode == TextToSpeech.ERROR_NOT_INSTALLED_YET) {

            listener.execute(LANGUAGE_NOT_SUPPORTED_ERROR);
        } else {

            logger.e(TAG, "UNKNOWN_ERROR - %s", getErrorType(errorCode));
            listener.execute(UNKNOWN_ERROR);
        }
    }

    private String getErrorType(int error) {
        switch (error) {
            case TextToSpeech.ERROR:
                return "ERROR";
            case TextToSpeech.ERROR_INVALID_REQUEST:
                return "ERROR_INVALID_REQUEST";
            case TextToSpeech.ERROR_NETWORK:
                return "ERROR_NETWORK";
            case TextToSpeech.ERROR_NETWORK_TIMEOUT:
                return "ERROR_NETWORK_TIMEOUT";
            case TextToSpeech.ERROR_NOT_INSTALLED_YET:
                return "ERROR_NOT_INSTALLED_YET";
            case TextToSpeech.ERROR_OUTPUT:
                return "ERROR_OUTPUT";
            case TextToSpeech.ERROR_SERVICE:
                return "ERROR_SERVICE";
            case TextToSpeech.ERROR_SYNTHESIS:
                return "ERROR_SYNTHESIS";
            default:
                return "ERROR_EXCEPTION";
        }
    }

    @Override
    void prepare(SynthesizerListener.OnPrepared onSynthesizerPrepared) {
        if (isTtsNull()) {
            setReady(false);
            logger.i(TAG, "creating new instance of Android TextToSpeech.class");
            tts = createTextToSpeech(application, status -> {
                if (status == TextToSpeech.SUCCESS) {
                    logger.i(TAG, "Android TextToSpeech.class new instance created");
                    setReady(true);
                    setupLanguage();
                } else prune();
                onSynthesizerPrepared.execute(status == TextToSpeech.SUCCESS ? SUCCESS : ERROR);
            });
            utteranceListener = new BaseSynthesizerUtteranceListener(application, this);
            tts.setOnUtteranceProgressListener(utteranceListener.getUtteranceProgressListener());
        } else if (isReady()) {
            //setupLanguage();
            onSynthesizerPrepared.execute(SUCCESS);
        }
    }

    @Override
    void executeOnEngineReady(String utteranceId, String text) {

        if (utteranceId.equals(CHECKING_UTTERANCE_ID)) {
            text = " ";
        }

        if (text.length() > TextToSpeech.getMaxSpeechInputLength()) {
            String[] split = StringUtils.split(text, TextToSpeech.getMaxSpeechInputLength());
            for (String item : split) {
                play(utteranceId, item);
            }
        } else {
            play(utteranceId, text);
        }
    }

    @Override
    void playSilence(String utteranceId, long durationInMillis) {
        logger.i(TAG, "[%s] - play internal silence", utteranceId);
        prepare(status -> {
            if (status == SUCCESS) {
                tts.playSilentUtterance(durationInMillis, TextToSpeech.QUEUE_ADD, utteranceId);
            } else {
                logger.e(TAG, "[%s] - silence status ERROR", utteranceId);
                utteranceListener.onError(utteranceId, ERROR);
            }
        });
    }

    @Override
    boolean isTtsNull() {
        return tts == null;
    }

    @Override
    boolean isTtsSpeaking() {
        return !isTtsNull() && tts.isSpeaking();
    }

    @Override
    public void setVoice(String gender) {
        Voice localeVoice = CollectionsKt.firstOrNull(
            tts.getVoices(),
            voice -> !voice.getFeatures().contains("notInstalled")
                     && voice.getLocale().equals(getConfiguration().getSpeechLanguage())
                     && (voice.getName().contains(gender)
                         || voice.getFeatures().contains(gender.replace("#", ""))
                         || voice.getFeatures().contains("gender=" + gender.replace("#", ""))));
        if (localeVoice != null) tts.setVoice(localeVoice);
        else logger.e(TAG, "Cannot find \"%1$s\" gender in Voice list [%1$d]", gender, tts.getVoices().size());
    }

    @Override
    public void setDefaultVoice() {
        tts.setVoice(tts.getDefaultVoice());
    }

    @Override
    public void stop() {
        logger.w(TAG, "stopping");
        super.stop();
        if (!isTtsNull()) {
            logger.v(TAG, "stopped");
            try {
                tts.stop();
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    protected void release() {
        super.release();
        utteranceListener = null;
    }

    private void setupLanguage() {
        // setLanguage might throw an IllegalArgumentException: Invalid int: "OS" - Samsung Android 6
        try {
            Locale speechLanguage = getConfiguration().getSpeechLanguage();
            if (tts != null && ! getConfiguration().isForceLanguageDetection()) {
                tts.setLanguage(speechLanguage);
            }
        } catch (Exception e) {
            logger.logException(e);
        }
    }

    private TextToSpeech createTextToSpeech(Application application, TextToSpeech.OnInitListener listener) {
        return new TextToSpeech(application, listener);
    }

    private void play(String utteranceId, String text) {
        logger.i(TAG, "[%s] - reading out loud: \"%s\"", utteranceId, text);
        prepare(status -> {
            if (status == SUCCESS) {
                File file = createTempFile(application, String.valueOf(System.currentTimeMillis()));
                setSynthesizedFile(file);
                tts.synthesizeToFile(text, null, file, utteranceId);
                //tts.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId);
            } else {
                logger.e(TAG, "[%s] - internal playTextNow status ERROR ", utteranceId);
                utteranceListener.onError(utteranceId, ERROR);
            }
        });
    }

    @Override
    public void getSpeechDuration(Context context, String text, Function1<Integer, Void> callback) {
        File temp = createTempFile(application, "duration");
        TextToSpeech[] _tts = new TextToSpeech[1];
        _tts[0] = createTextToSpeech((Application) context.getApplicationContext(), status -> {
            if (status == TextToSpeech.SUCCESS) {
                _tts[0].synthesizeToFile(text, null, temp, "get_duration");
            } else {
                callback.invoke(0);
            }
        });
        _tts[0].setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String utteranceId) {}

            @Override public void onDone(String utteranceId) {
                if (utteranceId.equals("get_duration")) {
                    Uri uri = Uri.parse(temp.getPath());
                    MediaMetadataRetriever mmr = new MediaMetadataRetriever();
                    mmr.setDataSource(context.getApplicationContext(), uri);
                    String durationStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                    int millSecond = Integer.parseInt(durationStr);
                    callback.invoke(millSecond);
                }
                try {
                    _tts[0].shutdown();
                } catch (Exception ignore) {}
            }

            @Override public void onError(String utteranceId) {
                callback.invoke(0);
                try {
                    _tts[0].shutdown();
                } catch (Exception ignore) {}
            }
        });
    }

    @Override
    void forceDestroyTTS() {
        destroyTTS();
    }

    private void destroyTTS() {
        if (!isTtsNull()) {
            try {
                tts.shutdown();
            } catch (Exception ignored) {
            }
            logger.v(TAG, "destroyed");
            tts = null;
            setReady(false);
        }
    }
}
