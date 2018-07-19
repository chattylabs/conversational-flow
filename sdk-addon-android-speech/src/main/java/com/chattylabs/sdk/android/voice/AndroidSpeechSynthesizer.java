package com.chattylabs.sdk.android.voice;

import android.annotation.TargetApi;
import android.app.Application;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.support.annotation.NonNull;

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

import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.SYNTHESIZER_AVAILABLE;
import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.SYNTHESIZER_AVAILABLE_BUT_INACTIVE;
import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.SYNTHESIZER_LANGUAGE_NOT_SUPPORTED_ERROR;
import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.SYNTHESIZER_NOT_AVAILABLE_ERROR;
import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.SYNTHESIZER_UNKNOWN_ERROR;
import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.SynthesizerListenerContract;

public final class AndroidSpeechSynthesizer extends BaseSpeechSynthesizer {

    private static final String CHECKING_UTTERANCE_ID = BuildConfig.APPLICATION_ID + ".checking";
    private static final String TESTING_STRING = "<TESTING_STRING>";
    private static final int MAX_SPEECH_TIME = 60;

    private final String TAG = Tag.make("AndroidSpeechSynthesizer");

    // States
    private boolean triedToDownloadTtsData; // released
    private boolean reviewAgain; // released

    // Resources
    private Application application;
    private TextToSpeech tts; // released

    AndroidSpeechSynthesizer(Application application,
                             VoiceConfig configuration,
                             AndroidAudioHandler audioHandler,
                             BluetoothSco bluetoothSco,
                             ILogger logger) {
        super(configuration, audioHandler, bluetoothSco, logger);
        this.application = application;
        this.release();
    }

    @Override
    public void setup(OnSynthesizerInitialised onSynthesizerInitialised) {
        logger.i(TAG, "TTS - setup and check language");
        try {
            initTts(status -> {
                if (status == TextToSpeech.SUCCESS) {
                    tryToDownloadTtsData(onSynthesizerInitialised);
                }
                else {
                    if (isTtsNull()) {
                        release();
                        TextToSpeech _tts = new TextToSpeech(application, null);
                        if (_tts.getEngines().size() > 0) {
                            onSynthesizerInitialised.execute(SYNTHESIZER_AVAILABLE_BUT_INACTIVE);
                        }
                        else {
                            onSynthesizerInitialised.execute(SYNTHESIZER_NOT_AVAILABLE_ERROR);
                        }
                        _tts.shutdown();
                    }
                    else {
                        shutdown();
                        onSynthesizerInitialised.execute(SYNTHESIZER_AVAILABLE_BUT_INACTIVE);
                    }
                }
            }, onSynthesizerInitialised);
        } catch (Exception e) {
            shutdown();
            onSynthesizerInitialised.execute(SYNTHESIZER_NOT_AVAILABLE_ERROR);
        }
    }

    @Override
    public  <T extends ConversationalFlowComponent.SynthesizerListenerContract> void playSilence(long durationInMillis, String queueId, T... listeners) {
//        if (durationInMillis <= 0) throw new IllegalArgumentException("Silence duration must be greater than 0");
//        String utteranceId = DEFAULT_UTTERANCE_ID + System.nanoTime();
//        logger.i(TAG, "TTS - play silence with Queue: <" + queueId + "> - " + utteranceId);
//        AndroidSpeechSynthesizerAdapter listener = generateUtteranceListener(listeners);
//        if (listenersMap.containsKey(utteranceId)) {
//            utteranceId = utteranceId + "_" + listenersMap.size();
//        }
//        final String uId = utteranceId;
//        handleListener(uId, listener);
//        // Silence doesn't need params
//        addToQueueSet(uId, null, durationInMillis, null, queueId);
//        logger.i(TAG, "TTS - ready: " + Boolean.toString(isReady) +
//                " | speaking: " + Boolean.toString(isSpeaking) +
//                " | held: " + Boolean.toString(isOnHold) + " - " + utteranceId);
//        if (isTtsNull()) {
//            initTts(status -> {
//                if (status == TextToSpeech.SUCCESS) {
//                    resume();
//                }
//                else {
//                    logger.e(TAG, "TTS - Silence status ERROR");
//                    if (listenersMap.containsKey(uId)) {
//                        UtteranceProgressListener utteranceProgressListener;
//                        synchronized (lock) {
//                            utteranceProgressListener = listenersMap.remove(uId);
//                        }
//                        utteranceProgressListener.onError(uId, TextToSpeech.ERROR);
//                    }
//                }
//            }, null);
//        }
//        else if (isReady && !isSpeaking && !isOnHold) {
//            resume();
//        }
    }

    @Override
    public  <T extends ConversationalFlowComponent.SynthesizerListenerContract> void playSilence(long durationInMillis, T... listeners) {
//        if (durationInMillis <= 0) throw new IllegalArgumentException("Silence duration must be greater than 0");
//        String utteranceId = DEFAULT_UTTERANCE_ID + System.nanoTime();
//        logger.i(TAG, "TTS - play silence immediately - " + utteranceId);
//        AndroidSpeechSynthesizerAdapter listener = generateUtteranceListener(listeners);
//        if (listenersMap.containsKey(utteranceId)) {
//            utteranceId = utteranceId + "_" + listenersMap.size();
//        }
//        final String uId = utteranceId;
//        handleListener(uId, listener);
//        Map<String, Object> map = new HashMap<>();
//        map.put(MAP_UTTERANCE_ID, uId);
//        map.put(MAP_SILENCE, durationInMillis);
//        logger.i(TAG, "TTS - ready: " + Boolean.toString(isReady) +
//                " | speaking: " + Boolean.toString(isSpeaking) + " - " + uId);
//        initTts(status -> {
//            if (status == TextToSpeech.SUCCESS) {
//                playTheCurrentQueue(map);
//            }
//            else {
//                logger.e(TAG, "TTS - silence NOW Status ERROR - " + uId);
//                shutdown();
//            }
//        }, null);
    }

    @Override
    UtteranceListener createUtteranceListener(@NonNull SynthesizerListenerContract... listeners) {
        return new AndroidSpeechSynthesizerAdapter()
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
        return !isTtsNull() && tts.isSpeaking();
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
                tts.stop();
                logger.v(TAG, "TTS - TextToSpeech stopped");
            } catch (Exception ignored) {}
        }
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
        logger.v(TAG, "TTS - building params for utterance: " + utteranceId);
        return params;
    }

    @Override
    void initTts(TextToSpeech.OnInitListener onInitListener,
                         OnSynthesizerInitialised onCheckLanguageInit) {
        if (isTtsNull()) {
            setReady(false);
            logger.i(TAG, "TTS - creating new instance of TextToSpeech.class");
            tts = createTextToSpeech(application, status -> {
                logger.i(TAG, "TTS - new instance created");
                if (status == TextToSpeech.SUCCESS) {
                    setReady(true);
                    // FIXME IllegalArgumentException: Invalid int: "OS" - Samsung Android 6
                    try {
                        tts.setLanguage(Locale.getDefault());
                    } catch (Exception ignored) {}
                    onInitListener.onInit(status);
                }
            });
            setUtteranceListener(createUtterancesListener(onCheckLanguageInit));
            tts.setOnUtteranceProgressListener((UtteranceProgressListener) getUtteranceListener());
        }
        else if (isReady()) {
            onInitListener.onInit(TextToSpeech.SUCCESS);
        }
    }

    private UtteranceListener createUtterancesListener(OnSynthesizerInitialised onCheckLanguageInit) {
        return new AndroidSpeechSynthesizerAdapter() {
            private long timestamp;
            private TimerTask task;
            private Timer timer;

            @Override
            public void clearTimeout() {
                logger.i(getTag(), "TTS - utterance timeout cleared!");
                if (task != null) task.cancel();
                if (timer != null) timer.cancel();
            }

            @Override
            public void startTimeout(String utteranceId) {
                logger.i(getTag(), "TTS - started timeout! - " + utteranceId);
                timer = new Timer();
                task = new TimerTask() {
                    @Override
                    public void run() {
                        if (isTtsNull() || !isTtsSpeaking()) {
                            logger.e(getTag(), "TTS - is null or not speaking && reached timeout! - " + utteranceId);
                            stop();
                            onDone(utteranceId);
                        }
                        else {
                            if ((System.currentTimeMillis() - timestamp) > TimeUnit.SECONDS.toMillis(MAX_SPEECH_TIME)) {
                                logger.e(getTag(), "TTS - exceeded " + MAX_SPEECH_TIME + " sec! - " + utteranceId);
                                stop();
                                onDone(utteranceId);
                            }
                            else {
                                clearTimeout();
                                startTimeout(utteranceId);
                            }
                        }
                    }
                };
                timer.schedule(task, TimeUnit.SECONDS.toMillis(10));
            }

            @Override
            public void onStart(String utteranceId) {
                logger.v(getTag(), "TTS - on start -> utterance - " + utteranceId + " - listener size: " + getListenersMap().size());

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
                clearTimeout();
                if (utteranceId.equals(CHECKING_UTTERANCE_ID)) {
                    logger.v(getTag(), "TTS - on done <" + getCurrentQueueId() + "> -> go to setup language - " + utteranceId);
                    checkLanguage(onCheckLanguageInit, true);
                }
                else {
                    logger.v(getTag(), "TTS - check For Empty Queue <" + getCurrentQueueId() + "> - " + utteranceId);
                    setSpeaking(false);
                    checkForEmptyCurrentQueue();
                    if (isCurrentQueueEmpty()) {
                        stop();
                        logger.i(getTag(), "TTS - Stream Finished. - " + utteranceId);
                    }
                    if (getListenersMap().size() > 0) {
                        UtteranceListener listener;
                        synchronized (lock) {
                            listener = getListenersMap().remove(utteranceId);
                        }
                        logger.v(getTag(), "TTS - Execute listener onDone <" + getCurrentQueueId() + "> - " + utteranceId);
                        listener.onDone(utteranceId);
                    }
                }
            }

            @Override
            @TargetApi(Build.VERSION_CODES.LOLLIPOP)
            public void onError(String utteranceId, int errorCode) {
                clearTimeout();
                logger.e(getTag(), "TTS - on error -> stop timeout -> utterance - " + utteranceId + " - listener size: " + getListenersMap().size());
                logger.e(getTag(), "TTS - error code: " + getErrorType(errorCode) + " - " + utteranceId);
                if (utteranceId.equals(CHECKING_UTTERANCE_ID)) {
                    shutdown();
                    if (errorCode == TextToSpeech.ERROR_NOT_INSTALLED_YET) {
                        onCheckLanguageInit.execute(SYNTHESIZER_NOT_AVAILABLE_ERROR);
                    }
                    else {
                        onCheckLanguageInit.execute(SYNTHESIZER_UNKNOWN_ERROR);
                    }
                }
                else {
                    setSpeaking(false);
                    checkForEmptyCurrentQueue();
                    if (isCurrentQueueEmpty()) {
                        stop();
                        logger.i(getTag(), "TTS - ERROR - Stream Finished. - " + utteranceId);
                    }
                    if (getListenersMap().size() > 0) {
                        UtteranceListener listener;
                        synchronized (lock) {
                            listener = getListenersMap().remove(utteranceId);
                        }
                        if (listener != null) {
                            listener.onError(utteranceId, errorCode);
                        }
                    }
                }
            }
        };
    }

    private void tryToDownloadTtsData(OnSynthesizerInitialised onInit) {
        if (!triedToDownloadTtsData) {
            triedToDownloadTtsData = true;
            logger.v(TAG, "TTS - try to download audio data");
            try {
                // Try downloading data voice!
                super.playText(TESTING_STRING, "DOWNLOADING_TTS_DATA", null, CHECKING_UTTERANCE_ID);
            } catch (Exception e) {
                logger.e(TAG, "error when downloading audio data: " + e.getMessage());
                // Otherwise it reports the TextToSpeechStatus to the Callback
                checkLanguage(onInit, false);
            }
        }
        else {
            logger.e(TAG, "TTS - try to download audio data - ERROR");
            shutdown();
            onInit.execute(SYNTHESIZER_UNKNOWN_ERROR);
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
            logger.v(TAG, "TTS - apply filter: " + filter + " - " + utteranceId);
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

    private void checkLanguage(OnSynthesizerInitialised onInit, boolean fromUtterance) {
        int result = tts.isLanguageAvailable(Locale.getDefault());
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            if (reviewAgain && !fromUtterance) {
                reviewAgain = false;
                shutdown();
                logger.v(getTag(), "TTS - double checking!");
                setup(onInit);
            }
            else {
                reviewAgain = true;
                shutdown();
                logger.v(getTag(), "TTS - checking error");
                onInit.execute(SYNTHESIZER_LANGUAGE_NOT_SUPPORTED_ERROR);
            }
        }
        else {
            // Everything has gone well!
            logger.i(getTag(), "TTS - checking has passed!");
            shutdown();
            onInit.execute(SYNTHESIZER_AVAILABLE);
        }
    }

    @Override
    void playSilence(String utteranceId, long durationInMillis) {
        logger.i(TAG, "TTS - play internal silence - " + utteranceId);
        initTts(status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.playSilentUtterance(durationInMillis, TextToSpeech.QUEUE_ADD, utteranceId);
            }
            else {
                logger.e(TAG, "TTS - silence status ERROR - " + utteranceId);
                shutdown();
            }
        }, null);
    }

    private void play(String utteranceId, String text, HashMap<String, String> params) {
        logger.i(TAG, "TTS - reading out loud: \"" + text + "\" - " + utteranceId);
        initTts(status -> {
            if (status == TextToSpeech.SUCCESS) {
                Bundle newParams = new Bundle();
                String paramStream = params.get(TextToSpeech.Engine.KEY_PARAM_STREAM);
                if (paramStream != null) {
                    newParams.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, Integer.valueOf(paramStream));
                }
                tts.speak(text, TextToSpeech.QUEUE_ADD, newParams, utteranceId);
            }
            else {
                logger.e(TAG, "TTS - playText status ERROR - " + utteranceId);
                shutdown();
            }
        }, null);
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
