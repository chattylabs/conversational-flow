package com.chattylabs.sdk.android.voice;

import android.annotation.TargetApi;
import android.app.Application;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;

import com.chattylabs.sdk.android.common.HtmlUtils;
import com.chattylabs.sdk.android.common.StringUtils;
import com.chattylabs.sdk.android.common.Tag;
import com.chattylabs.sdk.android.common.internal.ILogger;
import com.chattylabs.sdk.android.voice.ConversationalFlowComponent.OnSynthesizerInitialised;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.*;
import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.SynthesizerListener;

public final class AndroidSpeechSynthesizer extends BaseSpeechSynthesizer {

    private static final String CHECKING_UTTERANCE_ID = "<CHECKING_UTTERANCE_ID>";
    private static final String TESTING_STRING = "%%TESTING_STRING%%";

    private static final int MAX_SPEECH_TIME_SEC = 60;

    private final String TAG = Tag.make("AndroidSpeechSynthesizer");

    // States
    private boolean triedToDownloadTtsData; // released
    private boolean reviewAgain; // released

    // Resources
    private Application application;
    private TextToSpeech tts; // released
    private OnSynthesizerSetup onSynthesizerSetup;

    AndroidSpeechSynthesizer(Application application,
                             VoiceConfig configuration,
                             AndroidAudioHandler audioHandler,
                             BluetoothSco bluetoothSco,
                             ILogger logger) {
        super(configuration, audioHandler, bluetoothSco, logger);
        this.application = application;
        this.release();
    }

    @WorkerThread
    @Override
    public void setup(OnSynthesizerSetup onSynthesizerSetup) {
        logger.i(TAG, "ANDROID TTS - setup and check language");
        this.onSynthesizerSetup = onSynthesizerSetup;
        try {
            initTts(status -> {
                if (status == SynthesizerListener.SUCCESS) {
                    tryToDownloadTtsData();
                }
                else {
                    if (isTtsNull()) {
                        release();
                        TextToSpeech _tts = new TextToSpeech(application, null);
                        if (_tts.getEngines().size() > 0) {
                            onSynthesizerSetup.execute(SynthesizerListener.AVAILABLE_BUT_INACTIVE);
                        }
                        else {
                            onSynthesizerSetup.execute(SynthesizerListener.NOT_AVAILABLE_ERROR);
                        }
                        _tts.shutdown();
                    }
                    else {
                        shutdown();
                        onSynthesizerSetup.execute(SynthesizerListener.AVAILABLE_BUT_INACTIVE);
                    }
                }
            });
        } catch (Exception e) {
            logger.logException(e);
            shutdown();
            onSynthesizerSetup.execute(SynthesizerListener.NOT_AVAILABLE_ERROR);
        }
    }

    @Override
    UtteranceListener createUtteranceListener(@NonNull SynthesizerListener... listeners) {
        return new AndroidSpeechSynthesizerAdapter()
        {
            @Override
            public void onStart(String utteranceId) {
                _getOnStartedListener().execute(utteranceId);
            }

            @Override
            public void onDone(String utteranceId) {
                _getOnDoneListener().execute(utteranceId);
            }

            @Override
            public void onError(String utteranceId, int errorCode) {
                _getOnErrorListener().execute(utteranceId, errorCode);
            }
        };
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
            } catch (Exception ignored) {}
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
            } catch (Exception ignored) {}
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
    void initTts(OnSynthesizerInitialised onSynthesizerInitialised) {
        if (isTtsNull()) {
            setReady(false);
            logger.i(TAG, "ANDROID TTS - creating new instance of TextToSpeech.class");
            tts = createTextToSpeech(application, status -> {
                logger.i(TAG, "ANDROID TTS - new instance created");
                if (status == TextToSpeech.SUCCESS) {
                    setReady(true);
                    setupLanguage();
                }
                onSynthesizerInitialised.execute(status == TextToSpeech.SUCCESS ?
                        SynthesizerListener.SUCCESS : SynthesizerListener.ERROR);
            });
            setUtteranceListener(createUtterancesListener());
            tts.setOnUtteranceProgressListener((UtteranceProgressListener) getUtteranceListener());
        }
        else if (isReady()) {
            setupLanguage();
            onSynthesizerInitialised.execute(SynthesizerListener.SUCCESS);
        }
    }

    private void setupLanguage() {
        // setLanguage might throw an IllegalArgumentException: Invalid int: "OS" - Samsung Android 6
        try {
            tts.setLanguage(Locale.getDefault());
        } catch (Exception ignored) {
            logger.logException(ignored); }
    }

    private UtteranceListener createUtterancesListener() {
        return new AndroidSpeechSynthesizerAdapter() {
            private long timestamp;
            private TimerTask task;
            private Timer timer;

            @Override
            public void clearTimeout(String utteranceId) {
                logger.v(getTag(), "ANDROID TTS[%s] - utterance timeout cleared", utteranceId);
                if (task != null) task.cancel();
                if (timer != null) timer.cancel();
            }

            @Override
            public void startTimeout(String utteranceId) {
                logger.i(getTag(), "ANDROID TTS[%s] - started timeout", utteranceId);
                timer = new Timer();
                task = new TimerTask() {
                    @Override
                    public void run() {
                        if (!isTtsSpeaking()) {
                            logger.e(getTag(), "ANDROID TTS[%s] - is null or not speaking && reached timeout", utteranceId);
                            stop();
                            onError(utteranceId, SynthesizerListener.TIMEOUT);
                        }
                        else {
                            if ((System.currentTimeMillis() - timestamp) > TimeUnit.SECONDS.toMillis(MAX_SPEECH_TIME_SEC)) {
                                logger.e(getTag(), "ANDROID TTS[%s] - exceeded %s seconds", utteranceId, MAX_SPEECH_TIME_SEC);
                                stop();
                                onError(utteranceId, SynthesizerListener.TIMEOUT);
                            }
                            else {
                                clearTimeout(utteranceId);
                                startTimeout(utteranceId);
                            }
                        }
                    }
                };
                timer.schedule(task, TimeUnit.SECONDS.toMillis(10));
            }

            @Override
            public void onStart(String utteranceId) {
                logger.v(getTag(), "ANDROID TTS[%s] - on start", utteranceId);

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
                clearTimeout(utteranceId);
                if (utteranceId.equals(CHECKING_UTTERANCE_ID)) {
                    logger.v(getTag(), "ANDROID TTS[%s] - on done <%s> -> go to setup language", utteranceId, getCurrentQueueId());
                    checkLanguage(true);
                }
                else {
                    logger.v(getTag(), "ANDROID TTS[%s] - on done <%s> - check for Empty Queue", utteranceId, getCurrentQueueId());
                    moveToNextQueueIfNeeded();
                    if (isEmpty()) {
                        stop();
                        logger.i(getTag(), "ANDROID TTS[%s] - on done <%s> - Stream Finished", utteranceId, getCurrentQueueId());
                    }
                    setSpeaking(false);
                    if (getListenersMap().size() > 0) {
                        UtteranceListener listener = removeListener(utteranceId);
                        logger.v(getTag(), "ANDROID TTS[%s] - on done <%s> - execute listener.onDone", utteranceId, getCurrentQueueId());
                        if (listener != null) {
                            listener.onDone(utteranceId);
                        }
                    }
                }
            }

            @Override
            @TargetApi(Build.VERSION_CODES.LOLLIPOP)
            public void onError(String utteranceId, int errorCode) {
                clearTimeout(utteranceId);
                logger.e(getTag(), "ANDROID TTS[%s] - on error <%s> -> stop timeout", utteranceId, getCurrentQueueId());
                logger.e(getTag(), "ANDROID TTS[%s] - error code: %s", utteranceId, getErrorType(errorCode));
                if (utteranceId.equals(CHECKING_UTTERANCE_ID)) {
                    shutdown();
                    if (errorCode == TextToSpeech.ERROR_NOT_INSTALLED_YET) {
                        onSynthesizerSetup.execute(SynthesizerListener.NOT_AVAILABLE_ERROR);
                    }
                    else {
                        onSynthesizerSetup.execute(SynthesizerListener.UNKNOWN_ERROR);
                    }
                }
                else {
                    moveToNextQueueIfNeeded();
                    if (isEmpty()) {
                        stop();
                        logger.i(getTag(), "ANDROID TTS[%s] - ERROR <%s> - Stream Finished", utteranceId, getCurrentQueueId());
                    }
                    setSpeaking(false);
                    if (getListenersMap().size() > 0 && getListenersMap().containsKey(utteranceId)) {
                        UtteranceListener listener = removeListener(utteranceId);
                        shutdown();
                        if (listener != null) {
                            listener.onError(utteranceId, errorCode);
                        }
                    } else shutdown();
                }
            }
        };
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
        }
        else {
            logger.e(TAG, "ANDROID TTS - try to download audio data - ERROR");
            shutdown();
            onSynthesizerSetup.execute(SynthesizerListener.UNKNOWN_ERROR);
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
        }
        else {
            play(utteranceId, finalText, params);
        }
    }

    private void checkLanguage(boolean fromUtterance) {
        int result = tts.isLanguageAvailable(Locale.getDefault());
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            if (reviewAgain && !fromUtterance) {
                reviewAgain = false;
                shutdown();
                logger.v(getTag(), "ANDROID TTS - retry language");
                setup(onSynthesizerSetup);
            }
            else {
                reviewAgain = true;
                shutdown();
                logger.v(getTag(), "ANDROID TTS - checking error");
                onSynthesizerSetup.execute(SynthesizerListener.LANGUAGE_NOT_SUPPORTED_ERROR);
            }
        }
        else {
            // Everything has gone well!
            logger.i(getTag(), "ANDROID TTS - checking has passed");
            stop();
            onSynthesizerSetup.execute(SynthesizerListener.AVAILABLE);
        }
    }

    @Override
    void playSilence(String utteranceId, long durationInMillis) {
        logger.i(TAG, "ANDROID TTS[%s] - play internal silence", utteranceId);
        initTts(status -> {
            if (status == SynthesizerListener.SUCCESS) {
                tts.playSilentUtterance(durationInMillis, TextToSpeech.QUEUE_ADD, utteranceId);
            }
            else {
                logger.e(TAG, "ANDROID TTS[%s] - silence status ERROR", utteranceId);
                getUtteranceListener().onError(utteranceId, SynthesizerListener.ERROR);
            }
        });
    }

    private void play(String utteranceId, String text, HashMap<String, String> params) {
        logger.i(TAG, "ANDROID TTS[%s] - reading out loud: \"%s\"", utteranceId, text);
        initTts(status -> {
            if (status == SynthesizerListener.SUCCESS) {
                Bundle newParams = new Bundle();
                String paramStream = params.get(TextToSpeech.Engine.KEY_PARAM_STREAM);
                if (paramStream != null) {
                    newParams.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, Integer.valueOf(paramStream));
                }
                tts.speak(text, TextToSpeech.QUEUE_ADD, newParams, utteranceId);
            }
            else {
                logger.e(TAG, "ANDROID TTS[%s] - internal playText status ERROR ", utteranceId);
                getUtteranceListener().onError(utteranceId, SynthesizerListener.ERROR);
            }
        });
    }

    public static String getErrorType(int error) {
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
                return "ERROR_UNKNOWN";
        }
    }
}
