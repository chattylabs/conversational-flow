package chattylabs.conversations;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.speech.tts.TextToSpeech;

import androidx.annotation.Keep;

import java.util.Locale;

import chattylabs.android.commons.HtmlUtils;
import chattylabs.android.commons.StringUtils;
import chattylabs.android.commons.Tag;
import chattylabs.android.commons.internal.ILogger;

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
        if (isQueueEmpty() && isOnQueue()) shutdown();
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
        destroyTTS();
        release();
    }

    private void checkTTS(SynthesizerListener.OnStatusChecked listener) {
        try {
            // Try speaking an empty text!
            SynthesizerListener onDone = (SynthesizerListener.OnDone) utteranceId -> {
                logger.v(TAG, "[%s] - on done <%s> -> go to checkStatus language", utteranceId, getCurrentQueueId());
                getAudioManager().abandonAudioFocus(getConfiguration().isAudioExclusiveRequiredForSynthesizer());
                listener.execute(AVAILABLE);
            };
            SynthesizerListener onError = (SynthesizerListener.OnError) (utteranceId, errorCode) -> {
                checkInstallation(listener, errorCode);
            };
            super.playText(TESTING_STRING, DEFAULT_QUEUE_ID, CHECKING_UTTERANCE_ID, onDone, onError);
        } catch (Exception e) {
            logger.e(TAG, "error when checking TTS: %s", e.getMessage());
            // Otherwise it reports the TextToSpeechStatus to the OnReadyCallback
            checkInstallation(listener, TextToSpeech.ERROR_NOT_INSTALLED_YET);
        }
    }

    private void checkInstallation(SynthesizerListener.OnStatusChecked listener, int errorCode) {
        int result = tts.isLanguageAvailable(getLanguage());
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED
                || errorCode == TextToSpeech.ERROR_NOT_INSTALLED_YET) {
            application.registerActivityLifecycleCallbacks(new ActivityLifecycleCallback() {
                @Override
                public void onActivityResumed(Activity activity) {
                    TextToSpeech _tts = new TextToSpeech(application, null);
                    int result = _tts.isLanguageAvailable(getLanguage());
                    _tts.shutdown();
                    if (result == TextToSpeech.LANG_AVAILABLE) {
                        if (isOnQueue() && isQueueEmpty()) shutdown();
                        else getAudioManager().abandonAudioFocus(getConfiguration().isAudioExclusiveRequiredForSynthesizer());
                        listener.execute(AVAILABLE);
                    } else {
                        shutdown();
                        logger.e(TAG, "LANGUAGE_NOT_SUPPORTED_ERROR");
                        listener.execute(LANGUAGE_NOT_SUPPORTED_ERROR);
                    }
                }
            });
            Intent newIntent = new Intent(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
            application.startActivity(newIntent);
        } else {
            logger.e(TAG, "UNKNOWN_ERROR - %s", getErrorType(errorCode));
            shutdown();
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
                    //setupLanguage();
                } else prune();
                onSynthesizerPrepared.execute(status == TextToSpeech.SUCCESS ?
                        SUCCESS : ERROR);
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
        String finalText = HtmlUtils.from(text).toString();

        if (utteranceId.equals(CHECKING_UTTERANCE_ID)) {
            finalText = " ";
        } else {
            for (TextFilter filter : getFilters()) {
                logger.v(TAG, "[%s] - apply filter: %s", utteranceId, filter);
                finalText = filter.apply(finalText);
            }
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
        getAudioManager().abandonAudioFocus(getConfiguration().isAudioExclusiveRequiredForSynthesizer());
    }

    protected void release() {
        super.release();
        utteranceListener = null;
    }

    private void setupLanguage() {
        // setLanguage might throw an IllegalArgumentException: Invalid int: "OS" - Samsung Android 6
        try {
            if (tts != null) tts.setLanguage(getLanguage());
        } catch (Exception e) {
            logger.logException(e);
        }
    }

    private Locale getLanguage() {
        Locale speechLanguage = getConfiguration().getSpeechLanguage();
        return speechLanguage != null ? speechLanguage : Locale.getDefault();
    }

    private TextToSpeech createTextToSpeech(Application application, TextToSpeech.OnInitListener listener) {
        return new TextToSpeech(application, listener);
    }

    private void play(String utteranceId, String text) {
        logger.i(TAG, "[%s] - reading out loud: \"%s\"", utteranceId, text);
        prepare(status -> {
            if (status == SUCCESS) {
                tts.synthesizeToFile(text, null, createTempFile(application), utteranceId);
                //tts.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId);
            } else {
                logger.e(TAG, "[%s] - internal playTextNow status ERROR ", utteranceId);
                utteranceListener.onError(utteranceId, ERROR);
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
