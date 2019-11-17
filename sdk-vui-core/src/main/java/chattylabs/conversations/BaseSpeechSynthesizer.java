package chattylabs.conversations;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.chattylabs.android.commons.internal.ILogger;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * This is the base class that handles internally and holds a queue of messages to be played out.
 * <br/>You only need to implement the contract of {@link SpeechSynthesizer} and extend to this
 * class.
 * <p/>
 * The technical requirements while implementing the {@link SpeechSynthesizer} through
 * this base class are:
 * <p/>
 * - You will create a constructor that receives the following parameters in the same order:
 * <br/><pre>{@code
 * public Constructor(Application, ComponentConfig, AndroidAudioManager, AndroidBluetooth, ILogger) {
 *     super(ComponentConfig, AndroidAudioManager, AndroidBluetooth, ILogger);
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
 * - You will implement {@link #createDelegateUtteranceListener()} where you handle
 * the lifecycle of the played utterance.
 * <p/>
 * - You will implement {@link #executeOnEngineReady(String, String)} where you can apply any
 * {@link TextFilter}, escape HTML entities, split a string into chunks if needed, any other treatment
 * on the message to be played and ultimately to play the message through the implemented Provider.
 *
 * @see SynthesizerListener
 * @see SpeechSynthesizer
 * @see SynthesizerUtteranceListener
 * @see android.app.Application
 * @see ComponentConfig
 * @see AndroidAudioManager
 * @see AndroidBluetooth
 * @see ILogger
 */
abstract class BaseSpeechSynthesizer implements SpeechSynthesizer {
    private final String TAG;

    // Log stuff
    protected final ILogger logger;

    // Constants
    protected static final String DEFAULT_QUEUE_ID = "chattylabs.conversations:default_queue_id";
    private static final String DEFAULT_UTTERANCE_ID = "u:";
    private static final String MAP_UTTERANCE_ID = "utteranceId";
    private static final String MAP_SILENCE = "silence";
    private static final String MAP_MESSAGE = "message";

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
    private boolean isChecking;

    // Resources
    private final AndroidAudioManager audioManager;
    private final AndroidBluetooth bluetooth;
    private String queueId = DEFAULT_QUEUE_ID;
    private SynthesizerUtteranceListener synthesizerUtteranceListener;
    private String lastQueueId;
    private File tempFile;
    MediaPlayer mediaPlayer;

    BaseSpeechSynthesizer(ComponentConfig configuration,
                          AndroidAudioManager audioManager,
                          AndroidBluetooth bluetooth,
                          ILogger logger,
                          String tag) {
        this.configuration = configuration;
        this.filters = new LinkedList<>();
        this.listenersMap = new LinkedHashMap<>();
        this.queue = new LinkedHashMap<>();
        this.audioManager = audioManager;
        this.bluetooth = bluetooth;
        this.logger = logger;
        this.TAG = tag;
    }

    abstract void prepare(SynthesizerListener.OnPrepared onSynthesizerPrepared);

    abstract void executeOnEngineReady(String utteranceId, String text);

    abstract void playSilence(String utteranceId, long durationInMillis);

    abstract boolean isTtsNull();

    abstract boolean isTtsSpeaking();

    abstract SynthesizerUtteranceListener createDelegateUtteranceListener();

    public boolean isReady() {
        return isReady;
    }

    void setReady(boolean ready) {
        logger.w(TAG, "ready set to " + Boolean.toString(ready));
        isReady = ready;
    }

    public boolean isSpeaking() {
        return isSpeaking;
    }

    void setSpeaking(boolean speaking) {
        logger.w(TAG, "speaking set to " + Boolean.toString(speaking));
        isSpeaking = speaking;
    }

    @Override
    public List<TextFilter> getFilters() {
        return filters;
    }

    @Override
    public void addFilter(TextFilter filter) {
        filters.add(filter);
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

    public AndroidAudioManager getAudioManager() {
        return audioManager;
    }

    private SynthesizerUtteranceListener generateUtteranceListener(SynthesizerListener[] listeners) {
        SynthesizerUtteranceListener listener = createDelegateUtteranceListener();
        if (listeners != null) {
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
        SynthesizerUtteranceListener listener = generateUtteranceListener(listeners);
        playText(text, queueId, listener, DEFAULT_UTTERANCE_ID + System.nanoTime());
    }

    void playText(String text, String queueId,
                  @Nullable SynthesizerUtteranceListener listener, String utteranceId) {
        logger.i(TAG, "[%s] - play \"%s\" on queue <%s>", utteranceId, text, queueId);
        if (listenersMap.containsKey(utteranceId)) {
            utteranceId = utteranceId + "_" + listenersMap.size();
        }
        final String uId = utteranceId;
        if (listener != null) handleListener(uId, listener);
        addToQueue(uId, text, -1, queueId);
        logger.i(TAG, "[%s] - ready: %s | speaking: %s | held: %s",
                uId, Boolean.toString(isReady), Boolean.toString(isSpeaking), Boolean.toString(isOnHold));
        if (isTtsNull() && !isSpeaking) {
            setSpeaking(true);
            prepare(status -> {
                if (status == SynthesizerListener.Status.SUCCESS) {
                    run();
                }
                else {
                    logger.e(TAG, "[%s] - status ERROR with queue <%s>", uId, queueId);
                    SynthesizerUtteranceListener synthesizerUtteranceListener = removeListener(uId);
                    shutdown();
                    getAudioManager().abandonAudioFocus();
                    synthesizerUtteranceListener.onError(uId, SynthesizerListener.Status.ERROR);
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
        logger.i(TAG, "[%s] - immediately play \"%s\"", utteranceId, text);
        SynthesizerUtteranceListener listener = generateUtteranceListener(listeners);
        if (listenersMap.containsKey(utteranceId)) {
            utteranceId = utteranceId + "_" + listenersMap.size();
        }
        final String uId = utteranceId;
        if (listener != null) handleListener(uId, listener);
        Map<String, Object> map = new HashMap<>();
        map.put(MAP_UTTERANCE_ID, uId);
        map.put(MAP_MESSAGE, text);
        logger.i(TAG, "[%s] - ready: %s | speaking: %s",
                uId, Boolean.toString(isReady), Boolean.toString(isSpeaking));
        prepare(status -> {
            if (status == SynthesizerListener.Status.SUCCESS) {
                playTheCurrentQueue(map);
            }
            else {
                logger.e(TAG, "[%s] - no queue status ERROR", uId);
                SynthesizerUtteranceListener synthesizerUtteranceListener = removeListener(uId);
                shutdown();
                getAudioManager().abandonAudioFocus();
                synthesizerUtteranceListener.onError(uId, SynthesizerListener.Status.ERROR);
            }
        });
    }

    @Override
    public  void playSilence(long durationInMillis, String queueId,
                             SynthesizerListener... listeners) {
        if (durationInMillis <= 0) throw new IllegalArgumentException("Silence duration must be greater than 0");
        String utteranceId = DEFAULT_UTTERANCE_ID + System.nanoTime();
        logger.i(TAG, "[%s] - play silence on queue <%s>", utteranceId, queueId);
        SynthesizerUtteranceListener listener = generateUtteranceListener(listeners);
        if (listenersMap.containsKey(utteranceId)) {
            utteranceId = utteranceId + "_" + listenersMap.size();
        }
        final String uId = utteranceId;
        if (listener != null) handleListener(uId, listener);
        addToQueue(uId, null, durationInMillis, queueId);
        logger.i(TAG, "[%s] - ready: %s | speaking: %s | held: %s",
                uId, Boolean.toString(isReady), Boolean.toString(isSpeaking), Boolean.toString(isOnHold));
        if (isTtsNull() && !isSpeaking) {
            setSpeaking(true);
            prepare(status -> {
                if (status == SynthesizerListener.Status.SUCCESS) {
                    run();
                }
                else {
                    logger.e(TAG, "[%s] - silence status ERROR with queue <%s>", uId, queueId);
                    SynthesizerUtteranceListener synthesizerUtteranceListener = removeListener(uId);
                    shutdown();
                    getAudioManager().abandonAudioFocus();
                    synthesizerUtteranceListener.onError(uId, SynthesizerListener.Status.ERROR);
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
        logger.i(TAG, "[%s] - immediately play silence", utteranceId);
        SynthesizerUtteranceListener listener = generateUtteranceListener(listeners);
        if (listenersMap.containsKey(utteranceId)) {
            utteranceId = utteranceId + "_" + listenersMap.size();
        }
        final String uId = utteranceId;
        if (listener != null) handleListener(uId, listener);
        Map<String, Object> map = new HashMap<>();
        map.put(MAP_UTTERANCE_ID, uId);
        map.put(MAP_SILENCE, durationInMillis);
        logger.i(TAG, "[%s] - ready: %s | speaking: %s",
                uId, Boolean.toString(isReady), Boolean.toString(isSpeaking));
        prepare(status -> {
            if (status == SynthesizerListener.Status.SUCCESS) {
                playTheCurrentQueue(map);
            }
            else {
                logger.e(TAG, "[%s] - no queue silence status ERROR", uId);
                SynthesizerUtteranceListener synthesizerUtteranceListener = removeListener(uId);
                shutdown();
                getAudioManager().abandonAudioFocus();
                synthesizerUtteranceListener.onError(uId, SynthesizerListener.Status.ERROR);
            }
        });
    }

    @Override
    public void freeCurrentQueue() {
        logger.w(TAG, "isOnHold set to false");
        isOnHold = false;
    }

    @Override
    public void holdCurrentQueue() {
        logger.w(TAG, "isOnHold set to true");
        isOnHold = true;
    }

    @Override
    public void resume() {
        if (isSpeaking || isOnHold) return;
        String utteranceId = null;
        if (!isEmpty() && queue.containsKey(getCurrentQueueId())) {
            Map map = queue.get(getCurrentQueueId()).peek();
            if (map != null && map.containsKey(MAP_UTTERANCE_ID))
                utteranceId = (String) map.get(MAP_UTTERANCE_ID);
        }
        final String uId = utteranceId;
        logger.i(TAG, "[%s] - resume queue <%s>", uId, queueId);
        if (!isEmpty()) setSpeaking(true);
        prepare(status -> {
            if (status == SynthesizerListener.Status.SUCCESS) {
                run();
            }
            else {
                logger.e(TAG, "status ERROR");
                if (uId != null) {
                    SynthesizerUtteranceListener synthesizerUtteranceListener = removeListener(uId);
                    shutdown();
                    getAudioManager().abandonAudioFocus();
                    synthesizerUtteranceListener.onError(uId, SynthesizerListener.Status.ERROR);
                }
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
            logger.v(TAG, "no more messages in the queue <%s>", queueId);
            // is empty, still contains the queue id and it's not the default one
            String oldQueueId = queueId;
            if (queue.containsKey(queueId) && !DEFAULT_QUEUE_ID.equals(queueId)) {
                logger.v(TAG, "remove empty queue: <%s>", queueId);
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
            logger.v(TAG, "moved queue from <%s> to <%s>", oldQueueId, queueId);
        }
    }

    private void playTheCurrentQueue(Map<String, Object> map) {
        // Check whether Sco is connected or required
        logger.i(TAG, "bluetooth Sco required: %s",
                Boolean.toString(configuration.isBluetoothScoRequired()));

        if (bluetooth.isScoOn() || (bluetooth.isDeviceConnected() && configuration.isBluetoothScoRequired())) {
            // Start Bluetooth Sco
            logger.w(TAG, "waiting for bluetooth Sco connection...");
            bluetooth.startSco(() -> chooseItemToPlay(map));
        } else {
            audioManager.requestAudioFocus(null, configuration.isAudioExclusiveRequiredForSynthesizer());
            chooseItemToPlay(map);
        }
    }

    private void chooseItemToPlay(Map<String, Object> map) {
        if (map.containsKey(MAP_MESSAGE)) {
            executeOnEngineReady((String) map.get(MAP_UTTERANCE_ID), (String) map.get(MAP_MESSAGE));
        }
        else if (map.containsKey(MAP_SILENCE)) {
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
        logger.v(TAG, "[%s] - added new utterance listener -> map size: %s", utteranceId, listenersMap.size());
        synchronized (lock) {
            listenersMap.put(utteranceId, listener);
        }
    }

    private void addToQueue(@NonNull String utteranceId, String message,
                            long duration, @NonNull String queueId) {
        Map<String, Object> map = new HashMap<>();
        map.put(MAP_UTTERANCE_ID, utteranceId);
        if (message != null) map.put(MAP_MESSAGE, message);
        if (duration > 0) map.put(MAP_SILENCE, duration);
        if (!queue.containsKey(queueId)) {
            logger.v(TAG, "[%s] - added new queue <%s>", utteranceId, queueId);
            lastQueueId = queueId;
            synchronized (lock) {
                queue.put(queueId, new ConcurrentLinkedQueue<>());
            }
        }
        queue.get(queueId).add(map);
        logger.v(TAG, "[%s] - added message to queue <%s>",utteranceId, queueId);
        logger.v(TAG, "[%s] - queues [%s] ", utteranceId, queue.size());
        logger.v(TAG, "[%s] - messages in queue <%s> = %s", utteranceId, queueId, queue.get(queueId).size());
    }

    File createTempFile(Context context) {
        try {
            tempFile = File.createTempFile(
                    "speech_" + System.currentTimeMillis(), ".wav",
                    context.getCacheDir());
            tempFile.deleteOnExit();
            tempFile.setReadable(true);
        } catch (Exception ex) {
            logger.logException(ex);
        }
        return tempFile;
    }

    private void finishPlayer() {
        logger.v(TAG, "stop media player");
        try {
            if (mediaPlayer.isPlaying())
                mediaPlayer.stop();
            mediaPlayer.reset();
            mediaPlayer.release();
        } catch (Exception ignored) {}
        mediaPlayer = null;
    }

    void handleSynthesizedFile(Context context, SynthesizerUtteranceListener listener, String utterance) {
        finishPlayer();
        logger.v(TAG, "start media player");
        if (tempFile != null) {
            try {
                mediaPlayer = MediaPlayer.create(context, Uri.fromFile(tempFile), null,
                        audioManager.getAudioAttributes().build(), AudioManager.AUDIO_SESSION_ID_GENERATE);
                if (mediaPlayer == null) {
                    shutdown();
                    getAudioManager().abandonAudioFocus();
                    listener.onError(utterance, SynthesizerListener.Status.UNKNOWN_ERROR);
                    return;
                }

                mediaPlayer.setOnCompletionListener(mediaPlayer -> {
                    logger.i(TAG, "media player complete");
                    finishPlayer();
                    tempFile = null;
                    listener.onDone(utterance);
                });
                mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                    logger.e(TAG, "media player error");
                    finishPlayer();
                    tempFile = null;
                    shutdown();
                    getAudioManager().abandonAudioFocus();
                    listener.onError(utterance, extra);
                    return true;
                });
                //mediaPlayer.setAudioStreamType(audioManager.getMainStreamType());
                mediaPlayer.start();
            } catch (Exception ex) {
                logger.logException(ex);
                tempFile = null;
                shutdown();
                getAudioManager().abandonAudioFocus();
                listener.onError(utterance, SynthesizerListener.Status.UNKNOWN_ERROR);
            }
        } else {
            logger.w(TAG, "no media file to play, maybe silence?");
            listener.onDone(utterance);
        }
    }

    @CallSuper
    @Override
    public void stop() {
        setSpeaking(false);
        setChecking(false);
        freeCurrentQueue();
        finishPlayer();
    }

    @CallSuper
    protected void release() {
        synchronized (lock) {
            listenersMap.clear();
            queue.clear();
            queue.put(DEFAULT_QUEUE_ID, new ConcurrentLinkedQueue<>());
        }
        tempFile = null;
        queueId = DEFAULT_QUEUE_ID;
        logger.v(TAG, "states and resources released");
    }

    public boolean isChecking() {
        return isChecking;
    }

    public void setChecking(boolean checking) {
        isChecking = checking;
    }
}
