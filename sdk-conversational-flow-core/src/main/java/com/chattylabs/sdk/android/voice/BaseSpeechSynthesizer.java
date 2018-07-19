package com.chattylabs.sdk.android.voice;

import android.speech.tts.TextToSpeech;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

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
import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.SynthesizerListenerContract;
import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.TAG;

abstract class BaseSpeechSynthesizer implements SpeechSynthesizer {

    // Constants
    private static final String DEFAULT_UTTERANCE_ID = BuildConfig.APPLICATION_ID + ".utterance:";
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
    protected ILogger logger;

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

    void setReady(boolean ready) {
        isReady = ready;
    }

    boolean isReady() {
        return isReady;
    }

    public boolean isSpeaking() {
        return isSpeaking;
    }

    public void setSpeaking(boolean speaking) {
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

    public Map<String, UtteranceListener> getListenersMap() {
        return listenersMap;
    }

    public UtteranceListener getUtteranceListener() {
        return utteranceListener;
    }

    public void setUtteranceListener(UtteranceListener utteranceListener) {
        this.utteranceListener = utteranceListener;
    }

    abstract String getTag();

    abstract void initTts(
            TextToSpeech.OnInitListener onInitListener, 
            OnSynthesizerInitialised onCheckLanguageInit);

    abstract void executeOnTtsReady(
            String utteranceId, String text, HashMap<String, String> params);

    abstract void playSilence(String utteranceId, long durationInMillis);

    abstract HashMap<String,String> buildParams(String uId, String s);

    abstract boolean isTtsNull();

    abstract boolean isTtsSpeaking();

    abstract UtteranceListener createUtteranceListener(
            SynthesizerListenerContract[] listeners);

    private UtteranceListener generateUtteranceListener(
            UtteranceListener listener, SynthesizerListenerContract[] listeners) {
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
    public <T extends ConversationalFlowComponent.SynthesizerListenerContract> void playText(String text, String queueId, T... listeners) {
        UtteranceListener listener = generateUtteranceListener(createUtteranceListener(listeners), listeners);
        playText(text, queueId, listener, DEFAULT_UTTERANCE_ID + System.nanoTime());
    }

    protected void playText(String text, String queueId, @Nullable UtteranceListener listener, String utteranceId) {
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
        if (isTtsNull()) {
            initTts(status -> {
                if (status == TextToSpeech.SUCCESS) {
                    isReady = true;
                    resume();
                }
                else {
                    logger.e(TAG, "TTS - with queue status ERROR");
                    if (listenersMap.containsKey(uId)) {
                        UtteranceListener utteranceProgressListener;
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
    public <T extends ConversationalFlowComponent.SynthesizerListenerContract> void playText(String text, T... listeners) {
        String utteranceId = DEFAULT_UTTERANCE_ID + System.nanoTime();
        logger.i(TAG, "TTS - prepare to immediately playText \"" + text + "\" - " + utteranceId);
        UtteranceListener listener = generateUtteranceListener(createUtteranceListener(listeners), listeners);
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
    public void releaseCurrentQueue() {
        isOnHold = false;
    }

    @Override
    public void holdCurrentQueue() {
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
        isSpeaking = false;
        logger.w(TAG, "TTS - Speaking set to false");
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
        filters.clear();
        queueId = DEFAULT_QUEUE_ID;
        isReady = false;
        isOnHold = false;
        isSpeaking = false;
        logger.v(TAG, "TTS - states and resources released");
    }

    @Override
    public void resume() {
        if (isSpeaking) return;
        initTts(status -> {
            if (status == TextToSpeech.SUCCESS) {
                logger.i(getTag(), "TTS - resume queue: <" + queueId + ">");
                checkForEmptyCurrentQueue();
                if (!isEmpty()) {
                    isSpeaking = true;
                    // Gets and plays the current message in the queue
                    playTheCurrentQueue(queue.get(queueId).poll());
                }
            }
            else {
                logger.e(getTag(), "TTS - status ERROR");
                shutdown();
            }
        }, null);
    }

    void checkForEmptyCurrentQueue() {
        if (isCurrentQueueEmpty()) {
            logger.v(getTag(), "TTS - no more messages in the queue <" + queueId + ">");
            moveToNextQueue();
        }
    }

    private void playTheCurrentQueue(Map<String, Object> map) {
        // Check whether Sco is connected or required
        logger.i(getTag(), "TTS - is bluetooth Sco required: " +
                Boolean.toString(configuration.isBluetoothScoRequired()));
        if (configuration.isBluetoothScoRequired() && !bluetoothSco.isBluetoothScoOn()) {
            // Sco Listener
            BluetoothScoListener listener = new BluetoothScoListener() {
                @Override
                public void onConnected() {
                    logger.w(getTag(), "TTS - Sco onConnected");
                    runMap(map);
                }

                @Override
                public void onDisconnected() {
                    logger.w(getTag(), "TTS - Sco onDisconnected");
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
            logger.v(getTag(), "TTS - bluetooth sco is: " + (bluetoothSco.isBluetoothScoOn() ? "on" : "off"));
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
            logger.v(getTag(), "TTS - remove empty queue: <" + queueId + ">");
            synchronized (lock) {
                queue.remove(queueId);
            }
        }
        boolean isLastQueueEqualToCurrent = Objects.equals(lastQueueId, queueId);
        queueId = getNextQueueId();
        if (queueId == null) {
            queueId = DEFAULT_QUEUE_ID;
            if (isLastQueueEqualToCurrent) {
                logger.v(getTag(), "TTS - update last queue from <" + lastQueueId + "> to <" + queueId + ">");
                lastQueueId = queueId;
            }
        }
        logger.v(getTag(), "TTS - New queue: <" + queueId + ">");
    }

    private void handleListener(@NonNull String utteranceId, @NonNull UtteranceListener listener) {
        logger.v(getTag(), "TTS - added utterance - " + utteranceId + " - listener -> size:  " + listenersMap.size());
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
            logger.v(getTag(), "TTS - added queue: <" + queueId + "> - " + utteranceId);
            lastQueueId = queueId;
            synchronized (lock) {
                queue.put(queueId, new ConcurrentLinkedQueue<>());
            }
        }
        queue.get(queueId).add(map);
        logger.v(getTag(), "TTS - added message to queue <" + queueId + ">. Number of queues: " + queue.size());
        logger.v(getTag(), "TTS - messages in the queue <" + queueId + ">: " + queue.get(queueId).size());
    }
}
