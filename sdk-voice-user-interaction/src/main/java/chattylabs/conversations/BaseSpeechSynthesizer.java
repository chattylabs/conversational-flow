package chattylabs.conversations;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import chattylabs.android.commons.HtmlUtils;
import chattylabs.android.commons.Tag;
import chattylabs.android.commons.internal.ILogger;

/**
 * This is the base class that handles internally and holds a queue of messages to be played out.
 * <br/>You only need to implement the contract of {@link SpeechSynthesizer} and inherit from this
 * class.
 * <p/>
 * The technical requirements while implementing the {@link SpeechSynthesizer} through
 * this base class are:
 * <p/>
 * - You must create a constructor that receives the following parameters in the same order:
 * <br/><pre>{@code
 * public Constructor(Application, ComponentConfig, AndroidAudioManager, AndroidBluetooth, ILogger) {
 *     super(ComponentConfig, AndroidAudioManager, AndroidBluetooth, ILogger);
 *     //...
 * }
 * }</pre>
 * If the parameters are not in the same order the addon initialization will throw an Exception.
 * <p/>
 * - You must implement {@link #checkStatus(SynthesizerListener.OnStatusChecked)} which is
 * in charge of testing whether the TTS engine works and to check whether the required language
 * is available.
 * <p/>
 * - You must implement {@link #executeOnEngineReady(String, String)} which is where you initialize your TTS engine.
 * You can also apply any {@link Filter}, escape HTML entities, split a string into chunks if needed or any other treatment
 * upon the message to be played aloud and ultimately to play the message via your implemented TTS engine.
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
    private static final String TAG = Tag.make("BaseSpeechSynthesizer");
    // Constants
    protected static final String DEFAULT_QUEUE_ID = "chattylabs.conversations:default_queue_id";
    static final int ON_START = 1;
    static final int ON_DONE = 2;
    static final int ON_ERROR = 3;
    private static final String DEFAULT_UTTERANCE_ID = "u:";
    private static final String MAP_UTTERANCE_ID = "utteranceId";
    private static final String MAP_SILENCE = "silence";
    private static final String MAP_MESSAGE = "message";
    // Log stuff
    protected final ILogger logger;
    // Data
    private final LinkedHashMap<String, LinkedHashMap<Integer, SynthesizerListener>> listeners; //released
    private final LinkedHashMap<String, ConcurrentLinkedQueue<Map<String, Object>>> queue; //released
    private final List<Filter> filters;
    private final Object lock = new Object();
    // Resources
    private final ComponentConfig configuration;
    private final AndroidAudioManager audioManager;
    private final AndroidBluetooth bluetooth;
    private MediaPlayer mediaPlayer; //released
    private String queueId = DEFAULT_QUEUE_ID; //reset
    private String lastQueueId; //reset
    // States
    private boolean isReady; //reset
    private boolean isLocked; //reset
    private boolean isSpeaking; //reset
    private boolean isOnQueue; //reset
    private File tempFile; //released

    BaseSpeechSynthesizer(ComponentConfig configuration,
                          AndroidAudioManager audioManager,
                          AndroidBluetooth bluetooth,
                          ILogger logger) {
        this.configuration = configuration;
        this.filters = new LinkedList<>();
        this.listeners = new LinkedHashMap<>();
        this.queue = new LinkedHashMap<>();
        this.audioManager = audioManager;
        this.bluetooth = bluetooth;
        this.logger = logger;
    }


    abstract void executeOnEngineReady(String utteranceId, String text);

    abstract void playSilence(String utteranceId, long durationInMillis);

    abstract boolean isTtsSpeaking();

    abstract void forceDestroyTTS();


    private void selectListener(LinkedHashMap<Integer, SynthesizerListener> map,
                                String utteranceId, int method, int errorCode) {
        if (map != null) {
            SynthesizerListener listener = map.get(method);
            if (listener != null) {
                switch (method) {
                    case ON_START:
                        ((SynthesizerListener.OnStart) listener).execute(utteranceId);
                        break;
                    case ON_DONE:
                        ((SynthesizerListener.OnDone) listener).execute(utteranceId);
                        break;
                    case ON_ERROR:
                        ((SynthesizerListener.OnError) listener).execute(utteranceId, errorCode);
                        break;
                    default:
                        break;
                }
            }
        }
    }

    void executeListener(String utteranceId, int method, int errorCode) {
        synchronized (lock) {
            selectListener(listeners.get(utteranceId), utteranceId, method, errorCode);
        }
    }

    void removeAndExecuteListener(String utteranceId, int method, int errorCode) {
        synchronized (lock) {
            selectListener(listeners.remove(utteranceId), utteranceId, method, errorCode);
        }
    }

    private void storeListener(@NonNull String utteranceId,
                               @NonNull SynthesizerListener[] listeners) {
        logger.v(TAG, "[%s] - added new utterance listener -> map size: %s", utteranceId, this.listeners.size());
        synchronized (lock) {
            LinkedHashMap<Integer, SynthesizerListener> utterances = new LinkedHashMap<>();
            for (SynthesizerListener item : listeners) {
                if (item instanceof SynthesizerListener.OnStart) {
                    utterances.put(ON_START, item);
                }
                if (item instanceof SynthesizerListener.OnDone) {
                    utterances.put(ON_DONE, item);
                }
                if (item instanceof SynthesizerListener.OnError) {
                    utterances.put(ON_ERROR, item);
                }
            }
            this.listeners.put(utteranceId, utterances);
        }
    }

    private String handleListener(String utteranceId, @Nullable SynthesizerListener[] listeners) {
        if (this.listeners.containsKey(utteranceId)) {
            utteranceId = utteranceId + "_" + this.listeners.size();
        }
        if (listeners != null && listeners.length > 0) storeListener(utteranceId, listeners);
        return utteranceId;
    }

    @Override
    public synchronized void playText(String text, String queueId, SynthesizerListener... listeners) {

        if (! requestAudioFocus()) return;

        final String utteranceId = DEFAULT_UTTERANCE_ID + System.nanoTime();

        final String uId = handleListener(utteranceId, listeners);

        log_MessageOnQueue(text, queueId, uId);

        addToQueue(uId, text, -1, queueId);

        log_Status(uId);

        if (!isSpeaking && !isLocked) {
            setSpeaking(true);
            runOnQueue();
        } else {
            moveToNextQueueIfNeeded();
        }
    }

    @Override
    public synchronized void playTextNow(String text, SynthesizerListener... listeners) {

        if (! requestAudioFocus()) return;

        final String utteranceId = DEFAULT_UTTERANCE_ID + System.nanoTime();

        final String uId = handleListener(utteranceId, listeners);

        log_MessageNow(text, uId);

        Map<String, Object> map = new HashMap<>();
        map.put(MAP_UTTERANCE_ID, uId);
        map.put(MAP_MESSAGE, text);

        log_StatusNow(uId);

        isOnQueue = false;

        play(map);
    }

    @Override
    public synchronized void playSilence(long durationInMillis, String queueId,
                            SynthesizerListener... listeners) {

        if (! requestAudioFocus()) return;

        checkSilenceDuration(durationInMillis);

        String utteranceId = DEFAULT_UTTERANCE_ID + System.nanoTime();

        final String uId = handleListener(utteranceId, listeners);

        log_Silence(uId);

        addToQueue(uId, null, durationInMillis, queueId);

        log_Status(uId);

        if (!isSpeaking && !isLocked) {
            setSpeaking(true);
            runOnQueue();
        } else {
            moveToNextQueueIfNeeded();
        }
    }

    @Override
    public synchronized void playSilenceNow(long durationInMillis, SynthesizerListener... listeners) {

        if (! requestAudioFocus()) return;

        checkSilenceDuration(durationInMillis);

        String utteranceId = DEFAULT_UTTERANCE_ID + System.nanoTime();

        final String uId = handleListener(utteranceId, listeners);

        log_SilenceNow(uId);

        Map<String, Object> map = new HashMap<>();
        map.put(MAP_UTTERANCE_ID, uId);
        map.put(MAP_SILENCE, durationInMillis);

        log_StatusNow(uId);

        isOnQueue = false;
        play(map);
    }

    private void checkSilenceDuration(long durationInMillis) {
        if (durationInMillis <= 0)
            throw new IllegalArgumentException("Silence duration must be greater than 0");
    }

    @Override
    public synchronized void resume() {
        if (isSpeaking || isLocked) return;
        String utteranceId = null;
        if (!isQueueEmpty()) {
            if (queue.containsKey(getCurrentQueueId())) {
                ConcurrentLinkedQueue<Map<String, Object>> maps = queue.get(getCurrentQueueId());
                if (maps != null && !maps.isEmpty()) {
                    Map map = maps.peek();
                    if (map != null && map.containsKey(MAP_UTTERANCE_ID))
                        utteranceId = (String) map.get(MAP_UTTERANCE_ID);
                }
            }
            setSpeaking(true);
        }
        final String uId = utteranceId;
        logger.i(TAG, "[%s] - resume <%s>", uId, queueId);
        runOnQueue();
    }

    private void runOnQueue() {
        moveToNextQueueIfNeeded();
        if (!isQueueEmpty()) {
            isOnQueue = true;
            // Gets and plays the current message in the queue
            play(Objects.requireNonNull(queue.get(queueId)).poll());
        } else shutdown();
    }

    private void play(Map<String, Object> map) {
        // Check whether Sco is connected or required
        logger.i(TAG, "Sco required: %s",
                 Boolean.toString(configuration.isBluetoothScoRequired()));

        if (bluetooth.isScoOn() || (bluetooth.isDeviceConnected() && configuration.isBluetoothScoRequired())) {
            // Start Bluetooth Sco
            logger.w(TAG, "waiting for Sco connection...");
            bluetooth.startSco(() -> chooseFromMapToPlay(map));
        } else {
            chooseFromMapToPlay(map);
        }
    }

    private boolean requestAudioFocus() {
        return getAudioManager().requestAudioFocus(focusChange -> {
            switch (focusChange) {
                case AudioManager.AUDIOFOCUS_GAIN:
                    logger.v(TAG, "AUDIOFOCUS_GAIN");
                    if (mediaPlayer != null)
                        mediaPlayer.setVolume(1.0f, 1.0f);
                    break;
                case AudioManager.AUDIOFOCUS_LOSS:
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    logger.w(TAG, "AUDIOFOCUS_LOSS " + focusChange);
                    //shutdown();
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    logger.i(TAG, "AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK");
                    if (mediaPlayer != null)
                        mediaPlayer.setVolume(0.6f, 0.6f);
                    break;
            }
        }, getConfiguration().isAudioExclusiveRequiredForSynthesizer());
    }

    void removeFromQueue(String utteranceId) {
        if (!isQueueEmpty()) {
            logger.v(TAG, "[%s] - remove <%s> from Queue", utteranceId, queueId);
            Objects.requireNonNull(queue.get(queueId)).remove();
        }
    }

    void moveToNextQueueIfNeeded() {
        if (isQueueEmpty()) {
            // is empty, still contains the queue id and it's not the default one
            String oldQueueId = queueId;
            if (queue.containsKey(queueId) && !DEFAULT_QUEUE_ID.equals(queueId)) {
                synchronized (lock) {
                    logger.v(TAG, "remove empty queue <%s>", queueId);
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
            logger.v(TAG, "queue from <%s> to <%s>", oldQueueId, queueId);
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
        Objects.requireNonNull(queue.get(queueId)).add(map);
        logger.v(TAG, "[%s] - added message to queue <%s>", utteranceId, queueId);
        logger.v(TAG, "[%s] - queues [%s] ", utteranceId, queue.size());
        logger.v(TAG, "[%s] - messages in queue <%s> = %s", utteranceId, queueId,
                Objects.requireNonNull(queue.get(queueId)).size());
    }

    private void chooseFromMapToPlay(Map<String, Object> map) {

        String utteranceId = (String) map.get(MAP_UTTERANCE_ID);

        if (getConfiguration().isForceLanguageDetection()) {
            logger.v(TAG, "[%s] - force language detection", utteranceId);
            forceDestroyTTS();
        }

        if (map.containsKey(MAP_MESSAGE)) {
            String text = (String) map.get(MAP_MESSAGE);
            if (Objects.equals(text, EMPTY)) {
                text = " ";
            } else {
                text = Objects.requireNonNull(HtmlUtils.from(text)).toString();
                for (Filter filter : getFilters()) {
                    logger.v(TAG, "[%s] apply filter: %s", utteranceId, filter);
                    text = filter.apply(text);
                }
                if (Objects.equals(text, EMPTY))
                    text = " ";
            }
            executeOnEngineReady(utteranceId, text);
        } else if (map.containsKey(MAP_SILENCE)) {
            playSilence(utteranceId, (long) map.get(MAP_SILENCE));
        } else
            throw new RuntimeException("No message or silence item to play");
    }

    void setSynthesizedFile(File file) {
        tempFile = file;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    File createTempFile(Context context, String name) {
        File file = null;
        try {
            file = File.createTempFile(
                    "speech_" + name, ".wav",
                    context.getCacheDir());
            file.deleteOnExit();
            file.setReadable(true);
        } catch (Exception ex) {
            logger.logException(ex);
        }
        return file;
    }

    private void finishPlayer() {
        logger.v(TAG, "stop and release MediaPlayer");
        try {
            if (mediaPlayer.isPlaying())
                mediaPlayer.stop();
            mediaPlayer.reset();
            mediaPlayer.release();
        } catch (Exception ignored) {
        }
        mediaPlayer = null;
    }

    void handleSynthesizedFile(Context context, SynthesizerUtteranceListener listener, String utterance) {
        finishPlayer();
        logger.v(TAG, "start MediaPlayer");
        if (tempFile != null) {
            try {
                mediaPlayer = MediaPlayer.create(context, Uri.fromFile(tempFile), null,
                        audioManager.getAudioAttributes().build(), AudioManager.AUDIO_SESSION_ID_GENERATE);

                mediaPlayer.setVolume(1.0f, 1.0f);

                // This is too risky - we keep it for the record
                //int currentVolume = audioManager.getDefaultAudioManager().getStreamVolume(audioManager.getMainStreamType());
                //int maxVolume = audioManager.getDefaultAudioManager().getStreamMaxVolume(audioManager.getMainStreamType());
                //float percent = configuration.getCustomVolume() / 100f;
                //int finalVolume = (int) (maxVolume * percent);
                //audioManager.getDefaultAudioManager().setStreamVolume(audioManager.getMainStreamType(), finalVolume, 0);

                if (mediaPlayer == null) {
                    prune();
                    listener.onError(utterance, SynthesizerListener.Status.UNKNOWN_ERROR);
                    return;
                }

                mediaPlayer.setOnCompletionListener(mediaPlayer -> {
                    logger.i(TAG, "MediaPlayer complete");
                    //audioManager.getDefaultAudioManager().setStreamVolume(audioManager.getMainStreamType(), currentVolume, 0);
                    finishPlayer();
                    tempFile = null;
                    listener.onDone(utterance);
                });
                mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                    logger.e(TAG, "MediaPlayer error");
                    //audioManager.getDefaultAudioManager().setStreamVolume(audioManager.getMainStreamType(), currentVolume, 0);
                    prune();
                    listener.onError(utterance, extra);
                    return true;
                });
                mediaPlayer.start();
            } catch (Exception ex) {
                logger.logException(ex);
                prune();
                listener.onError(utterance, SynthesizerListener.Status.UNKNOWN_ERROR);
            }
        } else {
            logger.w(TAG, "no media file to play, maybe silence?");
            listener.onDone(utterance);
        }
    }

    @Override
    public void addFilter(Filter filter) {
        filters.add(filter);
    }

    @Override
    public void clearFilters() {
        filters.clear();
    }

    @Override
    public List<Filter> getFilters() {
        return filters;
    }

    @Override
    public String getLastQueueId() {
        return lastQueueId;
    }

    @Nullable
    @Override
    public synchronized String getNextQueueId() {
        Set<String> keys = getQueueSet();
        if (keys.size() > 1) {
            return (String) Objects.requireNonNull(keys.toArray())[1];
        } else {
            return null;
        }
    }

    @Override
    public String getCurrentQueueId() {
        return queueId;
    }

    @Override
    public Set<String> getQueueSet() {
        return queue.keySet();
    }

    @Override
    public synchronized boolean isQueueEmpty() {
        boolean isEmpty = queue.isEmpty() || (queue.containsKey(queueId) && Objects.requireNonNull(queue.get(queueId)).isEmpty());
        if (isEmpty) logger.v(TAG, "queue is empty");
        return isEmpty;
    }

    @Override
    public synchronized boolean isOnQueue() {
        return isOnQueue;
    }

    protected boolean isReady() {
        return isReady;
    }

    void setReady(boolean ready) {
        logger.w(TAG, "ready set to " + ready);
        isReady = ready;
    }

    @Override
    public synchronized boolean isSpeaking() {
        return isSpeaking;
    }

    void setSpeaking(boolean speaking) {
        logger.w(TAG, "speaking set to " + speaking);
        isSpeaking = speaking;
    }

    public ComponentConfig getConfiguration() {
        return configuration;
    }

    public AndroidAudioManager getAudioManager() {
        return audioManager;
    }

    MediaPlayer getMediaPlayer() {
        return mediaPlayer;
    }

    @Override
    public synchronized void lock() {
        logger.w(TAG, "isLocked set to true");
        isLocked = true;
    }

    @Override
    public synchronized void unlock() {
        logger.w(TAG, "isLocked set to false");
        isLocked = false;
    }

    @Override
    public synchronized boolean isLocked() {
        return isLocked;
    }

    /**
     * Set speaking to false, free current message queue and finishes the MediaPlayer.
     */
    @CallSuper
    @Override
    public void stop() {
        setSpeaking(false);
        forceDestroyTTS();
        finishPlayer();
        getAudioManager().abandonAudioFocus(getConfiguration().isAudioExclusiveRequiredForSynthesizer());
    }

    /**
     * Clear listeners and messages queue, and restores queue Id to default value
     */
    @CallSuper
    protected void release() {
        synchronized (lock) {
            listeners.clear();
            queue.clear();
            queue.put(DEFAULT_QUEUE_ID, new ConcurrentLinkedQueue<>());
        }
        queueId     = DEFAULT_QUEUE_ID;
        isOnQueue   = false;
        lastQueueId = null;
        tempFile    = null;
        logger.v(TAG, "states and resources released");
    }


    /// LOGS

    private void log_MessageNow(String text, String utteranceId) {
        logger.i(TAG, "[%s] - play immediately \"%s\"", utteranceId, text);
    }

    private void log_MessageOnQueue(String text, String queueId, String utteranceId) {
        logger.i(TAG, "[%s] - play \"%s\" on queue <%s>", utteranceId, text, queueId);
    }

    private void log_Status(String uId) {
        logger.i(TAG, "[%s] - ready: %s | speaking: %s | held: %s",
                 uId, Boolean.toString(isReady), Boolean.toString(isSpeaking), Boolean.toString(isLocked));
    }

    private void log_StatusNow(String uId) {
        logger.i(TAG, "[%s] - ready: %s | speaking: %s",
                 uId, Boolean.toString(isReady), Boolean.toString(isSpeaking));
    }

    private void log_SilenceNow(String uId) {
        logger.i(TAG, "[%s] - play silence immediately", uId);
    }

    private void log_Silence(String uId) {
        logger.i(TAG, "[%s] - play silence on queue <%s>", uId, queueId);
    }
}
