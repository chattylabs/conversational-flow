package chattylabs.conversations;

import android.app.Application;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

import com.chattylabs.android.commons.HtmlUtils;
import com.chattylabs.android.commons.StringUtils;
import com.chattylabs.android.commons.Tag;
import com.chattylabs.android.commons.internal.ILogger;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;

public final class AndroidSpeechSynthesizer extends BaseSpeechSynthesizer
        implements AndroidSynthesizerUtteranceListener.AndroidSynthesizerUtteranceSupplier {

    private static final String CHECKING_UTTERANCE_ID = "<CHECKING_UTTERANCE_ID>";
    private static final String TESTING_STRING = "%%TESTING_STRING%%";
    private static final String TAG = Tag.make("AndroidSpeechSynthesizer");

    // States
    private boolean triedToDownloadTtsData; // released
    private boolean reviewAgain; // released

    // Resources
    private Application application;
    private TextToSpeech tts; // released
    private SynthesizerListener.OnStatusChecked onStatusChecked;

    @Keep
    public AndroidSpeechSynthesizer(Application application,
                             ComponentConfig configuration,
                             AndroidAudioManager audioManager,
                             BluetoothSco bluetoothSco,
                             ILogger logger) {
        super(configuration, audioManager, bluetoothSco, logger, TAG);
        this.release();
        this.application = application;
    }

    @Override
    public void checkStatus(SynthesizerListener.OnStatusChecked listener) {
        logger.i(TAG, "checkStatus and check language");
        this.onStatusChecked = listener;
        try {
            prepare(status -> {
                if (status == SynthesizerListener.Status.SUCCESS) {
                    tryToDownloadTtsData();
                } else {
                    if (isTtsNull()) {
                        release();
                        TextToSpeech _tts = new TextToSpeech(application, null);
                        if (_tts.getEngines().size() > 0) {
                            listener.execute(SynthesizerListener.Status.AVAILABLE_BUT_INACTIVE);
                        } else {
                            listener.execute(SynthesizerListener.Status.NOT_AVAILABLE_ERROR);
                        }
                        _tts.shutdown();
                    } else {
                        shutdown();
                        listener.execute(SynthesizerListener.Status.AVAILABLE_BUT_INACTIVE);
                    }
                }
            });
        } catch (Exception e) {
            logger.logException(e);
            shutdown();
            listener.execute(SynthesizerListener.Status.NOT_AVAILABLE_ERROR);
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
        this.stop();
        if (!isTtsNull()) {
            try {
                tts.shutdown();
                logger.v(TAG, "destroyed");
            } catch (Exception ignored) {
            }
        }
        release();
    }

    @Override
    public void release() {
        super.release();
        tts = null;
        triedToDownloadTtsData = false;
        reviewAgain = true;
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
                        SynthesizerListener.Status.SUCCESS : SynthesizerListener.Status.ERROR);
            });
            setSynthesizerUtteranceListener(createBaseUtteranceListener());
            final UtteranceProgressListener utterance = ((AndroidSynthesizerUtteranceListener)
                    getSynthesizerUtteranceListener()).getUtteranceProgressListener();
            tts.setOnUtteranceProgressListener(utterance);
        } else if (isReady()) {
            setupLanguage();
            onSynthesizerPrepared.execute(SynthesizerListener.Status.SUCCESS);
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

    private void tryToDownloadTtsData() {
        if (!triedToDownloadTtsData) {
            triedToDownloadTtsData = true;
            logger.v(TAG, "try to download audio data");
            try {
                // Try downloading data voice!
                super.playText(TESTING_STRING, DEFAULT_QUEUE_ID, null, CHECKING_UTTERANCE_ID);
            } catch (Exception e) {
                logger.e(TAG, "error when downloading audio data: " + e.getMessage());
                // Otherwise it reports the TextToSpeechStatus to the OnReadyCallback
                checkLanguage(false);
            }
        } else {
            logger.e(TAG, "try to download audio data - ERROR");
            shutdown();
            onStatusChecked.execute(SynthesizerListener.Status.UNKNOWN_ERROR);
        }
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
    public void checkLanguage(boolean fromUtterance) {
        int result = tts.isLanguageAvailable(getLanguage());
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            if (reviewAgain && !fromUtterance) {
                reviewAgain = false;
                shutdown();
                logger.v(TAG, "retry language");
                checkStatus(onStatusChecked);
            } else {
                reviewAgain = true;
                shutdown();
                logger.v(TAG, "checking error");
                onStatusChecked.execute(SynthesizerListener.Status.LANGUAGE_NOT_SUPPORTED_ERROR);
            }
        } else {
            // Everything has gone well!
            logger.i(TAG, "checking has passed");
            onStatusChecked.execute(SynthesizerListener.Status.AVAILABLE);
        }
    }

    @Override
    public String getCheckingUtteranceId() {
        return CHECKING_UTTERANCE_ID;
    }

    @Override
    void playSilence(String utteranceId, long durationInMillis) {
        logger.i(TAG, "[%s] - play internal silence", utteranceId);
        prepare(status -> {
            if (status == SynthesizerListener.Status.SUCCESS) {
                tts.playSilentUtterance(durationInMillis, TextToSpeech.QUEUE_ADD, utteranceId);
            } else {
                logger.e(TAG, "[%s] - silence status ERROR", utteranceId);
                getSynthesizerUtteranceListener().onError(utteranceId, SynthesizerListener.Status.ERROR);
            }
        });
    }

    private void play(String utteranceId, String text) {
        logger.i(TAG, "[%s] - reading out loud: \"%s\"", utteranceId, text);
        prepare(status -> {
            if (status == SynthesizerListener.Status.SUCCESS) {
                tts.synthesizeToFile(text, null, createTempFile(application), utteranceId);
                //tts.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId);
            } else {
                logger.e(TAG, "[%s] - internal playText status ERROR ", utteranceId);
                getSynthesizerUtteranceListener().onError(utteranceId, SynthesizerListener.Status.ERROR);
            }
        });
    }
}
