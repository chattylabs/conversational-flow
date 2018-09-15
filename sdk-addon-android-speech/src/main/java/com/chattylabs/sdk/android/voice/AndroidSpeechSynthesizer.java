package com.chattylabs.sdk.android.voice;

import android.app.Application;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;

import com.chattylabs.android.commons.HtmlUtils;
import com.chattylabs.android.commons.StringUtils;
import com.chattylabs.android.commons.Tag;
import com.chattylabs.android.commons.internal.ILogger;

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
    private SynthesizerListener.OnSetup onSynthesizerSetup;

    AndroidSpeechSynthesizer(Application application,
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
        logger.i(TAG, "ANDROID TTS - setup and check language");
        this.onSynthesizerSetup = onSynthesizerSetup;
        try {
            prepare(status -> {
                if (status == SynthesizerListener.Status.SUCCESS) {
                    tryToDownloadTtsData();
                } else {
                    if (isTtsNull()) {
                        release();
                        TextToSpeech _tts = new TextToSpeech(application, null);
                        if (_tts.getEngines().size() > 0) {
                            onSynthesizerSetup.execute(SynthesizerListener.Status.AVAILABLE_BUT_INACTIVE);
                        } else {
                            onSynthesizerSetup.execute(SynthesizerListener.Status.NOT_AVAILABLE_ERROR);
                        }
                        _tts.shutdown();
                    } else {
                        shutdown();
                        onSynthesizerSetup.execute(SynthesizerListener.Status.AVAILABLE_BUT_INACTIVE);
                    }
                }
            });
        } catch (Exception e) {
            logger.logException(e);
            shutdown();
            onSynthesizerSetup.execute(SynthesizerListener.Status.NOT_AVAILABLE_ERROR);
        }
    }

    @Override
    SynthesizerUtteranceListener createUtteranceListener(@NonNull SynthesizerListener... listeners) {
        return new AndroidSynthesizerUtteranceListener(this, this);
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
    String getTag() {
        return TAG;
    }

    @Override
    public void stop() {
        logger.w(TAG, "ANDROID TTS - stopping");
        super.stop();
        if (!isTtsNull()) {
            try {
                tts.stop();
                logger.v(TAG, "ANDROID TTS - stopped");
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void shutdown() {
        logger.w(TAG, "ANDROID TTS - shutting down");
        this.stop();
        if (!isTtsNull()) {
            try {
                tts.shutdown();
                logger.v(TAG, "ANDROID TTS - destroyed");
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
    HashMap<String, String> buildParams(@NonNull String utteranceId, @NonNull String audioStream) {
        HashMap<String, String> params = new LinkedHashMap<>();
        params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);
        params.put(TextToSpeech.Engine.KEY_PARAM_VOLUME, "1");
        params.put(TextToSpeech.Engine.KEY_PARAM_STREAM, audioStream);
        params.put(TextToSpeech.Engine.KEY_FEATURE_NETWORK_TIMEOUT_MS, "5000");
        params.put(TextToSpeech.Engine.KEY_FEATURE_NETWORK_RETRIES_COUNT, "2");
        return params;
    }

    @Override
    void prepare(SynthesizerListener.OnPrepared onSynthesizerPrepared) {
        if (isTtsNull()) {
            setReady(false);
            logger.i(TAG, "ANDROID TTS - creating new instance of TextToSpeech.class");
            tts = createTextToSpeech(application, status -> {
                logger.i(TAG, "ANDROID TTS - new instance created");
                if (status == TextToSpeech.SUCCESS) {
                    setReady(true);
                    setupLanguage();
                }
                onSynthesizerPrepared.execute(status == TextToSpeech.SUCCESS ?
                        SynthesizerListener.Status.SUCCESS : SynthesizerListener.Status.ERROR);
            });
            setSynthesizerUtteranceListener(createUtterancesListener());
            final AndroidSynthesizerUtteranceListener synthesizerUtteranceListener =
                    (AndroidSynthesizerUtteranceListener) getSynthesizerUtteranceListener();
            tts.setOnUtteranceProgressListener(synthesizerUtteranceListener.getUtteranceProgressListener());
        } else if (isReady()) {
            setupLanguage();
            onSynthesizerPrepared.execute(SynthesizerListener.Status.SUCCESS);
        }
    }

    private void setupLanguage() {
        // setLanguage might throw an IllegalArgumentException: Invalid int: "OS" - Samsung Android 6
        try {
            if (tts != null) tts.setLanguage(getLanguage());
        } catch (Exception ignored) {
            logger.logException(ignored);
        }
    }

    private Locale getLanguage() {
        Locale speechLanguage = getConfiguration().getSpeechLanguage();
        return speechLanguage != null ? speechLanguage : Locale.getDefault();
    }

    private SynthesizerUtteranceListener createUtterancesListener() {
        return new AndroidSynthesizerUtteranceListener(this, this,
                BaseSynthesizerUtteranceListener.Mode.DELEGATE);
    }

    private void tryToDownloadTtsData() {
        if (!triedToDownloadTtsData) {
            triedToDownloadTtsData = true;
            logger.v(TAG, "ANDROID TTS - try to download audio data");
            try {
                // Try downloading data voice!
                super.playText(TESTING_STRING, DEFAULT_QUEUE_ID, null, CHECKING_UTTERANCE_ID);
            } catch (Exception e) {
                logger.e(TAG, "error when downloading audio data: " + e.getMessage());
                // Otherwise it reports the TextToSpeechStatus to the Callback
                checkLanguage(false);
            }
        } else {
            logger.e(TAG, "ANDROID TTS - try to download audio data - ERROR");
            shutdown();
            onSynthesizerSetup.execute(SynthesizerListener.Status.UNKNOWN_ERROR);
        }
    }

    private TextToSpeech createTextToSpeech(Application application, TextToSpeech.OnInitListener listener) {
        return new TextToSpeech(application, listener);
    }

    @Override
    void executeOnTtsReady(String utteranceId, String text, HashMap<String, String> params) {
        //noinspection ConstantConditions
        String finalText = HtmlUtils.from(text).toString();

        for (TextFilter filter : getFilters()) {
            logger.v(TAG, "ANDROID TTS[%s] - apply filter: %s", utteranceId, filter);
            finalText = filter.apply(finalText);
        }

        if (utteranceId.equals(CHECKING_UTTERANCE_ID)) {
            finalText = " ";
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

    @NonNull
    @Override
    public SynthesizerListener.OnSetup getOnSetupSynthesizer() {
        return onSynthesizerSetup;
    }

    @Override
    public void checkLanguage(boolean fromUtterance) {
        int result = tts.isLanguageAvailable(getLanguage());
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            if (reviewAgain && !fromUtterance) {
                reviewAgain = false;
                shutdown();
                logger.v(getTag(), "ANDROID TTS - retry language");
                setup(onSynthesizerSetup);
            } else {
                reviewAgain = true;
                shutdown();
                logger.v(getTag(), "ANDROID TTS - checking error");
                onSynthesizerSetup.execute(SynthesizerListener.Status.LANGUAGE_NOT_SUPPORTED_ERROR);
            }
        } else {
            // Everything has gone well!
            logger.i(getTag(), "ANDROID TTS - checking has passed");
            stop();
            onSynthesizerSetup.execute(SynthesizerListener.Status.AVAILABLE);
        }
    }

    @Override
    public String getCheckingUtteranceId() {
        return CHECKING_UTTERANCE_ID;
    }

    @Override
    void playSilence(String utteranceId, long durationInMillis) {
        logger.i(TAG, "ANDROID TTS[%s] - play internal silence", utteranceId);
        prepare(status -> {
            if (status == SynthesizerListener.Status.SUCCESS) {
                tts.playSilentUtterance(durationInMillis, TextToSpeech.QUEUE_ADD, utteranceId);
            } else {
                logger.e(TAG, "ANDROID TTS[%s] - silence status ERROR", utteranceId);
                getSynthesizerUtteranceListener().onError(utteranceId, SynthesizerListener.Status.ERROR);
            }
        });
    }

    private void play(String utteranceId, String text, HashMap<String, String> params) {
        logger.i(TAG, "ANDROID TTS[%s] - reading out loud: \"%s\"", utteranceId, text);
        prepare(status -> {
            if (status == SynthesizerListener.Status.SUCCESS) {
                Bundle newParams = new Bundle();
                String paramStream = params.get(TextToSpeech.Engine.KEY_PARAM_STREAM);
                if (paramStream != null) {
                    newParams.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, Integer.valueOf(paramStream));
                }
                tts.speak(text, TextToSpeech.QUEUE_ADD, newParams, utteranceId);
            } else {
                logger.e(TAG, "ANDROID TTS[%s] - internal playText status ERROR ", utteranceId);
                getSynthesizerUtteranceListener().onError(utteranceId, SynthesizerListener.Status.ERROR);
            }
        });
    }
}
