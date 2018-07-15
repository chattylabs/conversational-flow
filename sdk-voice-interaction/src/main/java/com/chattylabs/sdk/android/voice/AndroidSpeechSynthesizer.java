package com.chattylabs.sdk.android.voice;

import android.annotation.TargetApi;
import android.app.Application;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.chattylabs.sdk.android.common.HtmlUtils;
import com.chattylabs.sdk.android.common.StringUtils;
import com.chattylabs.sdk.android.common.Tag;
import com.chattylabs.sdk.android.common.internal.ILogger;
import com.chattylabs.sdk.android.voice.VoiceInteractionComponent.OnSynthesizerDone;
import com.chattylabs.sdk.android.voice.VoiceInteractionComponent.OnSynthesizerError;
import com.chattylabs.sdk.android.voice.VoiceInteractionComponent.OnSynthesizerInitialised;
import com.chattylabs.sdk.android.voice.VoiceInteractionComponent.OnSynthesizerStart;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import static com.chattylabs.sdk.android.voice.VoiceInteractionComponent.DEFAULT_QUEUE_ID;
import static com.chattylabs.sdk.android.voice.VoiceInteractionComponent.SYNTHESIZER_AVAILABLE;
import static com.chattylabs.sdk.android.voice.VoiceInteractionComponent.SYNTHESIZER_AVAILABLE_BUT_INACTIVE;
import static com.chattylabs.sdk.android.voice.VoiceInteractionComponent.SYNTHESIZER_LANGUAGE_NOT_SUPPORTED_ERROR;
import static com.chattylabs.sdk.android.voice.VoiceInteractionComponent.SYNTHESIZER_NOT_AVAILABLE_ERROR;
import static com.chattylabs.sdk.android.voice.VoiceInteractionComponent.SYNTHESIZER_UNKNOWN_ERROR;
import static com.chattylabs.sdk.android.voice.VoiceInteractionComponent.SynthesizerListenerContract;

public final class AndroidSpeechSynthesizer implements VoiceInteractionComponent.SpeechSynthesizer {
    private static final String TAG = Tag.make("AndroidSpeechSynthesizer");

    // Constants
    private static final String CHECKING_UTTERANCE_ID = BuildConfig.APPLICATION_ID + ".checking";
    private static final String DEFAULT_UTTERANCE_ID = BuildConfig.APPLICATION_ID + ".utterance:";
    private static final String TESTING_STRING = "<TESTING_STRING>";
    private static final String MAP_UTTERANCE_ID = "utteranceId";
    private static final String MAP_SILENCE = "silence";
    private static final String MAP_MESSAGE = "message";
    private static final String MAP_PARAMS = "params";
    private static final int MAX_SPEECH_TIME = 60;

    // Data
    private final VoiceConfig configuration;
    private final Map<String, UtteranceProgressListener> listenersMap;
    private final Map<String, ConcurrentLinkedQueue<Map<String, Object>>> queue;
    private final List<TextFilter> filters;
    private final Object lock = new Object();

    // States
    private boolean isReady; // released
    private boolean isOnHold; // released
    private boolean isSpeaking; // released
    private boolean reviewAgain; // released
    private boolean triedToDownloadTtsData; // released

    // Resources
    private String queueId = DEFAULT_QUEUE_ID; // released
    private String lastQueueId;
    private Application application;
    private TextToSpeech tts; // released
    private AndroidSpeechSynthesizerUtteranceAdapter utteranceListener; // released
    private AndroidAudioHandler audioHandler;
    private BluetoothSco bluetoothSco;

    // Log stuff
    private ILogger logger;

    AndroidSpeechSynthesizer(Application application, VoiceConfig configuration,
                             AndroidAudioHandler audioHandler, BluetoothSco bluetoothSco, ILogger logger) {
        this.application = application;
        this.listenersMap = new LinkedHashMap<>();
        this.queue = new LinkedHashMap<>();
        this.filters = new LinkedList<>();
        this.configuration = configuration;
        this.audioHandler = audioHandler;
        this.bluetoothSco = bluetoothSco;
        this.logger = logger;
        this.release();
    }

    @Override
    public void playText(String text, String queueId, SynthesizerListenerContract... listeners) {
        playText(text, queueId, generateUtteranceListener(listeners));
    }

    private void playText(String text, String queueId, UtteranceProgressListener listener) {
        playText(text, queueId, listener, DEFAULT_UTTERANCE_ID + System.nanoTime());
    }

    private void playText(String text, String queueId, @Nullable UtteranceProgressListener listener, String utteranceId) {
        logger.i(TAG, "TTS - prepare to playText \"" + text + "\" with Queue: <" + queueId + "> - " + utteranceId);
        if (listenersMap.containsKey(utteranceId)) {
            utteranceId = utteranceId + "_" + System.currentTimeMillis();
        }
        final String uId = utteranceId;
        HashMap<String, String> params = buildParams(uId, String.valueOf(audioHandler.getMainStreamType()));
        if (listener != null) handleListener(uId, listener);
        addToQueueSet(uId, text, -1, params, queueId);
        logger.i(TAG, "TTS - ready: " + Boolean.toString(isReady) +
                " | speaking: " + Boolean.toString(isSpeaking) +
                " | held: " + Boolean.toString(isOnHold) + " - " + utteranceId);
        if (tts == null) {
            initTts(status -> {
                if (status == TextToSpeech.SUCCESS) {
                    isReady = true;
                    resume();
                }
                else {
                    logger.e(TAG, "TTS - with queue status ERROR");
                    if (listenersMap.containsKey(uId)) {
                        UtteranceProgressListener utteranceProgressListener;
                        synchronized (lock) {
                            utteranceProgressListener = listenersMap.remove(uId);
                        }
                        utteranceProgressListener.onError(uId, TextToSpeech.ERROR);
                    }
                }
            }, null);
        }
        else if (isReady && !isSpeaking && !isOnHold) {
            resume();
        }
    }

    @Override
    public void playText(String text, SynthesizerListenerContract... listeners) {
        String utteranceId = DEFAULT_UTTERANCE_ID + System.nanoTime();
        logger.i(TAG, "TTS - prepare to immediately playText \"" + text + "\" - " + utteranceId);
        AndroidSpeechSynthesizerUtteranceAdapter listener = generateUtteranceListener(listeners);
        if (listenersMap.containsKey(utteranceId)) {
            utteranceId = utteranceId + "_" + listenersMap.size();
        }
        HashMap<String, String> params = buildParams(utteranceId, String.valueOf(audioHandler.getMainStreamType()));
        handleListener(utteranceId, listener);
        Map<String, Object> map = new HashMap<>();
        map.put(MAP_UTTERANCE_ID, utteranceId);
        map.put(MAP_MESSAGE, text);
        map.put(MAP_PARAMS, params);
        logger.i(TAG, "TTS - ready: " + Boolean.toString(isReady) +
                " | speaking: " + Boolean.toString(isSpeaking) + " - " + utteranceId);
        initTts(status -> {
            if (status == TextToSpeech.SUCCESS) {
                playTheCurrentQueue(map);
            }
            else {
                logger.e(TAG, "TTS - no queue status ERROR");
                shutdown();
            }
        }, null);
    }

    @Override
    public void playSilence(long durationInMillis, String queueId, SynthesizerListenerContract... listeners) {
        if (durationInMillis <= 0) throw new IllegalArgumentException("Silence duration must be greater than 0");
        String utteranceId = DEFAULT_UTTERANCE_ID + System.nanoTime();
        logger.i(TAG, "TTS - play silence with Queue: <" + queueId + "> - " + utteranceId);
        AndroidSpeechSynthesizerUtteranceAdapter listener = generateUtteranceListener(listeners);
        if (listenersMap.containsKey(utteranceId)) {
            utteranceId = utteranceId + "_" + listenersMap.size();
        }
        final String uId = utteranceId;
        handleListener(uId, listener);
        // Silence doesn't need params
        addToQueueSet(uId, null, durationInMillis, null, queueId);
        logger.i(TAG, "TTS - ready: " + Boolean.toString(isReady) +
                " | speaking: " + Boolean.toString(isSpeaking) +
                " | held: " + Boolean.toString(isOnHold) + " - " + utteranceId);
        if (tts == null) {
            initTts(status -> {
                if (status == TextToSpeech.SUCCESS) {
                    resume();
                }
                else {
                    logger.e(TAG, "TTS - Silence status ERROR");
                    if (listenersMap.containsKey(uId)) {
                        UtteranceProgressListener utteranceProgressListener;
                        synchronized (lock) {
                            utteranceProgressListener = listenersMap.remove(uId);
                        }
                        utteranceProgressListener.onError(uId, TextToSpeech.ERROR);
                    }
                }
            }, null);
        }
        else if (isReady && !isSpeaking && !isOnHold) {
            resume();
        }
    }

    @Override
    public void playSilence(long durationInMillis, SynthesizerListenerContract... listeners) {
        if (durationInMillis <= 0) throw new IllegalArgumentException("Silence duration must be greater than 0");
        String utteranceId = DEFAULT_UTTERANCE_ID + System.nanoTime();
        logger.i(TAG, "TTS - play silence immediately - " + utteranceId);
        AndroidSpeechSynthesizerUtteranceAdapter listener = generateUtteranceListener(listeners);
        if (listenersMap.containsKey(utteranceId)) {
            utteranceId = utteranceId + "_" + listenersMap.size();
        }
        final String uId = utteranceId;
        handleListener(uId, listener);
        Map<String, Object> map = new HashMap<>();
        map.put(MAP_UTTERANCE_ID, uId);
        map.put(MAP_SILENCE, durationInMillis);
        logger.i(TAG, "TTS - ready: " + Boolean.toString(isReady) +
                " | speaking: " + Boolean.toString(isSpeaking) + " - " + uId);
        initTts(status -> {
            if (status == TextToSpeech.SUCCESS) {
                playTheCurrentQueue(map);
            }
            else {
                logger.e(TAG, "TTS - silence NOW Status ERROR - " + uId);
                shutdown();
            }
        }, null);
    }

    private AndroidSpeechSynthesizerUtteranceAdapter generateUtteranceListener(@NonNull SynthesizerListenerContract... listeners) {
        AndroidSpeechSynthesizerUtteranceAdapter listener = new AndroidSpeechSynthesizerUtteranceAdapter()
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
        if (listeners.length > 0) {
            for (SynthesizerListenerContract item : listeners) {
                if (item instanceof OnSynthesizerStart) {
                    listener.setOnStartedListener((OnSynthesizerStart) item);
                }
                if (item instanceof OnSynthesizerDone) {
                    listener.setOnDoneListener((OnSynthesizerDone) item);
                }
                if (item instanceof OnSynthesizerError) {
                    listener.setOnErrorListener((OnSynthesizerError) item);
                }
            }
        }
        return listener;
    }

    @Override
    public void releaseCurrentQueue() {
        isOnHold = false;
    }

    @Override
    public void holdCurrentQueue() {
        isOnHold = true;
    }

    @Override
    public void resume() {
        if (isSpeaking) return;
        initTts(status -> {
            if (status == TextToSpeech.SUCCESS) {
                logger.i(TAG, "TTS - resume queue: <" + queueId + ">");
                checkForEmptyCurrentQueue();
                if (!isEmpty()) {
                    isSpeaking = true;
                    // Gets and plays the current message in the queue
                    playTheCurrentQueue(queue.get(queueId).poll());
                }
            }
            else {
                logger.e(TAG, "TTS - status ERROR");
                shutdown();
            }
        }, null);
    }

    private void checkForEmptyCurrentQueue() {
        if (isCurrentQueueEmpty()) {
            logger.v(TAG, "TTS - no more messages in the queue <" + queueId + ">");
            moveToNextQueue();
        }
    }

    private void playTheCurrentQueue(Map<String, Object> map) {
        // Check whether Sco is connected or required
        logger.i(TAG, "TTS - is bluetooth Sco required: " +
                Boolean.toString(configuration.isBluetoothScoRequired()));
        if (configuration.isBluetoothScoRequired() && !bluetoothSco.isBluetoothScoOn()) {
            // Sco Listener
            BluetoothScoListener listener = new BluetoothScoListener() {
                @Override
                public void onConnected() {
                    logger.w(TAG, "TTS - Sco onConnected");
                    runMap(map);
                }

                @Override
                public void onDisconnected() {
                    logger.w(TAG, "TTS - Sco onDisconnected");
                    if (bluetoothSco.isBluetoothScoOn()) {
                        logger.w(TAG, "TTS - shutting down from Sco listener");
                        shutdown();
                    }
                }
            };
            // Start Bluetooth Sco
            bluetoothSco.startSco(listener);
            logger.v(TAG, "TTS - waiting for bluetooth sco connection");
        }
        else {
            logger.v(TAG, "TTS - bluetooth sco is: " + (bluetoothSco.isBluetoothScoOn() ? "on" : "off"));
            runMap(map);
        }
    }

    private void runMap(Map<String, Object> map) {
        audioHandler.requestAudioFocus(configuration.isAudioExclusiveRequiredForSynthesizer());
        if (map.containsKey(MAP_MESSAGE)) {
            //noinspection unchecked
            executeOnTtsReady((String) map.get(MAP_UTTERANCE_ID),
                    (String) map.get(MAP_MESSAGE),
                    (HashMap<String, String>) map.get(MAP_PARAMS));
        }
        else if (map.containsKey(MAP_SILENCE)) {
            //noinspection unchecked
            playSilence((String) map.get(MAP_UTTERANCE_ID), (long) map.get(MAP_SILENCE));
        }
    }

    @Override
    public void shutdown() {
        logger.w(TAG, "TTS - shutting down");
        stop();
        if (tts != null) {
            try {
                logger.v(TAG, "TTS - shutting down");
                tts.shutdown();
            } catch (Exception ignored) {}
            tts = null;
            logger.v(TAG, "TTS - destroyed");
        }
        // Release and reset all resources
        release();
    }

    @Override
    public void stop() {
        logger.w(TAG, "TTS - Stopping..");
        if (utteranceListener != null)
            utteranceListener.clearTimeout();
        // Stop Bluetooth Sco if required
        bluetoothSco.stopSco();
        // Audio focus
        audioHandler.abandonAudioFocus();
        // Shutdown text to speech
        if (tts != null) {
            try {
                tts.stop();
                logger.v(TAG, "TTS - TextToSpeech stopped");
            } catch (Exception ignored) {}
        }
        logger.w(TAG, "TTS - Speaking false");
        isSpeaking = false;
        releaseCurrentQueue();
    }

    private void release() {
        synchronized (lock) {
            listenersMap.clear();
            queue.clear();
            queue.put(DEFAULT_QUEUE_ID, new ConcurrentLinkedQueue<>());
        }
        filters.clear();
        queueId = DEFAULT_QUEUE_ID;
        reviewAgain = true;
        triedToDownloadTtsData = false;
        isReady = false;
        isOnHold = false;
        isSpeaking = false;
        logger.v(TAG, "TTS - states and resources released");
    }

    @Override
    public String getCurrentQueueId() {
        return queueId;
    }

    @Override
    public String getLastQueueId() {
        return lastQueueId;
    }

    @Nullable
    @Override
    public String getNextQueueId() {
        Set<String> keys = getQueueSet();
        if (keys.size() > 1) {
            return (String) keys.toArray()[1];
        }
        else {
            return null;
        }
    }

    @Override
    public boolean isCurrentQueueEmpty() {
        return !queue.containsKey(queueId) || queue.get(queueId).isEmpty();
    }

    @Override
    public boolean isEmpty() {
        return queue.isEmpty() || (queue.containsKey(DEFAULT_QUEUE_ID) && queue.size() == 1 && queue.get(DEFAULT_QUEUE_ID).isEmpty());
    }

    @Override
    public Set<String> getQueueSet() {
        return queue.keySet();
    }

    private void moveToNextQueue() {
        // is empty, still contains the queue id and it's not the default one
        if (queue.containsKey(queueId) && !DEFAULT_QUEUE_ID.equals(queueId)) {
            logger.v(TAG, "TTS - remove empty queue: <" + queueId + ">");
            synchronized (lock) {
                queue.remove(queueId);
            }
        }
        boolean isLastQueueEqualToCurrent = Objects.equals(lastQueueId, queueId);
        queueId = getNextQueueId();
        if (queueId == null) {
            queueId = DEFAULT_QUEUE_ID;
            if (isLastQueueEqualToCurrent) {
                logger.v(TAG, "TTS - update last queue from <" + lastQueueId + "> to <" + queueId + ">");
                lastQueueId = queueId;
            }
        }
        logger.v(TAG, "TTS - New queue: <" + queueId + ">");
    }

    @Override
    public void addFilter(TextFilter filter) {
        filters.add(filter);
    }

    private void handleListener(@NonNull String utteranceId, @NonNull UtteranceProgressListener listener) {
        logger.v(TAG, "TTS - added utterance - " + utteranceId + " - listener -> size:  " + listenersMap.size());
        synchronized (lock) {
            listenersMap.put(utteranceId, listener);
        }
    }

    private void addToQueueSet(@NonNull String utteranceId, String message, long duration, @Nullable HashMap<String, String> params, @NonNull String queueId) {
        Map<String, Object> map = new HashMap<>();
        map.put(MAP_UTTERANCE_ID, utteranceId);
        if (message != null) map.put(MAP_MESSAGE, message);
        if (duration > 0) map.put(MAP_SILENCE, duration);
        if (params != null) map.put(MAP_PARAMS, params);
        if (!queue.containsKey(queueId)) {
            logger.v(TAG, "TTS - added queue: <" + queueId + "> - " + utteranceId);
            lastQueueId = queueId;
            synchronized (lock) {
                queue.put(queueId, new ConcurrentLinkedQueue<>());
            }
        }
        queue.get(queueId).add(map);
        logger.v(TAG, "TTS - added message to queue <" + queueId + ">. Number of queues: " + queue.size());
        logger.v(TAG, "TTS - messages in the queue <" + queueId + ">: " + queue.get(queueId).size());
    }

    private HashMap<String, String> buildParams(@NonNull String utteranceId, @NonNull String audioStream) {
        HashMap<String, String> params = new LinkedHashMap<>();
        params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);
        params.put(TextToSpeech.Engine.KEY_PARAM_VOLUME, "1");
        params.put(TextToSpeech.Engine.KEY_PARAM_STREAM, audioStream);
        params.put(TextToSpeech.Engine.KEY_FEATURE_NETWORK_TIMEOUT_MS, "5000");
        params.put(TextToSpeech.Engine.KEY_FEATURE_NETWORK_RETRIES_COUNT, "2");
        logger.v(TAG, "TTS - building params - " + utteranceId);
        return params;
    }

    private void initTts(TextToSpeech.OnInitListener onInitListener,
                         OnSynthesizerInitialised onCheckLanguageInit) {
        if (tts == null) {
            isReady = false;
            logger.i(TAG, "TTS - creating new instance");
            tts = createTextToSpeech(application, status -> {
                logger.i(TAG, "TTS - new instance created");
                if (status == TextToSpeech.SUCCESS) {
                    isReady = true;
                    // FIXME IllegalArgumentException: Invalid int: "OS" - Samsung Android 6
                    try {
                        tts.setLanguage(Locale.getDefault());
                    } catch (Exception ignored) {}
                    onInitListener.onInit(status);
                }
            });
            utteranceListener = initUtterancesListener(onCheckLanguageInit);
            tts.setOnUtteranceProgressListener(utteranceListener);
        }
        else if (isReady) {
            onInitListener.onInit(TextToSpeech.SUCCESS);
        }
    }

    private AndroidSpeechSynthesizerUtteranceAdapter initUtterancesListener(OnSynthesizerInitialised onCheckLanguageInit) {
        return new AndroidSpeechSynthesizerUtteranceAdapter() {

            private long timestamp;
            private TimerTask task;
            private Timer timer;

            @Override
            protected void clearTimeout() {
                logger.i(TAG, "TTS - utterance timeout cleared!");
                if (task != null) task.cancel();
                if (timer != null) timer.cancel();
            }

            @Override
            protected void startTimeout(String utteranceId) {
                logger.i(TAG, "TTS - started timeout! - " + utteranceId);
                timer = new Timer();
                task = new TimerTask() {
                    @Override
                    public void run() {
                        if (tts == null || !tts.isSpeaking()) {
                            logger.e(TAG, "TTS - is null or not speaking && reached timeout! - " + utteranceId);
                            stop();
                            onDone(utteranceId);
                        }
                        else {
                            if ((System.currentTimeMillis() - timestamp) > TimeUnit.SECONDS.toMillis(MAX_SPEECH_TIME)) {
                                logger.e(TAG, "TTS - exceeded " + MAX_SPEECH_TIME + " sec! - " + utteranceId);
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
                logger.v(TAG, "TTS - on start -> utterance - " + utteranceId + " - listener size: " + listenersMap.size());

                startTimeout(utteranceId);
                timestamp = System.currentTimeMillis();

                if (listenersMap.size() > 0) {
                    UtteranceProgressListener listener = listenersMap.get(utteranceId);
                    if (listener != null) {
                        listener.onStart(utteranceId);
                    }
                }
            }

            @Override
            public void onDone(String utteranceId) {
                clearTimeout();
                if (utteranceId.equals(CHECKING_UTTERANCE_ID)) {
                    logger.v(TAG, "TTS - on done <" + queueId + "> -> go to setup language - " + utteranceId);
                    checkLanguage(onCheckLanguageInit, true);
                }
                else {
                    logger.v(TAG, "TTS - check For Empty Queue <" + queueId + "> - " + utteranceId);
                    isSpeaking = false;
                    checkForEmptyCurrentQueue();
                    if (isCurrentQueueEmpty()) {
                        stop();
                        logger.i(TAG, "TTS - Stream Finished. - " + utteranceId);
                    }
                    if (listenersMap.size() > 0) {
                        UtteranceProgressListener listener;
                        synchronized (lock) {
                            listener = listenersMap.remove(utteranceId);
                        }
                        logger.v(TAG, "TTS - Execute listener onDone <" + queueId + "> - " + utteranceId);
                        listener.onDone(utteranceId);
                    }
                }
            }

            @Override
            @TargetApi(Build.VERSION_CODES.LOLLIPOP)
            public void onError(String utteranceId, int errorCode) {
                clearTimeout();
                logger.e(TAG, "TTS - on error -> stop timeout -> utterance - " + utteranceId + " - listener size: " + listenersMap.size());
                logger.e(TAG, "TTS - error code: " + getErrorType(errorCode) + " - " + utteranceId);
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
                    isSpeaking = false;
                    checkForEmptyCurrentQueue();
                    if (isCurrentQueueEmpty()) {
                        stop();
                        logger.i(TAG, "TTS - ERROR - Stream Finished. - " + utteranceId);
                    }
                    if (listenersMap.size() > 0) {
                        UtteranceProgressListener listener;
                        synchronized (lock) {
                            listener = listenersMap.remove(utteranceId);
                        }
                        if (listener != null) {
                            listener.onError(utteranceId, errorCode);
                        }
                    }
                }
            }
        };
    }

    private void checkLanguage(OnSynthesizerInitialised onInit, boolean fromUtterance) {
        int result = tts.isLanguageAvailable(Locale.getDefault());
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            if (reviewAgain && !fromUtterance) {
                reviewAgain = false;
                shutdown();
                logger.v(TAG, "TTS - double checking!");
                setup(application, onInit);
            }
            else {
                reviewAgain = true;
                shutdown();
                logger.v(TAG, "TTS - checking error");
                onInit.execute(SYNTHESIZER_LANGUAGE_NOT_SUPPORTED_ERROR);
            }
        }
        else {
            // Everything has gone well!
            logger.i(TAG, "TTS - checking has passed!");
            shutdown();
            onInit.execute(SYNTHESIZER_AVAILABLE);
        }
    }

    private void tryToDownloadTtsData(OnSynthesizerInitialised onInit) {
        if (!triedToDownloadTtsData) {
            triedToDownloadTtsData = true;
            logger.v(TAG, "TTS - try to download audio data");
            try {
                // Try downloading data voice!
                playText(TESTING_STRING, "DOWNLOADING_TTS_DATA", null, CHECKING_UTTERANCE_ID);
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
    public void setup(Application application, OnSynthesizerInitialised onSynthesizerInitialised) {
        logger.i(TAG, "TTS - setup and check language");
        this.application = application;
        try {
            initTts(status -> {
                if (status == TextToSpeech.SUCCESS) {
                    tryToDownloadTtsData(onSynthesizerInitialised);
                }
                else {
                    if (tts == null) {
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

    private void executeOnTtsReady(String utteranceId, String text, HashMap<String, String> params) {
        //noinspection ConstantConditions
        String finalText = HtmlUtils.from(text).toString();

        for (TextFilter filter : filters) {
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

    private void playSilence(String utteranceId, long durationInMillis) {
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
