package com.chattylabs.sdk.android.voice;

import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.chattylabs.android.commons.internal.ILogger;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.TAG;

/**
 * This is the base class that handles internally and holds a queue of messages to be played out.
 * <br/>You only need to implement the contract of {@link SpeechSynthesizerComponent} and extend to this
 * class.
 * <p/>
 * The technical requirements while implementing the {@link SpeechSynthesizerComponent} through
 * this base class are:
 * <p/>
 * - You will create a constructor that receives the following parameters in the same order:
 * <br/><pre>{@code
 * public Constructor(Application, ComponentConfig, AndroidAudioManager, BluetoothSco, ILogger) {
 *     super(ComponentConfig, AndroidAudioManager, BluetoothSco, ILogger);
 *     //...
 * }
 * }</pre>
 * If the parameters are not in the same order the addon initialization will throw an Exception.
 * <p/>
 * - You will implement {@link #checkStatus(SynthesizerListener.OnStatusChecked)} which is
 * in charge of testing whether the client provider work and to check whether the required language
 * is available.
 * <p/>
 * - You will implement {@link #prepare(SynthesizerListener.OnPrepared)} which is the entry point
 * for any call to {@link #playText(String, SynthesizerListener[])} or
 * {@link #playSilence(long, SynthesizerListener[])} and its variations.
 * <br/>This method behaves like {@link #checkStatus(SynthesizerListener.OnStatusChecked)} but it stores the current
 * Text To Speech client, sets up the current required language, and initializes a
 * {@link SynthesizerUtteranceListener}.
 * <br/>This method should check whether the current client instance is available or create a new one.
 * <p/>
 * - You will implement {@link #createUtteranceListener(SynthesizerListener...)} where you handle
 * the lifecycle of the played utterance.
 * <p/>
 * - You will implement {@link #executeOnTtsReady(String, String, HashMap)} where you can apply any
 * {@link TextFilter}, escape HTML entities, split a string into chunks if needed, any other treatment
 * on the message to be played and ultimately to play the message through the implemented Provider.
 *
 * @see SynthesizerListener
 * @see SpeechSynthesizerComponent
 * @see SynthesizerUtteranceListener
 * @see android.app.Application
 * @see ComponentConfig
 * @see AndroidAudioManager
 * @see BluetoothSco
 * @see ILogger
 */
abstract class BaseSpeechSynthesizer implements SpeechSynthesizerComponent {

    // Constants
    static final String DEFAULT_QUEUE_ID = "com.chattylabs.sdk.android.voice:default_queue_id";
    private static final String DEFAULT_UTTERANCE_ID = "u:";
    private static final String MAP_UTTERANCE_ID = "utteranceId";
    private static final String MAP_SILENCE = "silence";
    private static final String MAP_MESSAGE = "message";
    private static final String MAP_PARAMS = "params";

    // Data
    private final ComponentConfig configuration;
    private final LinkedHashMap<String, SynthesizerUtteranceListener> listenersMap;
    private final LinkedHashMap<String, ConcurrentLinkedQueue<Map<String, Object>>> queue;
    private final List<TextFilter> filters;
    private final Object lock = new Object();

    // States
    private boolean isReady;
    private boolean isOnHold;
    private boolean isSpeaking;

    // Resources
    private String queueId = DEFAULT_QUEUE_ID;
    private SynthesizerUtteranceListener synthesizerUtteranceListener;
    private String lastQueueId;
    private final AndroidAudioManager audioManager;
    private final BluetoothSco bluetoothSco;

    // Log stuff
    protected final ILogger logger;

    BaseSpeechSynthesizer(ComponentConfig configuration,
                          AndroidAudioManager audioManager,
                          BluetoothSco bluetoothSco,
                          ILogger logger) {
        this.configuration = configuration;
        this.filters = new LinkedList<>();
        this.listenersMap = new LinkedHashMap<>();
        this.queue = new LinkedHashMap<>();
        this.audioManager = audioManager;
        this.bluetoothSco = bluetoothSco;
        this.logger = logger;
    }

    abstract String getTag();

    abstract void prepare(SynthesizerListener.OnPrepared onSynthesizerPrepared);

    abstract void executeOnTtsReady(String utteranceId, String text, HashMap<String, String> params);

    abstract void playSilence(String utteranceId, long durationInMillis);

    abstract HashMap<String,String> buildParams(String uId, String s);

    abstract boolean isTtsNull();

    abstract boolean isTtsSpeaking();

    abstract SynthesizerUtteranceListener createUtteranceListener(SynthesizerListener[] listeners);

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

    SynthesizerUtteranceListener removeListener(String utteranceId) {
        synchronized (lock) {
            return getListenersMap().remove(utteranceId);
        }
    }

    Map<String, SynthesizerUtteranceListener> getListenersMap() {
        return listenersMap;
    }

    SynthesizerUtteranceListener getSynthesizerUtteranceListener() {
        return synthesizerUtteranceListener;
    }

    void setSynthesizerUtteranceListener(SynthesizerUtteranceListener synthesizerUtteranceListener) {
        this.synthesizerUtteranceListener = synthesizerUtteranceListener;
    }

    public ComponentConfig getConfiguration() {
        return configuration;
    }

    private SynthesizerUtteranceListener generateUtteranceListener(
            SynthesizerUtteranceListener listener, SynthesizerListener[] listeners) {
        if (listeners.length > 0) {
            for (SynthesizerListener item : listeners) {
                if (item instanceof SynthesizerListener.OnStart) {
                    listener._setOnStartedListener((SynthesizerListener.OnStart) item);
                }
                if (item instanceof SynthesizerListener.OnDone) {
                    listener._setOnDoneListener((SynthesizerListener.OnDone) item);
                }
                if (item instanceof SynthesizerListener.OnError) {
                    listener._setOnErrorListener((SynthesizerListener.OnError) item);
                }
            }
        }
        return listener;
    }

    @Override
    public void playText(String text, String queueId, SynthesizerListener... listeners) {
        SynthesizerUtteranceListener listener = generateUtteranceListener(createUtteranceListener(listeners), listeners);
        playText(text, queueId, listener, DEFAULT_UTTERANCE_ID + System.nanoTime());
    }

    void playText(String text, String queueId,
                  @Nullable SynthesizerUtteranceListener listener, String utteranceId) {
        logger.i(TAG, "TTS[%s] - play \"%s\" on queue <%s>", utteranceId, text, queueId);
        if (listenersMap.containsKey(utteranceId)) {
            utteranceId = utteranceId + "_" + listenersMap.size();
        }
        final String uId = utteranceId;
        HashMap<String, String> params = buildParams(uId, String.valueOf(audioManager.getMainStreamType()));
        if (listener != null) handleListener(uId, listener);
        addToQueue(uId, text, -1, params, queueId);
        logger.i(TAG, "TTS[%s] - ready: %s | speaking: %s | held: %s",
                uId, Boolean.toString(isReady), Boolean.toString(isSpeaking), Boolean.toString(isOnHold));
        if (isTtsNull() && !isSpeaking) {
            setSpeaking(true);
            prepare(status -> {
                if (status == SynthesizerListener.Status.SUCCESS) {
                    run();
                }
                else {
                    logger.e(TAG, "TTS[%s] - status ERROR with queue <%s>", uId, queueId);
                    getSynthesizerUtteranceListener().onError(uId, SynthesizerListener.Status.ERROR);
                }
            });
        }
        else if (isReady && !isSpeaking && !isOnHold) {
            setSpeaking(true);
            run();
        }
    }

    @Override
    public void playText(String text, SynthesizerListener... listeners) {
        String utteranceId = DEFAULT_UTTERANCE_ID + System.nanoTime();
        logger.i(TAG, "TTS[%s] - immediately play \"%s\"", utteranceId, text);
        SynthesizerUtteranceListener listener = generateUtteranceListener(createUtteranceListener(listeners), listeners);
        if (listenersMap.containsKey(utteranceId)) {
            utteranceId = utteranceId + "_" + listenersMap.size();
        }
        final String uId = utteranceId;
        HashMap<String, String> params = buildParams(uId, String.valueOf(audioManager.getMainStreamType()));
        if (listener != null) handleListener(uId, listener);
        Map<String, Object> map = new HashMap<>();
        map.put(MAP_UTTERANCE_ID, uId);
        map.put(MAP_MESSAGE, text);
        map.put(MAP_PARAMS, params);
        logger.i(TAG, "TTS[%s] - ready: %s | speaking: %s",
                uId, Boolean.toString(isReady), Boolean.toString(isSpeaking));
        prepare(status -> {
            if (status == SynthesizerListener.Status.SUCCESS) {
                playTheCurrentQueue(map);
            }
            else {
                logger.e(TAG, "TTS[%s] - no queue status ERROR", uId);
                getSynthesizerUtteranceListener().onError(uId, SynthesizerListener.Status.ERROR);
            }
        });
    }

    @Override
    public  void playSilence(long durationInMillis, String queueId,
                             SynthesizerListener... listeners) {
        if (durationInMillis <= 0) throw new IllegalArgumentException("Silence duration must be greater than 0");
        String utteranceId = DEFAULT_UTTERANCE_ID + System.nanoTime();
        logger.i(TAG, "TTS[%s] - play silence on queue <%s>", utteranceId, queueId);
        SynthesizerUtteranceListener listener = generateUtteranceListener(createUtteranceListener(listeners), listeners);
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
            prepare(status -> {
                if (status == SynthesizerListener.Status.SUCCESS) {
                    run();
                }
                else {
                    logger.e(TAG, "TTS[%s] - silence status ERROR with queue <%s>", uId, queueId);
                    getSynthesizerUtteranceListener().onError(uId, SynthesizerListener.Status.ERROR);
                }
            });
        }
        else if (isReady && !isSpeaking && !isOnHold) {
            setSpeaking(true);
            run();
        }
    }

    @Override
    public void playSilence(long durationInMillis, SynthesizerListener... listeners) {
        if (durationInMillis <= 0) throw new IllegalArgumentException("Silence duration must be greater than 0");
        String utteranceId = DEFAULT_UTTERANCE_ID + System.nanoTime();
        logger.i(TAG, "TTS[%s] - immediately play silence", utteranceId);
        SynthesizerUtteranceListener listener = generateUtteranceListener(createUtteranceListener(listeners), listeners);
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
        prepare(status -> {
            if (status == SynthesizerListener.Status.SUCCESS) {
                playTheCurrentQueue(map);
            }
            else {
                logger.e(TAG, "TTS[%s] - no queue silence status ERROR", uId);
                getSynthesizerUtteranceListener().onError(uId, SynthesizerListener.Status.ERROR);
            }
        });
    }

    @Override
    public void freeCurrentQueue() {
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
        if (synthesizerUtteranceListener != null)
            synthesizerUtteranceListener.clearTimeout(null);
        // Stop Bluetooth Sco if required
        bluetoothSco.stopSco();
        // Audio focus
        audioManager.abandonAudioFocus();
        setSpeaking(false);
        freeCurrentQueue();
    }

    @CallSuper
    @Override
    public void release() {
        synchronized (lock) {
            listenersMap.clear();
            queue.clear();
            queue.put(DEFAULT_QUEUE_ID, new ConcurrentLinkedQueue<>());
        }
        queueId = DEFAULT_QUEUE_ID;
        freeCurrentQueue();
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
        if (!isEmpty()) setSpeaking(true);
        prepare(status -> {
            if (status == SynthesizerListener.Status.SUCCESS) {
                run();
            }
            else {
                logger.e(getTag(), "TTS - status ERROR");
                if (uId != null)
                    getSynthesizerUtteranceListener().onError(uId, SynthesizerListener.Status.ERROR);
            }
        });
    }

    private void run() {
        moveToNextQueueIfNeeded();
        if (!isEmpty()) {
            // Gets and plays the current message in the queue
            playTheCurrentQueue(queue.get(queueId).poll());
        }
    }

    void moveToNextQueueIfNeeded() {
        if (isEmpty()) {
            logger.v(getTag(), "TTS - no more messages in the queue <%s>", queueId);
            // is empty, still contains the queue id and it's not the default one
            String oldQueueId = queueId;
            if (queue.containsKey(queueId) && !DEFAULT_QUEUE_ID.equals(queueId)) {
                logger.v(getTag(), "TTS - remove empty queue: <%s>", queueId);
                synchronized (lock) {
                    queue.remove(queueId);
                }
            }
            boolean isLastQueueEqualsToCurrent = Objects.equals(lastQueueId, queueId);
            queueId = getNextQueueId();
            if (queueId == null) {
                if (isLastQueueEqualsToCurrent) {
                    lastQueueId = DEFAULT_QUEUE_ID;
                }
                queueId = DEFAULT_QUEUE_ID;
            }
            logger.v(getTag(), "TTS - moved queue from <%s> to <%s>", oldQueueId, queueId);
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
                    chooseItemToPlay(map);
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
            chooseItemToPlay(map);
        }
    }

    private void chooseItemToPlay(Map<String, Object> map) {
        audioManager.requestAudioFocus(configuration.isAudioExclusiveRequiredForSynthesizer());
        if (map.containsKey(MAP_MESSAGE)) {
            //noinspection unchecked
            executeOnTtsReady((String) map.get(MAP_UTTERANCE_ID),
                    (String) map.get(MAP_MESSAGE),
                    (HashMap<String, String>) map.get(MAP_PARAMS));
        }
        else if (map.containsKey(MAP_SILENCE)) {
            //noinspection unchecked
            playSilence((String) map.get(MAP_UTTERANCE_ID), (long) map.get(MAP_SILENCE));
        } else
            throw new RuntimeException("No message or silence item to play");
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
    public boolean isEmpty() {
        return queue.isEmpty() || (queue.containsKey(queueId) && queue.get(queueId).isEmpty());
    }

    @Override
    public Set<String> getQueueSet() {
        return queue.keySet();
    }

    private void handleListener(@NonNull String utteranceId,
                                @NonNull SynthesizerUtteranceListener listener) {
        logger.v(getTag(), "TTS[%s] - added new utterance listener -> map size: %s", utteranceId, listenersMap.size());
        synchronized (lock) {
            listenersMap.put(utteranceId, listener);
        }
    }

    private void addToQueue(@NonNull String utteranceId, String message,
                            long duration, @Nullable HashMap<String, String> params,
                            @NonNull String queueId) {
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
