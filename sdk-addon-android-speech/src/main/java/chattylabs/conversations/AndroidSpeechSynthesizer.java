package chattylabs.conversations;

import android.app.Application;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

import com.chattylabs.android.commons.HtmlUtils;
import com.chattylabs.android.commons.StringUtils;
import com.chattylabs.android.commons.Tag;
import com.chattylabs.android.commons.internal.ILogger;

import java.util.List;
import java.util.Locale;

import static chattylabs.conversations.SynthesizerListener.Status.*;

public final class AndroidSpeechSynthesizer extends BaseSpeechSynthesizer
        implements AndroidSynthesizerUtteranceListener.AndroidSynthesizerUtteranceSupplier {

    private static final String CHECKING_UTTERANCE_ID = "<CHECKING_UTTERANCE_ID>";
    private static final String TESTING_STRING = "%%TESTING_STRING%%";
    private static final String TAG = Tag.make("AndroidSpeechSynthesizer");

    // States
    private boolean triedToDownloadTtsData = false; // released
    private boolean reviewAgain = true; // released

    // Resources
    private Application application;
    private TextToSpeech tts; // released
    private SynthesizerListener.OnStatusChecked onStatusChecked;

    @Keep
    public AndroidSpeechSynthesizer(Application application,
                             ComponentConfig configuration,
                             AndroidAudioManager audioManager,
                             AndroidBluetooth bluetooth,
                             ILogger logger) {
        super(configuration, audioManager, bluetooth, logger, TAG);
        this.release();
        this.application = application;
    }

    @Override
    public void checkStatus(SynthesizerListener.OnStatusChecked listener) {
        logger.i(TAG, "checkStatus and check language");
        this.onStatusChecked = listener;
        try {
            prepare(status -> {
                if (status == SUCCESS) {
                    tryToDownloadTtsData();
                } else {
                    if (isTtsNull()) {
                        shutdown();
                        getAudioManager().abandonAudioFocus();
                        TextToSpeech _tts = new TextToSpeech(application, null);
                        List<TextToSpeech.EngineInfo> engines = _tts.getEngines();
                        _tts.shutdown();
                        if (!engines.isEmpty()) {
                            listener.execute(AVAILABLE_BUT_INACTIVE);
                        } else {
                            listener.execute(NOT_AVAILABLE_ERROR);
                        }
                    } else {
                        shutdown();
                        getAudioManager().abandonAudioFocus();
                        listener.execute(AVAILABLE_BUT_INACTIVE);
                    }
                }
            });
        } catch (Exception e) {
            logger.logException(e);
            shutdown();
            getAudioManager().abandonAudioFocus();
            listener.execute(NOT_AVAILABLE_ERROR);
        }
    }

    private void tryToDownloadTtsData() {
        if (!triedToDownloadTtsData) {
            triedToDownloadTtsData = true;
            logger.v(TAG, "try to download audio data");
            try {
                // Try downloading data voice!
                setChecking(true);
                super.playText(TESTING_STRING, DEFAULT_QUEUE_ID, null, CHECKING_UTTERANCE_ID);
            } catch (Exception e) {
                logger.e(TAG, "error when downloading audio data: " + e.getMessage());
                // Otherwise it reports the TextToSpeechStatus to the OnReadyCallback
                setChecking(false);
                checkLanguage(false);
            }
        } else {
            logger.e(TAG, "try to download audio data - ERROR");
            shutdown();
            getAudioManager().abandonAudioFocus();
            onStatusChecked.execute(UNKNOWN_ERROR);
        }
    }

    @Override
    public void checkLanguage(boolean fromUtterance) {
        int result = tts.isLanguageAvailable(getLanguage());
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            if (reviewAgain && !fromUtterance) {
                reviewAgain = false;
                shutdown();
                logger.w(TAG, "retry checking language");
                checkStatus(onStatusChecked);
            } else {
                reviewAgain = true;
                shutdown();
                getAudioManager().abandonAudioFocus();
                logger.e(TAG, "LANGUAGE_NOT_SUPPORTED_ERROR");
                onStatusChecked.execute(LANGUAGE_NOT_SUPPORTED_ERROR);
            }
        } else {
            triedToDownloadTtsData = false;
            reviewAgain = true;
            // Everything has gone well!
            logger.i(TAG, "checking has passed");
            getAudioManager().abandonAudioFocus();
            onStatusChecked.execute(AVAILABLE);
        }
    }

    @Override
    SynthesizerUtteranceListener createDelegateUtteranceListener() {
        return new AndroidSynthesizerUtteranceListener(application, this, this,
                BaseSynthesizerUtteranceListener.Mode.DELEGATE);
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
    void prepare(SynthesizerListener.OnPrepared onSynthesizerPrepared) {
        if (isTtsNull()) {
            setReady(false);
            logger.i(TAG, "creating new instance of TextToSpeech.class");
            tts = createTextToSpeech(application, status -> {
                logger.i(TAG, "new instance created");
                if (status == TextToSpeech.SUCCESS) {
                    setReady(true);
                    setupLanguage();
                }
                onSynthesizerPrepared.execute(status == TextToSpeech.SUCCESS ?
                        SUCCESS : ERROR);
            });
            setSynthesizerUtteranceListener(createBaseUtteranceListener());
            final UtteranceProgressListener utterance = ((AndroidSynthesizerUtteranceListener)
                    getSynthesizerUtteranceListener()).getUtteranceProgressListener();
            tts.setOnUtteranceProgressListener(utterance);
        } else if (isReady()) {
            setupLanguage();
            onSynthesizerPrepared.execute(SUCCESS);
        }
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

    private SynthesizerUtteranceListener createBaseUtteranceListener() {
        return new AndroidSynthesizerUtteranceListener(application, this, this);
    }

    private TextToSpeech createTextToSpeech(Application application, TextToSpeech.OnInitListener listener) {
        return new TextToSpeech(application, listener);
    }

    @Override
    void executeOnEngineReady(String utteranceId, String text) {
        //noinspection ConstantConditions
        String finalText = HtmlUtils.from(text).toString();

        for (TextFilter filter : getFilters()) {
            logger.v(TAG, "[%s] - apply filter: %s", utteranceId, filter);
            finalText = filter.apply(finalText);
        }

        if (utteranceId.equals(CHECKING_UTTERANCE_ID)) {
            finalText = " ";
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

    @NonNull
    @Override
    public SynthesizerListener.OnStatusChecked getOnStatusCheckedListener() {
        return onStatusChecked;
    }

    @Override
    public String getCheckingUtteranceId() {
        return CHECKING_UTTERANCE_ID;
    }

    @Override
    public void setChecking(boolean checking) {
        super.setChecking(checking);
    }

    @Override
    void playSilence(String utteranceId, long durationInMillis) {
        logger.i(TAG, "[%s] - play internal silence", utteranceId);
        prepare(status -> {
            if (status == SUCCESS) {
                tts.playSilentUtterance(durationInMillis, TextToSpeech.QUEUE_ADD, utteranceId);
            } else {
                logger.e(TAG, "[%s] - silence status ERROR", utteranceId);
                shutdown();
                getAudioManager().abandonAudioFocus();
                getSynthesizerUtteranceListener().onError(utteranceId, ERROR);
            }
        });
    }

    private void play(String utteranceId, String text) {
        logger.i(TAG, "[%s] - reading out loud: \"%s\"", utteranceId, text);
        prepare(status -> {
            if (status == SUCCESS) {
                tts.synthesizeToFile(text, null, createTempFile(application), utteranceId);
                //tts.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId);
            } else {
                logger.e(TAG, "[%s] - internal playText status ERROR ", utteranceId);
                shutdown();
                getAudioManager().abandonAudioFocus();
                getSynthesizerUtteranceListener().onError(utteranceId, ERROR);
            }
        });
    }

    @Override
    public void stop() {
        logger.w(TAG, "stopping");
        super.stop();
        if (!isTtsNull()) {
            try {
                tts.stop();
                logger.v(TAG, "stopped");
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void shutdown() {
        logger.w(TAG, "shutting down");
        stop();
        if (!isTtsNull()) {
            try {
                tts.shutdown();
                logger.v(TAG, "destroyed");
            } catch (Exception ignored) {
            }
        }
        release();
    }

    protected void release() {
        super.release();
        tts = null;
        setReady(false);
    }
}
