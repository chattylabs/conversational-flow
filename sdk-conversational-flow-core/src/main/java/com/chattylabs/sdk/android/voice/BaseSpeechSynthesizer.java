package com.chattylabs.sdk.android.voice;

import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;

import com.chattylabs.sdk.android.common.internal.ILogger;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.DEFAULT_QUEUE_ID;
import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.OnSynthesizerDone;
import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.OnSynthesizerError;
import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.OnSynthesizerInitialised;
import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.OnSynthesizerStart;
import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.SpeechSynthesizer;
import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.SynthesizerListener;
import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.TAG;

abstract class BaseSpeechSynthesizer implements SpeechSynthesizer {

    // Constants
    private static final String DEFAULT_UTTERANCE_ID = "u:";
    private static final String MAP_UTTERANCE_ID = "utteranceId";
    private static final String MAP_SILENCE = "silence";
    private static final String MAP_MESSAGE = "message";
    private static final String MAP_PARAMS = "params";

    // Data
    private final VoiceConfig configuration;
    private final Map<String, UtteranceListener> listenersMap;
    private final Map<String, ConcurrentLinkedQueue<Map<String, Object>>> queue;
    private final List<TextFilter> filters;
    final Object lock = new Object();

    // States
    private boolean isReady; // released
    private boolean isOnHold; // released
    private boolean isSpeaking; // released

    // Resources
    private String queueId = DEFAULT_QUEUE_ID; // released
    private UtteranceListener utteranceListener; // released
    private String lastQueueId;
    private final AndroidAudioHandler audioHandler;
    private final BluetoothSco bluetoothSco;

    // Log stuff
    protected final ILogger logger;

    BaseSpeechSynthesizer(VoiceConfig configuration,
                          AndroidAudioHandler audioHandler,
                          BluetoothSco bluetoothSco,
                          ILogger logger) {
        this.configuration = configuration;
        this.filters = new LinkedList<>();
        this.listenersMap = new LinkedHashMap<>();
        this.queue = new LinkedHashMap<>();
        this.audioHandler = audioHandler;
        this.bluetoothSco = bluetoothSco;
        this.logger = logger;
    }

    abstract String getTag();

    abstract void initTts(OnSynthesizerInitialised onSynthesizerInitialised);

    abstract void executeOnTtsReady(
            String utteranceId, String text, HashMap<String, String> params);

    abstract void playSilence(String utteranceId, long durationInMillis);

    abstract HashMap<String,String> buildParams(String uId, String s);

    abstract boolean isTtsNull();

    abstract boolean isTtsSpeaking();

    abstract UtteranceListener createUtteranceListener(
            SynthesizerListener[] listeners);

    void setReady(boolean ready) {
        logger.w(TAG, "TTS - ready set to " + Boolean.toString(ready));
        isReady = ready;
    }

    public boolean isReady() {
        return isReady;
    }

    public boolean isSpeaking() {
        return isSpeaking;
    }

    void setSpeaking(boolean speaking) {
        logger.w(TAG, "TTS - speaking set to " + Boolean.toString(speaking));
        isSpeaking = speaking;
    }

    @Override
    public void addFilter(TextFilter filter) {
        filters.add(filter);
    }

    @Override
    public List<TextFilter> getFilters() {
        return filters;
    }

    @Override
    public void clearFilters() {
        filters.clear();
    }

    Map<String, UtteranceListener> getListenersMap() {
        return listenersMap;
    }

    UtteranceListener getUtteranceListener() {
        return utteranceListener;
    }

    void setUtteranceListener(UtteranceListener utteranceListener) {
        this.utteranceListener = utteranceListener;
    }

    public VoiceConfig getConfiguration() {
        return configuration;
    }

    private UtteranceListener generateUtteranceListener(
            UtteranceListener listener, SynthesizerListener[] listeners) {
        if (listeners.length > 0) {
            for (SynthesizerListener item : listeners) {
                if (item instanceof OnSynthesizerStart) {
                    listener._setOnStartedListener((OnSynthesizerStart) item);
                }
                if (item instanceof OnSynthesizerDone) {
                    listener._setOnDoneListener((OnSynthesizerDone) item);
                }
                if (item instanceof OnSynthesizerError) {
                    listener._setOnErrorListener((OnSynthesizerError) item);
                }
            }
        }
        return listener;
    }

    @WorkerThread
    @Override
    public <T extends SynthesizerListener> void playText(String text, String queueId, T... listeners) {
        UtteranceListener listener = generateUtteranceListener(createUtteranceListener(listeners), listeners);
        playText(text, queueId, listener, DEFAULT_UTTERANCE_ID + System.nanoTime());
    }

    void playText(String text, String queueId, @Nullable UtteranceListener listener, String utteranceId) {
        logger.i(TAG, "TTS[%s] - play \"%s\" on queue <%s>", utteranceId, text, queueId);
        if (listenersMap.containsKey(utteranceId)) {
            utteranceId = utteranceId + "_" + listenersMap.size();
        }
        final String uId = utteranceId;
        HashMap<String, String> params = buildParams(uId, String.valueOf(audioHandler.getMainStreamType()));
        if (listener != null) handleListener(uId, listener);
        addToQueue(uId, text, -1, params, queueId);
        logger.i(TAG, "TTS[%s] - ready: %s | speaking: %s | held: %s",
                uId, Boolean.toString(isReady), Boolean.toString(isSpeaking), Boolean.toString(isOnHold));
        if (isTtsNull() && !isSpeaking) {
            setSpeaking(true);
            initTts(status -> {
                if (status == SynthesizerListener.SUCCESS) {
                    run();
                }
                else {
                    logger.e(TAG, "TTS[%s] - status ERROR with queue <%s>", uId, queueId);
                    getUtteranceListener().onError(uId, SynthesizerListener.ERROR);
                }
            });
        }
        else if (isReady && !isSpeaking && !isOnHold) {
            run();
        }
    }

    @WorkerThread
    @Override
    public <T extends SynthesizerListener> void playText(String text, T... listeners) {
        String utteranceId = DEFAULT_UTTERANCE_ID + System.nanoTime();
        logger.i(TAG, "TTS[%s] - immediately play \"%s\"", utteranceId, text);
        UtteranceListener listener = generateUtteranceListener(createUtteranceListener(listeners), listeners);
        if (listenersMap.containsKey(utteranceId)) {
            utteranceId = utteranceId + "_" + listenersMap.size();
        }
        final String uId = utteranceId;
        HashMap<String, String> params = buildParams(uId, String.valueOf(audioHandler.getMainStreamType()));
        if (listener != null) handleListener(uId, listener);
        Map<String, Object> map = new HashMap<>();
        map.put(MAP_UTTERANCE_ID, uId);
        map.put(MAP_MESSAGE, text);
        map.put(MAP_PARAMS, params);
        logger.i(TAG, "TTS[%s] - ready: %s | speaking: %s",
                uId, Boolean.toString(isReady), Boolean.toString(isSpeaking));
        initTts(status -> {
            if (status == SynthesizerListener.SUCCESS) {
                playTheCurrentQueue(map);
            }
            else {
                logger.e(TAG, "TTS[%s] - no queue status ERROR", uId);
                getUtteranceListener().onError(uId, SynthesizerListener.ERROR);
            }
        });
    }

    @WorkerThread
    @Override
    public  <T extends SynthesizerListener> void playSilence(long durationInMillis, String queueId, T... listeners) {
        if (durationInMillis <= 0) throw new IllegalArgumentException("Silence duration must be greater than 0");
        String utteranceId = DEFAULT_UTTERANCE_ID + System.nanoTime();
        logger.i(TAG, "TTS[%s] - play silence on queue <%s>", utteranceId, queueId);
        UtteranceListener listener = generateUtteranceListener(createUtteranceListener(listeners), listeners);
        if (listenersMap.containsKey(utteranceId)) {
            utteranceId = utteranceId + "_" + listenersMap.size();
        }
        final String uId = utteranceId;
        if (listener != null) handleListener(uId, listener);
        addToQueue(uId, null, durationInMillis, null, queueId);
        logger.i(TAG, "TTS[%s] - ready: %s | speaking: %s | held: %s",
                uId, Boolean.toString(isReady), Boolean.toString(isSpeaking), Boolean.toString(isOnHold));
        if (isTtsNull() && !isSpeaking) {
            setSpeaking(true);
            initTts(status -> {
                if (status == SynthesizerListener.SUCCESS) {
                    run();
                }
                else {
                    logger.e(TAG, "TTS[%s] - silence status ERROR with queue <%s>", uId, queueId);
                    getUtteranceListener().onError(uId, SynthesizerListener.ERROR);
                }
            });
        }
        else if (isReady && !isSpeaking && !isOnHold) {
            run();
        }
    }

    @WorkerThread
    @Override
    public  <T extends SynthesizerListener> void playSilence(long durationInMillis, T... listeners) {
        if (durationInMillis <= 0) throw new IllegalArgumentException("Silence duration must be greater than 0");
        String utteranceId = DEFAULT_UTTERANCE_ID + System.nanoTime();
        logger.i(TAG, "TTS[%s] - immediately play silence", utteranceId);
        UtteranceListener listener = generateUtteranceListener(createUtteranceListener(listeners), listeners);
        if (listenersMap.containsKey(utteranceId)) {
            utteranceId = utteranceId + "_" + listenersMap.size();
        }
        final String uId = utteranceId;
        if (listener != null) handleListener(uId, listener);
        Map<String, Object> map = new HashMap<>();
        map.put(MAP_UTTERANCE_ID, uId);
        map.put(MAP_SILENCE, durationInMillis);
        logger.i(TAG, "TTS[%s] - ready: %s | speaking: %s",
                uId, Boolean.toString(isReady), Boolean.toString(isSpeaking));
        initTts(status -> {
            if (status == SynthesizerListener.SUCCESS) {
                playTheCurrentQueue(map);
            }
            else {
                logger.e(TAG, "TTS[%s] - no queue silence status ERROR", uId);
                getUtteranceListener().onError(uId, SynthesizerListener.ERROR);
            }
        });
    }

    @Override
    public void releaseCurrentQueue() {
        logger.w(TAG, "TTS - isOnHold set to false");
        isOnHold = false;
    }

    @Override
    public void holdCurrentQueue() {
        logger.w(TAG, "TTS - isOnHold set to true");
        isOnHold = true;
    }

    @CallSuper
    @Override
    public void stop() {
        if (utteranceListener != null)
            utteranceListener.clearTimeout();
        // Stop Bluetooth Sco if required
        bluetoothSco.stopSco();
        // Audio focus
        audioHandler.abandonAudioFocus();
        setSpeaking(false);
        releaseCurrentQueue();
    }

    @CallSuper
    @Override
    public void release() {
        synchronized (lock) {
            listenersMap.clear();
            queue.clear();
            queue.put(DEFAULT_QUEUE_ID, new ConcurrentLinkedQueue<>());
        }
        //TODO: ?filters.clear();
        queueId = DEFAULT_QUEUE_ID;
        releaseCurrentQueue();
        setReady(false);
        setSpeaking(false);
        logger.v(TAG, "TTS - states and resources released");
    }

    @Override
    public void resume() {
        if (isSpeaking) return;
        String utteranceId = null;
        if (!isEmpty() && queue.containsKey(getCurrentQueueId())) {
            Map map = queue.get(getCurrentQueueId()).peek();
            if (map != null && map.containsKey(MAP_UTTERANCE_ID))
                utteranceId = (String) map.get(MAP_UTTERANCE_ID);
        }
        final String uId = utteranceId;
        logger.i(getTag(), "TTS[%s] - resume queue <%s>", uId, queueId);
        setSpeaking(true);
        initTts(status -> {
            if (status == SynthesizerListener.SUCCESS) {
                run();
            }
            else {
                logger.e(getTag(), "TTS - status ERROR");
                if (uId != null)
                    getUtteranceListener().onError(uId, SynthesizerListener.ERROR);
            }
        });
    }

    private void run() {
        checkForEmptyCurrentQueue();
        if (!isEmpty()) {
            // Gets and plays the current message in the queue
            playTheCurrentQueue(queue.get(queueId).poll());
        }
    }

    void checkForEmptyCurrentQueue() {
        if (isCurrentQueueEmpty()) {
            logger.v(getTag(), "TTS - no more messages in the queue <%s>", queueId);
            moveToNextQueue();
        }
    }

    private void playTheCurrentQueue(Map<String, Object> map) {
        // Check whether Sco is connected or required
        logger.i(getTag(), "TTS - is bluetooth Sco required: %s",
                Boolean.toString(configuration.isBluetoothScoRequired()));
        if (configuration.isBluetoothScoRequired() && !bluetoothSco.isBluetoothScoOn()) {
            // Sco Listener
            BluetoothScoListener listener = new BluetoothScoListener() {
                @Override
                public void onConnected() {
                    logger.i(getTag(), "TTS - Sco onConnected");
                    runMap(map);
                }

                @Override
                public void onDisconnected() {
                    logger.i(getTag(), "TTS - Sco onDisconnected");
                    if (bluetoothSco.isBluetoothScoOn()) {
                        logger.w(getTag(), "TTS - shutting down from Sco listener");
                        shutdown();
                    }
                }
            };
            // Start Bluetooth Sco
            bluetoothSco.startSco(listener);
            logger.v(getTag(), "TTS - waiting for bluetooth sco connection");
        }
        else {
            logger.v(getTag(), "TTS - bluetooth sco is: %s", (bluetoothSco.isBluetoothScoOn() ? "on" : "off"));
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
            logger.v(getTag(), "TTS - remove empty queue: <%s>", queueId);
            synchronized (lock) {
                queue.remove(queueId);
            }
        }
        boolean isLastQueueEqualToCurrent = Objects.equals(lastQueueId, queueId);
        queueId = getNextQueueId();
        if (queueId == null) {
            queueId = DEFAULT_QUEUE_ID;
            if (isLastQueueEqualToCurrent) {
                logger.v(getTag(), "TTS - queue from <%s> to <%s>", lastQueueId, queueId);
                lastQueueId = queueId;
            }
        }
        logger.v(getTag(), "TTS - new queue <%s>", queueId);
    }

    private void handleListener(@NonNull String utteranceId, @NonNull UtteranceListener listener) {
        logger.v(getTag(), "TTS[%s] - added new utterance listener -> map size: %s", utteranceId, listenersMap.size());
        synchronized (lock) {
            listenersMap.put(utteranceId, listener);
        }
    }

    private void addToQueue(@NonNull String utteranceId, String message, long duration, @Nullable HashMap<String, String> params, @NonNull String queueId) {
        Map<String, Object> map = new HashMap<>();
        map.put(MAP_UTTERANCE_ID, utteranceId);
        if (message != null) map.put(MAP_MESSAGE, message);
        if (duration > 0) map.put(MAP_SILENCE, duration);
        if (params != null) map.put(MAP_PARAMS, params);
        if (!queue.containsKey(queueId)) {
            logger.v(getTag(), "TTS[%s] - added new queue <%s>", utteranceId, queueId);
            lastQueueId = queueId;
            synchronized (lock) {
                queue.put(queueId, new ConcurrentLinkedQueue<>());
            }
        }
        queue.get(queueId).add(map);
        logger.v(getTag(), "TTS[%s] - added message to queue <%s>",utteranceId, queueId);
        logger.v(getTag(), "TTS[%s] - queues [%s] ", utteranceId, queue.size());
        logger.v(getTag(), "TTS[%s] - messages in queue <%s> = %s", utteranceId, queueId, queue.get(queueId).size());
    }
}
