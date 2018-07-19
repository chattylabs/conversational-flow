package com.chattylabs.sdk.android.voice;

import android.annotation.TargetApi;
import android.os.Build;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.chattylabs.sdk.android.common.internal.ILogger;

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

import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.*;
import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.DEFAULT_QUEUE_ID;
import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.SYNTHESIZER_AVAILABLE;
import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.SYNTHESIZER_LANGUAGE_NOT_SUPPORTED_ERROR;
import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.SYNTHESIZER_NOT_AVAILABLE_ERROR;
import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.SYNTHESIZER_UNKNOWN_ERROR;

abstract class BaseSpeechSynthesizer implements SpeechSynthesizer {

    // Constants
    static final String CHECKING_UTTERANCE_ID = BuildConfig.APPLICATION_ID + ".checking";
    static final String DEFAULT_UTTERANCE_ID = BuildConfig.APPLICATION_ID + ".utterance:";
    private static final String TESTING_STRING = "<TESTING_STRING>";
    private static final String MAP_UTTERANCE_ID = "utteranceId";
    private static final String MAP_SILENCE = "silence";
    private static final String MAP_MESSAGE = "message";
    private static final String MAP_PARAMS = "params";
    private static final int MAX_SPEECH_TIME = 60;

    // Data
    private final VoiceConfig configuration;
    private final Map<String, UtteranceListener> listenersMap;
    private final Map<String, ConcurrentLinkedQueue<Map<String, Object>>> queue;
    private final List<TextFilter> filters;
    private final Object lock = new Object();

    // States
    private boolean isReady; // released
    private boolean isOnHold; // released
    private boolean isSpeaking; // released
    private boolean reviewAgain; // released

    // Resources
    private String queueId = DEFAULT_QUEUE_ID; // released
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

    protected abstract void initTts(
            TextToSpeech.OnInitListener onInitListener, 
            OnSynthesizerInitialised onCheckLanguageInit);

    protected abstract void executeOnTtsReady(
            String utteranceId, String text, HashMap<String, String> params);

    protected abstract void playSilence(String utteranceId, long durationInMillis);
    
    protected abstract String getTag();

    private void playText(String text, String queueId, @Nullable UtteranceListener listener, String utteranceId) {
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

    private void checkForEmptyCurrentQueue() {
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

    @Override
    public void addFilter(TextFilter filter) {
        filters.add(filter);
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

    private AndroidSpeechSynthesizerAdapter initUtterancesListener(OnSynthesizerInitialised onCheckLanguageInit) {
        return new AndroidSpeechSynthesizerAdapter() {

            private long timestamp;
            private TimerTask task;
            private Timer timer;

            @Override
            protected void clearTimeout() {
                logger.i(getTag(), "TTS - utterance timeout cleared!");
                if (task != null) task.cancel();
                if (timer != null) timer.cancel();
            }

            @Override
            protected void startTimeout(String utteranceId) {
                logger.i(getTag(), "TTS - started timeout! - " + utteranceId);
                timer = new Timer();
                task = new TimerTask() {
                    @Override
                    public void run() {
                        if (tts == null || !tts.isSpeaking()) {
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
                logger.v(getTag(), "TTS - on start -> utterance - " + utteranceId + " - listener size: " + listenersMap.size());

                startTimeout(utteranceId);
                timestamp = System.currentTimeMillis();

                if (listenersMap.size() > 0) {
                    UtteranceListener listener = listenersMap.get(utteranceId);
                    if (listener != null) {
                        listener.onStart(utteranceId);
                    }
                }
            }

            @Override
            public void onDone(String utteranceId) {
                clearTimeout();
                if (utteranceId.equals(CHECKING_UTTERANCE_ID)) {
                    logger.v(getTag(), "TTS - on done <" + queueId + "> -> go to setup language - " + utteranceId);
                    checkLanguage(onCheckLanguageInit, true);
                }
                else {
                    logger.v(getTag(), "TTS - check For Empty Queue <" + queueId + "> - " + utteranceId);
                    isSpeaking = false;
                    checkForEmptyCurrentQueue();
                    if (isCurrentQueueEmpty()) {
                        stop();
                        logger.i(getTag(), "TTS - Stream Finished. - " + utteranceId);
                    }
                    if (listenersMap.size() > 0) {
                        UtteranceProgressListener listener;
                        synchronized (lock) {
                            listener = listenersMap.remove(utteranceId);
                        }
                        logger.v(getTag(), "TTS - Execute listener onDone <" + queueId + "> - " + utteranceId);
                        listener.onDone(utteranceId);
                    }
                }
            }

            @Override
            @TargetApi(Build.VERSION_CODES.LOLLIPOP)
            public void onError(String utteranceId, int errorCode) {
                clearTimeout();
                logger.e(getTag(), "TTS - on error -> stop timeout -> utterance - " + utteranceId + " - listener size: " + listenersMap.size());
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
                    isSpeaking = false;
                    checkForEmptyCurrentQueue();
                    if (isCurrentQueueEmpty()) {
                        stop();
                        logger.i(getTag(), "TTS - ERROR - Stream Finished. - " + utteranceId);
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
}
