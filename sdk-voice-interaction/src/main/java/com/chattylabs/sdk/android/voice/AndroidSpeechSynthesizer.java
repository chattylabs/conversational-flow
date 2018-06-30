package com.chattylabs.sdk.android.voice;

import android.annotation.TargetApi;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.telephony.TelephonyManager;

import com.chattylabs.sdk.android.common.HtmlUtils;
import com.chattylabs.sdk.android.common.StringUtils;
import com.chattylabs.sdk.android.common.Tag;
import com.chattylabs.sdk.android.common.internal.ILogger;
import com.chattylabs.sdk.android.voice.VoiceInteractionComponent.OnTextToSpeechDoneListener;
import com.chattylabs.sdk.android.voice.VoiceInteractionComponent.OnTextToSpeechErrorListener;
import com.chattylabs.sdk.android.voice.VoiceInteractionComponent.OnTextToSpeechInitialisedListener;
import com.chattylabs.sdk.android.voice.VoiceInteractionComponent.OnTextToSpeechStartedListener;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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

import static com.chattylabs.sdk.android.voice.VoiceInteractionComponent.DEFAULT_GROUP;
import static com.chattylabs.sdk.android.voice.VoiceInteractionComponent.MessageFilter;
import static com.chattylabs.sdk.android.voice.VoiceInteractionComponent.TEXT_TO_SPEECH_AVAILABLE;
import static com.chattylabs.sdk.android.voice.VoiceInteractionComponent.TEXT_TO_SPEECH_AVAILABLE_BUT_INACTIVE;
import static com.chattylabs.sdk.android.voice.VoiceInteractionComponent.TEXT_TO_SPEECH_LANGUAGE_NOT_SUPPORTED_ERROR;
import static com.chattylabs.sdk.android.voice.VoiceInteractionComponent.TEXT_TO_SPEECH_NOT_AVAILABLE_ERROR;
import static com.chattylabs.sdk.android.voice.VoiceInteractionComponent.TEXT_TO_SPEECH_UNKNOWN_ERROR;
import static com.chattylabs.sdk.android.voice.VoiceInteractionComponent.TextToSpeechListeners;
import static com.chattylabs.sdk.android.voice.VoiceInteractionComponent.getTextToSpeechErrorType;

final class AndroidSpeechSynthesizer {
    private static final String TAG = Tag.make("AndroidSpeechSynthesizer");

    private static final String CHECKING_UTTERANCE_ID = BuildConfig.APPLICATION_ID + ".checking";
    private static final String DEFAULT_UTTERANCE_ID = BuildConfig.APPLICATION_ID + ".utterance:";
    private static final int MAX_SPEECH_TIME = 60;
    private static final String TESTING_STRING = "<TESTING_STRING>";
    private static final String MAP_UTTERANCE_ID = "utteranceId";
    private static final String MAP_SILENCE = "silence";
    private static final String MAP_MESSAGE = "message";
    private static final String MAP_PARAMS = "params";
    // Data
    private final Map<String, UtteranceProgressListener> listenersMap;
    private final Map<String, ConcurrentLinkedQueue<Map<String, Object>>> queue;
    private final List<MessageFilter> filters;
    private final Object lock = new Object();
    // States
    private boolean isReady; // released
    private boolean isOnHold; // released
    private boolean isSpeaking; // released
    private boolean reviewAgain; // released
    private boolean triedToDownloadTtsData; // released
    private boolean bluetoothScoRequired; // released
    private boolean requestAudioFocusMayDuck; // released
    private boolean requestAudioFocusExclusive; // released
    private boolean isPhoneStateReceiverRegistered; // released
    private boolean isScoReceiverRegistered; // released
    private boolean speakerphoneOn; // released
    private boolean requestAudioExclusive; // released
    private boolean isBluetoothScoOn;
    private int audioMode = AudioManager.MODE_CURRENT; // released
    // Objects
    private String groupId = DEFAULT_GROUP; // released
    private String lastGroup;
    private AudioManager audioManager;
    private Peripheral peripheral;
    private AudioFocusRequest focusRequestMayDuck;
    private AudioFocusRequest focusRequestExclusive;
    private Application application;
    private TextToSpeech tts; // released
    private UtteranceAdapter utteranceListener; // released
    private PhoneStateReceiver phoneStateReceiver = new PhoneStateReceiver();
    private ScoReceiver scoReceiver = new ScoReceiver();
    // We put this mode because when over Sco we never gain focus!
    private AudioAttributes.Builder audioAttributes = new AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .setUsage(isBluetoothScoRequired() ? AudioAttributes.USAGE_VOICE_COMMUNICATION : AudioAttributes.USAGE_MEDIA)
            .setLegacyStreamType(getMainStreamType())
            ;
    private ILogger logger;

    AndroidSpeechSynthesizer(Application application, ILogger logger) {
        this.listenersMap = new LinkedHashMap<>();
        this.queue = new LinkedHashMap<>();
        this.filters = new LinkedList<>();
        this.logger = logger;
        this.release();
        this.application = application;
        this.audioManager = (AudioManager) application.getSystemService(Context.AUDIO_SERVICE);
        this.peripheral = new Peripheral(audioManager);
    }

    void speak(String text, String groupId, TextToSpeechListeners... listeners) {
        speak(text, groupId, generateUtteranceListener(listeners));
    }

    private void speak(String text, String groupId, UtteranceProgressListener listener) {
        speak(text, groupId, listener, DEFAULT_UTTERANCE_ID + System.nanoTime());
    }

    private void speak(String text, String groupId, @Nullable UtteranceProgressListener listener, String utteranceId) {
        logger.i(TAG, "TTS - prepare to speak \"" + text + "\" with Group: <" + groupId + ">");
        if (listenersMap.containsKey(utteranceId)) {
            utteranceId = utteranceId + "_" + listenersMap.size();
        }
        final String uId = utteranceId;
        HashMap<String, String> params = buildParams(uId, String.valueOf(getMainStreamType()));
        if (listener != null) handleListener(uId, listener);
        addToQueue(uId, text, -1, params, groupId);
        logger.i(TAG, "TTS - ready: " + Boolean.toString(isReady) +
                " | speaking: " + Boolean.toString(isSpeaking) +
                " | held: " + Boolean.toString(isOnHold));
        if (tts == null) {
            initTts(status -> {
                if (status == TextToSpeech.SUCCESS) {
                    isReady = true;
                    resume();
                }
                else {
                    logger.e(TAG, "TTS - with group status ERROR");
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

    void speakNow(String text, TextToSpeechListeners... listeners) {
        logger.i(TAG, "TTS - prepare to speak \"" + text + "\" with no Group");
        UtteranceAdapter listener = generateUtteranceListener(listeners);
        String utteranceId = DEFAULT_UTTERANCE_ID + System.nanoTime();
        if (listenersMap.containsKey(utteranceId)) {
            utteranceId = utteranceId + "_" + listenersMap.size();
        }
        HashMap<String, String> params = buildParams(utteranceId, String.valueOf(getMainStreamType()));
        handleListener(utteranceId, listener);
        Map<String, Object> map = new HashMap<>();
        map.put(MAP_UTTERANCE_ID, utteranceId);
        map.put(MAP_MESSAGE, text);
        map.put(MAP_PARAMS, params);
        logger.i(TAG, "TTS - ready: " + Boolean.toString(isReady) +
                " | speaking: " + Boolean.toString(isSpeaking));
        initTts(status -> {
            if (status == TextToSpeech.SUCCESS) {
                playTheQueue(map);
            }
            else {
                logger.e(TAG, "TTS - no group status ERROR");
                shutdown();
            }
        }, null);
    }

    void playSilence(long durationInMillis, String groupId, TextToSpeechListeners... listeners) {
        if (durationInMillis <= 0) throw new IllegalArgumentException("Silence duration must be greater than 0");
        logger.i(TAG, "TTS - play silence with Group: <" + groupId + ">");
        UtteranceAdapter listener = generateUtteranceListener(listeners);
        String utteranceId = DEFAULT_UTTERANCE_ID + System.nanoTime();
        if (listenersMap.containsKey(utteranceId)) {
            utteranceId = utteranceId + "_" + listenersMap.size();
        }
        final String uId = utteranceId;
        handleListener(uId, listener);
        // Silence doesn't need params
        addToQueue(uId, null, durationInMillis, null, groupId);
        logger.i(TAG, "TTS - ready: " + Boolean.toString(isReady) +
                " | speaking: " + Boolean.toString(isSpeaking) +
                " | held: " + Boolean.toString(isOnHold));
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

    void playSilenceNow(long durationInMillis, TextToSpeechListeners... listeners) {
        if (durationInMillis <= 0) throw new IllegalArgumentException("Silence duration must be greater than 0");
        logger.i(TAG, "TTS - play silence with no Group");
        UtteranceAdapter listener = generateUtteranceListener(listeners);
        String utteranceId = DEFAULT_UTTERANCE_ID + System.nanoTime();
        if (listenersMap.containsKey(utteranceId)) {
            utteranceId = utteranceId + "_" + listenersMap.size();
        }
        handleListener(utteranceId, listener);
        Map<String, Object> map = new HashMap<>();
        map.put(MAP_UTTERANCE_ID, utteranceId);
        map.put(MAP_SILENCE, durationInMillis);
        logger.i(TAG, "TTS - ready: " + Boolean.toString(isReady) +
                " | speaking: " + Boolean.toString(isSpeaking));
        initTts(status -> {
            if (status == TextToSpeech.SUCCESS) {
                playTheQueue(map);
            }
            else {
                logger.e(TAG, "TTS - silence NOW Status ERROR");
                shutdown();
            }
        }, null);
    }

    private UtteranceAdapter generateUtteranceListener(@NonNull TextToSpeechListeners... listeners) {
        UtteranceAdapter listener = new UtteranceAdapter()
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
            for (TextToSpeechListeners item : listeners) {
                if (item instanceof OnTextToSpeechStartedListener) {
                    listener.setOnStartedListener((OnTextToSpeechStartedListener) item);
                }
                if (item instanceof OnTextToSpeechDoneListener) {
                    listener.setOnDoneListener((OnTextToSpeechDoneListener) item);
                }
                if (item instanceof OnTextToSpeechErrorListener) {
                    listener.setOnErrorListener((OnTextToSpeechErrorListener) item);
                }
            }
        }
        return listener;
    }

    void dispose() {
        isOnHold = false;
    }

    void hold() {
        isOnHold = true;
    }

    void resume() {
        initTts(status -> {
            if (status == TextToSpeech.SUCCESS) {
                run();
            }
            else {
                logger.e(TAG, "TTS - status ERROR");
                shutdown();
            }
        }, null);
    }

    private void run() {
        logger.i(TAG, "TTS - resume group: <" + groupId + ">");
        checkForEmptyGroup();
        if (!isEmpty()) {
            isSpeaking = true;
            // Gets and plays the current message in the queue
            playTheQueue(queue.get(groupId).poll());
        }
    }

    private void checkForEmptyGroup() {
        if (isCurrentGroupEmpty()) {
            logger.v(TAG, "TTS - no more messages in the group <" + groupId + ">");
            moveToNextGroup();
        }
    }

    private void playTheQueue(Map<String, Object> map) {
        boolean isScoConnected = isBluetoothScoOn;
        // Sco Listener
        OnScoListener listener = new OnScoListener() {
            @Override
            public void onConnected() {
                if (audioManager.isBluetoothScoOn()) { logger.w(TAG, "TTS - Sco onConnected"); }
                if (requestAudioExclusive) { requestAudioFocusExclusive(); }
                else { requestAudioFocusMayDuck(); }
                if (map.containsKey(MAP_MESSAGE)) {
                    //noinspection unchecked
                    executeOnTtsReady((String) map.get(MAP_UTTERANCE_ID),
                                      (String) map.get(MAP_MESSAGE),
                                      (HashMap<String, String>) map.get(MAP_PARAMS));
                }
                else {
                    //noinspection unchecked
                    playSilence((String) map.get(MAP_UTTERANCE_ID), (long) map.get(MAP_SILENCE));
                }
            }

            @Override
            public void onDisconnected() {
                logger.w(TAG, "TTS - Sco onDisconnected");
                if (isScoConnected) {
                    logger.w(TAG, "TTS - shutting down from Sco");
                    shutdown();
                }
            }
        };
        // Register for incoming calls
        registerPhoneStateReceiver();
        // Check whether Sco is connected or required
        logger.i(TAG, "TTS - is bluetooth Sco required: " + Boolean.toString(isBluetoothScoRequired()));
        if (!isBluetoothScoRequired() || isScoConnected) {
            logger.v(TAG, "TTS - bluetooth sco is: " + (isScoConnected ? "on" : "off"));
            listener.onConnected();
        }
        else {
            // Sco receivers
            registerScoReceivers(listener);
            // Start Bluetooth Sco
            startSco();
            logger.v(TAG, "TTS - waiting for bluetooth sco connection");
        }
    }

    void shutdown() {
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

    void stop() {
        logger.w(TAG, "TTS - Stopping..");
        if (utteranceListener != null)
            utteranceListener.clearTimeout();
        // Unregister all broadcast receivers
        unregisterReceivers();
        // Stop Bluetooth Sco if required
        stopSco();
        // Audio focus
        abandonAudioFocusMayDuck();
        abandonAudioFocusExclusive();
        // Shutdown text to speech
        if (tts != null) {
            try {
                tts.stop();
                logger.v(TAG, "TTS - TextToSpeech stopped");
            } catch (Exception ignored) {}
        }
        logger.w(TAG, "TTS - Speaking false");
        isSpeaking = false;
    }

    TextToSpeech.EngineInfo getEngineByName(TextToSpeech tts, String name) {
        List<TextToSpeech.EngineInfo> engines = tts.getEngines();
        for (TextToSpeech.EngineInfo engineInfo : engines) {
            if (engineInfo.name.contains(name)) {
                return engineInfo;
            }
        }
        return null;
    }

    @SuppressWarnings({"unchecked", "TryWithIdenticalCatches"})
    String getCurrentEngine(TextToSpeech tts) {
        Class c;
        String result = null;
        try {
            c = Class.forName("android.speech.tts.TextToSpeech");
            Method m = c.getMethod("getCurrentEngine"); // TODO: this need a proguard to skip obfuscating reflection method
            result = (String) m.invoke(tts);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
        return result;
    }

    private void release() {
        synchronized (lock) {
            listenersMap.clear();
            queue.clear();
            queue.put(DEFAULT_GROUP, new ConcurrentLinkedQueue<>());
        }
        filters.clear();
        groupId = DEFAULT_GROUP;
        reviewAgain = true;
        bluetoothScoRequired = false;
        triedToDownloadTtsData = false;
        isReady = false;
        isOnHold = false;
        isSpeaking = false;
        requestAudioExclusive = false;
        logger.v(TAG, "TTS - states and resources released");
    }

    private boolean isBluetoothScoRequired() {
        return bluetoothScoRequired && !peripheral.get(Peripheral.Type.HEADSET).isConnected();
    }

    void setBluetoothScoRequired(boolean bluetoothScoRequired) {
        this.bluetoothScoRequired = bluetoothScoRequired;
    }

    void setRequestAudioExclusive(boolean exclusive) {
        this.requestAudioExclusive = exclusive;
    }

    String getGroupId() {
        return groupId;
    }

    String getLastGroup() {
        return lastGroup;
    }

    @Nullable
    String getNextGroup() {
        Set<String> keys = getGroupQueue();
        if (keys.size() > 1) {
            return (String) keys.toArray()[1];
        }
        else {
            return null;
        }
    }

    boolean isCurrentGroupEmpty() {
        return !queue.containsKey(groupId) || queue.get(groupId).isEmpty();
    }

    boolean isEmpty() {
        return queue.isEmpty() || (queue.containsKey(DEFAULT_GROUP) && queue.size() == 1 && queue.get(DEFAULT_GROUP).isEmpty());
    }

    Set<String> getGroupQueue() {
        return queue.keySet();
    }

    private void moveToNextGroup() {
        // is empty, still contains the group id and it's not the default one
        if (queue.containsKey(groupId) && !DEFAULT_GROUP.equals(groupId)) {
            logger.v(TAG, "TTS - remove empty group: <" + groupId + ">");
            synchronized (lock) {
                queue.remove(groupId);
            }
        }
        boolean isLastGroupEquals = Objects.equals(lastGroup, groupId);
        groupId = getNextGroup();
        if (groupId == null) {
            groupId = DEFAULT_GROUP;
            if (isLastGroupEquals) {
                logger.v(TAG, "TTS - update last group from <" + lastGroup + "> to <" + groupId + ">");
                lastGroup = groupId;
            }
        }
        logger.v(TAG, "TTS - New group: <" + groupId + ">");
    }

    void addFilter(MessageFilter filter) {
        filters.add(filter);
    }

    private void registerPhoneStateReceiver() {
        if (!isPhoneStateReceiverRegistered) {
            logger.v(TAG, "TTS - register for phone receiver");
            IntentFilter phoneFilter = new IntentFilter();
            phoneFilter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
            phoneFilter.addAction(Intent.ACTION_NEW_OUTGOING_CALL);
            phoneStateReceiver.setListener(new OnPhoneListener() {
                @Override
                public void onOutgoingCallStarts() {
                    shutdown();
                }

                @Override
                public void onIncomingCallRinging() {
                    shutdown();
                }

                @Override
                public void onOutgoingCallEnds() {}

                @Override
                public void onIncomingCallEnds() {}
            });
            application.registerReceiver(phoneStateReceiver, phoneFilter);
            isPhoneStateReceiverRegistered = true;
        }
    }

    private void registerScoReceivers(OnScoListener onScoListener) {
        scoReceiver.setListener(onScoListener);
        if (!isScoReceiverRegistered) {
            logger.v(TAG, "TTS - register sco receiver");
            IntentFilter scoFilter = new IntentFilter();
            scoFilter.addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED);
            application.registerReceiver(scoReceiver, scoFilter);
            isScoReceiverRegistered = true;
        }
    }

    private void unregisterReceivers() {
        // Phone State
        if (isPhoneStateReceiverRegistered) {
            application.unregisterReceiver(phoneStateReceiver);
            isPhoneStateReceiverRegistered = false;
        }
        // Bluetooth Sco
        if (isScoReceiverRegistered) {
            application.unregisterReceiver(scoReceiver);
            isScoReceiverRegistered = false;
        }
    }

    private void startSco() {
        if (audioManager.isBluetoothScoAvailableOffCall() && !isBluetoothScoOn) {
            audioManager.setBluetoothScoOn(true);
            audioManager.startBluetoothSco();
            isBluetoothScoOn = true;
            logger.v(TAG, "TTS - start bluetooth sco");
        }
    }

    private void stopSco() {
        if (audioManager.isBluetoothScoAvailableOffCall() && isBluetoothScoOn) {
            audioManager.setBluetoothScoOn(false);
            audioManager.stopBluetoothSco();
            isBluetoothScoOn = false;
            logger.v(TAG, "TTS - stop bluetooth sco");
        }
    }

    private void requestAudioFocusMayDuck() {
        if (!requestAudioFocusMayDuck) {
            logger.v(TAG, "TTS - request Audio Focus May Duck");
            setAudioMode();
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N_MR1) {
                requestAudioFocusMayDuck = AudioManager.AUDIOFOCUS_REQUEST_GRANTED == audioManager.requestAudioFocus(
                        null,
                        getMainStreamType(),
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
            }
            else {
                focusRequestMayDuck = new AudioFocusRequest
                        .Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                        .setAudioAttributes(audioAttributes.build()).build();
                requestAudioFocusMayDuck = AudioManager.AUDIOFOCUS_REQUEST_GRANTED == audioManager.requestAudioFocus(focusRequestMayDuck);
            }
        }
    }

    private void abandonAudioFocusMayDuck() {
        if (requestAudioFocusMayDuck) {
            logger.v(TAG, "TTS - abandon Audio Focus May Duck");
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N_MR1) {
                audioManager.abandonAudioFocus(null);
            }
            else {
                audioManager.abandonAudioFocusRequest(focusRequestMayDuck);
            }
            unsetAudioMode();
            requestAudioFocusMayDuck = false;
        }
    }

    private void requestAudioFocusExclusive() {
        if (!requestAudioFocusExclusive) {
            logger.v(TAG, "TTS - request Audio Focus Exclusive");
            setAudioMode();
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N_MR1) {
                requestAudioFocusExclusive = AudioManager.AUDIOFOCUS_REQUEST_GRANTED == audioManager.requestAudioFocus(
                        null,
                        getMainStreamType(),
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE);

            }
            else {
                focusRequestExclusive = new AudioFocusRequest
                        .Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                        .setAudioAttributes(audioAttributes.build()).build();
                requestAudioFocusExclusive = AudioManager.AUDIOFOCUS_REQUEST_GRANTED == audioManager.requestAudioFocus(focusRequestExclusive);
            }
        }
    }

    private void abandonAudioFocusExclusive() {
        if (requestAudioFocusExclusive) {
            logger.v(TAG, "TTS - abandon Audio Focus Exclusive");
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N_MR1) {
                //noinspection deprecation
                audioManager.abandonAudioFocus(null);
            }
            else {
                audioManager.abandonAudioFocusRequest(focusRequestExclusive);
            }
            unsetAudioMode();
            requestAudioFocusExclusive = false;
        }
    }

    private void setAudioMode() {
        audioMode = audioManager.getMode();
        audioManager.setMode(isBluetoothScoRequired() ? AudioManager.MODE_IN_CALL : AudioManager.MODE_NORMAL);

        // Enabling this option, the audio is not rooted to the speakers if the sco is activated
        // Meaning that we can force bluetooth sco even with speakers connected
        // Nice to have feature!
        //speakerphoneOn = audioManager.isSpeakerphoneOn();
        //boolean isHeadsetConnected = peripheral.get(Peripheral.Type.HEADSET).isConnected();
        //if (!isHeadsetConnected) { audioManager.setSpeakerphoneOn(!isBluetoothScoRequired()); }
        //else { audioManager.setSpeakerphoneOn(true); }
    }

    private void unsetAudioMode() {
        audioManager.setMode(audioMode);

        // Enabling this option, the audio is not rooted to the speakers if the sco is activated
        // Meaning that we can force bluetooth sco even with speakers connected
        // Nice to have feature!
        audioManager.setSpeakerphoneOn(speakerphoneOn);
    }

    private void handleListener(@NonNull String utteranceId, @NonNull UtteranceProgressListener listener) {
        logger.v(TAG, "TTS - added utterance listener -> size:  " + listenersMap.size());
        synchronized (lock) {
            listenersMap.put(utteranceId, listener);
        }
    }

    private void addToQueue(@NonNull String utteranceId, String message, long duration, @Nullable HashMap<String, String> params, @NonNull String groupId) {
        Map<String, Object> map = new HashMap<>();
        map.put(MAP_UTTERANCE_ID, utteranceId);
        if (message != null) map.put(MAP_MESSAGE, message);
        if (duration > 0) map.put(MAP_SILENCE, duration);
        if (params != null) map.put(MAP_PARAMS, params);
        if (!queue.containsKey(groupId)) {
            logger.v(TAG, "TTS - added group: <" + groupId + ">");
            lastGroup = groupId;
            synchronized (lock) {
                queue.put(groupId, new ConcurrentLinkedQueue<>());
            }
        }
        queue.get(groupId).add(map);
        logger.v(TAG, "TTS - added message to queue <" + groupId + ">. Number of groups: " + queue.size());
        logger.v(TAG, "TTS - messages in the queue <" + groupId + ">: " + queue.get(groupId).size());
    }

    private HashMap<String, String> buildParams(@NonNull String utteranceId, @NonNull String audioStream) {
        HashMap<String, String> params = new LinkedHashMap<>();
        params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);
        params.put(TextToSpeech.Engine.KEY_PARAM_VOLUME, "1.1");
        params.put(TextToSpeech.Engine.KEY_PARAM_STREAM, audioStream);
        params.put(TextToSpeech.Engine.KEY_FEATURE_NETWORK_TIMEOUT_MS, "5000");
        params.put(TextToSpeech.Engine.KEY_FEATURE_NETWORK_RETRIES_COUNT, "2");
        logger.v(TAG, "TTS - building params");
        return params;
    }

    private void initTts(TextToSpeech.OnInitListener onInitListener,
                         OnTextToSpeechInitialisedListener onCheckLanguageInit) {
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

    private UtteranceAdapter initUtterancesListener(OnTextToSpeechInitialisedListener onCheckLanguageInit) {
        return new UtteranceAdapter() {

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
                logger.i(TAG, "TTS - started timeout!");
                timer = new Timer();
                task = new TimerTask() {
                    @Override
                    public void run() {
                        if (tts == null || !tts.isSpeaking()) {
                            logger.e(TAG, "TTS - is null or not speaking && reached timeout!");
                            stop();
                            onDone(utteranceId);
                        }
                        else {
                            if ((System.currentTimeMillis() - timestamp) > TimeUnit.SECONDS.toMillis(MAX_SPEECH_TIME)) {
                                logger.e(TAG, "TTS - exceeded " + MAX_SPEECH_TIME + " sec!");
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
                logger.v(TAG, "TTS - on start -> utterance listener size: " + listenersMap.size());

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
                    logger.v(TAG, "TTS - on done <" + groupId + "> -> go to setup language");
                    checkLanguage(onCheckLanguageInit, true);
                }
                else {
                    logger.v(TAG, "TTS - check For Empty Group <" + groupId + ">");
                    checkForEmptyGroup();
                    if (isCurrentGroupEmpty()) {
                        stop();
                        logger.i(TAG, "TTS - Stream Finished.");
                    }
                    if (listenersMap.size() > 0) {
                        UtteranceProgressListener listener;
                        synchronized (lock) {
                            listener = listenersMap.remove(utteranceId);
                        }
                        logger.v(TAG, "TTS - Execute listener onDone <" + groupId + ">");
                        listener.onDone(utteranceId);
                    }
                }
            }

            @Override
            @TargetApi(Build.VERSION_CODES.LOLLIPOP)
            public void onError(String utteranceId, int errorCode) {
                clearTimeout();
                logger.e(TAG, "TTS - on error -> stop timeout -> utterance listener size: " + listenersMap.size());
                logger.e(TAG, "TTS - error code: " + getTextToSpeechErrorType(errorCode));
                if (utteranceId.equals(CHECKING_UTTERANCE_ID)) {
                    shutdown();
                    if (errorCode == TextToSpeech.ERROR_NOT_INSTALLED_YET) {
                        onCheckLanguageInit.execute(TEXT_TO_SPEECH_NOT_AVAILABLE_ERROR);
                    }
                    else {
                        onCheckLanguageInit.execute(TEXT_TO_SPEECH_UNKNOWN_ERROR);
                    }
                }
                else {
                    checkForEmptyGroup();
                    if (isCurrentGroupEmpty()) {
                        stop();
                        logger.i(TAG, "TTS - ERROR - Stream Finished.");
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

    private void checkLanguage(OnTextToSpeechInitialisedListener onInit, boolean fromUtterance) {
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
                onInit.execute(TEXT_TO_SPEECH_LANGUAGE_NOT_SUPPORTED_ERROR);
            }
        }
        else {
            // Everything has gone well!
            logger.i(TAG, "TTS - checking has passed!");
            shutdown();
            onInit.execute(TEXT_TO_SPEECH_AVAILABLE);
        }
    }

    private void tryToDownloadTtsData(OnTextToSpeechInitialisedListener onInit) {
        if (!triedToDownloadTtsData) {
            triedToDownloadTtsData = true;
            logger.v(TAG, "TTS - try to download audio data");
            try {
                // Try downloading data voice!
                speak(TESTING_STRING, "DOWNLOADING_TTS_DATA", null, CHECKING_UTTERANCE_ID);
            } catch (Exception e) {
                logger.e(TAG, "error when downloading audio data: " + e.getMessage());
                // Otherwise it reports the TextToSpeechStatus to the Callback
                checkLanguage(onInit, false);
            }
        }
        else {
            logger.e(TAG, "TTS - try to download audio data - ERROR");
            shutdown();
            onInit.execute(TEXT_TO_SPEECH_UNKNOWN_ERROR);
        }
    }

    private TextToSpeech createTextToSpeech(Application application, TextToSpeech.OnInitListener listener) {
        return new TextToSpeech(application, listener);
    }

    void setup(Application application, OnTextToSpeechInitialisedListener onInit) {
        logger.i(TAG, "TTS - setup and check language");
        this.application = application;
        try {
            initTts(status -> {
                if (status == TextToSpeech.SUCCESS) {
                    tryToDownloadTtsData(onInit);
                }
                else {
                    if (tts == null) {
                        release();
                        TextToSpeech _tts = new TextToSpeech(application, null);
                        if (_tts.getEngines().size() > 0) {
                            onInit.execute(TEXT_TO_SPEECH_AVAILABLE_BUT_INACTIVE);
                        }
                        else {
                            onInit.execute(TEXT_TO_SPEECH_NOT_AVAILABLE_ERROR);
                        }
                        _tts.shutdown();
                    }
                    else {
                        shutdown();
                        onInit.execute(TEXT_TO_SPEECH_AVAILABLE_BUT_INACTIVE);
                    }
                }
            }, onInit);
        } catch (Exception e) {
            shutdown();
            onInit.execute(TEXT_TO_SPEECH_NOT_AVAILABLE_ERROR);
        }
    }

    private int getMainStreamType() {
        return isBluetoothScoRequired() ? AudioManager.STREAM_VOICE_CALL : AudioManager.STREAM_MUSIC;
    }

    private void executeOnTtsReady(String utteranceId, String text, HashMap<String, String> params) {
        //noinspection ConstantConditions
        String finalText = HtmlUtils.from(text).toString();

        for (MessageFilter filter : filters) {
            logger.v(TAG, "TTS - apply filter: " + filter);
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
        logger.i(TAG, "TTS - play silence");
        initTts(status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.playSilentUtterance(durationInMillis, TextToSpeech.QUEUE_ADD, utteranceId);
            }
            else {
                logger.e(TAG, "TTS - silence status ERROR");
                shutdown();
            }
        }, null);
    }

    private void play(String utteranceId, String text, HashMap<String, String> params) {
        logger.i(TAG, "TTS - reading out loud: \"" + text + "\"");
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
                logger.e(TAG, "TTS - play status ERROR");
                shutdown();
            }
        }, null);
    }
}
