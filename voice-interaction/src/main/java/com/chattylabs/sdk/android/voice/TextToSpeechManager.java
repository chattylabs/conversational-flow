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
import android.util.Log;

import com.chattylabs.sdk.android.common.HtmlUtils;
import com.chattylabs.sdk.android.common.StringUtils;
import com.chattylabs.sdk.android.common.Tag;
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

final class TextToSpeechManager {
    private static final String TAG = Tag.make(TextToSpeechManager.class);

    private static final String CHECKING_UTTERANCE_ID = BuildConfig.APPLICATION_ID + ".checking";
    private static final String DEFAULT_UTTERANCE_ID = BuildConfig.APPLICATION_ID + ".utterance:";
    private static final int MAX_SPEECH_TIME = 30;
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

    TextToSpeechManager(Application application) {
        this.listenersMap = new LinkedHashMap<>();
        this.queue = new LinkedHashMap<>();
        this.filters = new LinkedList<>();
        this.release();
        this.application = application;
        this.audioManager = (AudioManager) application.getSystemService(Context.AUDIO_SERVICE);
        this.peripheral = new Peripheral(audioManager);
    }

    private TextToSpeech createTextToSpeech(Application application, TextToSpeech.OnInitListener listener) {
        return new TextToSpeech(application, listener);
    }

    synchronized void setup(Application application, OnTextToSpeechInitialisedListener onInit) {
        Log.i(TAG, "TTS - setup and checking");
        this.application = application;
        try {
            initTts(status -> {
                Log.i(TAG, "TTS - instance initialized");
                if (status == TextToSpeech.SUCCESS) {
                    isReady = true;
                    // FIXME IllegalArgumentException: Invalid int: "OS" - Samsung Android 6
                    try {
                        tts.setLanguage(Locale.getDefault());
                        // try to select network synthesis
                        //for (Voice voice : tts.getVoices()) {
                        //    if (voice.isNetworkConnectionRequired()) {
                        //        tts.setVoice(voice);
                        //    }
                        //}
                    } catch (Exception ignored) {
                    }
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

    synchronized void speak(String text, String groupId, TextToSpeechListeners... listeners) {
        speak(text, groupId, generateUtteranceListener(listeners));
    }

    private void speak(String text, String groupId, UtteranceProgressListener listener) {
        speak(text, groupId, listener, DEFAULT_UTTERANCE_ID + System.nanoTime());
    }

    private void speak(String text, String groupId, @Nullable UtteranceProgressListener listener, String utteranceId) {
        Log.i(TAG, "TTS - prepare to speak \"" + text + "\" with Group: <" + groupId + ">");
        synchronized (lock) {
            if (listenersMap.containsKey(utteranceId)) {
                utteranceId = utteranceId + "_" + listenersMap.size();
            }
        }
        final String uId = utteranceId;
        HashMap<String, String> params = buildParams(uId, String.valueOf(getMainStreamType()));
        if (listener != null) handleListener(uId, listener);
        addToQueue(uId, text, -1, params, groupId);
        Log.i(TAG, "TTS - ready: " + b(isReady) + " | speaking: " + b(isSpeaking));
        if (tts == null) {
            initTts(status -> {
                Log.i(TAG, "TTS - instance initialized");
                if (status == TextToSpeech.SUCCESS) {
                    isReady = true;
                    resume();
                }
                else {
                    Log.e(TAG, "TTS - Status ERROR");
                    synchronized (lock) {
                        if (listenersMap.containsKey(uId)) {
                            listenersMap.remove(uId).onError(uId, TextToSpeech.ERROR);
                        }
                    }
                    //shutdown();
                }
            }, null);
        }
        else if (isReady && !isSpeaking) {
            resume();
        }
    }

    synchronized void speakNow(String text, TextToSpeechListeners... listeners) {
        Log.i(TAG, "TTS - prepare to speak \"" + text + "\" with no Group");
        UtteranceAdapter listener = generateUtteranceListener(listeners);
        String utteranceId = DEFAULT_UTTERANCE_ID + System.nanoTime();
        synchronized (lock) {
            if (listenersMap.containsKey(utteranceId)) {
                utteranceId = utteranceId + "_" + listenersMap.size();
            }
        }
        HashMap<String, String> params = buildParams(utteranceId, String.valueOf(getMainStreamType()));
        handleListener(utteranceId, listener);
        Map<String, Object> map = new HashMap<>();
        map.put(MAP_UTTERANCE_ID, utteranceId);
        map.put(MAP_MESSAGE, text);
        map.put(MAP_PARAMS, params);
        Log.i(TAG, "TTS - ready: " + b(isReady) + " | speaking: " + b(isSpeaking));
        if (tts == null) {
            initTts(status -> {
                Log.i(TAG, "TTS - instance initialized");
                if (status == TextToSpeech.SUCCESS) {
                    isReady = true;
                    playTheQueue(map);
                }
                else {
                    Log.e(TAG, "TTS - no group Status ERROR");
                    shutdown();
                }
            }, null);
        }
        else if (isReady) {
            playTheQueue(map);
        }
    }

    synchronized void playSilence(long durationInMillis, String groupId, TextToSpeechListeners... listeners) {
        if (durationInMillis <= 0) throw new IllegalArgumentException("Silence duration must be greater than 0");
        Log.i(TAG, "TTS - play silence with Group: <" + groupId + ">");
        UtteranceAdapter listener = generateUtteranceListener(listeners);
        String utteranceId = DEFAULT_UTTERANCE_ID + System.nanoTime();
        synchronized (lock) {
            if (listenersMap.containsKey(utteranceId)) {
                utteranceId = utteranceId + "_" + listenersMap.size();
            }
        }
        final String uId = utteranceId;
        handleListener(uId, listener);
        // Silence doesn't need params
        addToQueue(uId, null, durationInMillis, null, groupId);
        Log.i(TAG, "TTS - ready: " + b(isReady) + " | speaking: " + b(isSpeaking));
        if (tts == null) {
            initTts(status -> {
                Log.i(TAG, "TTS - instance initialized");
                if (status == TextToSpeech.SUCCESS) {
                    isReady = true;
                    resume();
                }
                else {
                    Log.e(TAG, "TTS - Status ERROR");
                    synchronized (lock) {
                        if (listenersMap.containsKey(uId)) {
                            listenersMap.remove(uId).onError(uId, TextToSpeech.ERROR);
                        }
                    }
                    //shutdown();
                }
            }, null);
        }
        else if (isReady && !isSpeaking) {
            resume();
        }
    }

    synchronized void playSilenceNow(long durationInMillis, TextToSpeechListeners... listeners) {
        if (durationInMillis <= 0) throw new IllegalArgumentException("Silence duration must be greater than 0");
        Log.i(TAG, "TTS - play silence with no Group");
        UtteranceAdapter listener = generateUtteranceListener(listeners);
        String utteranceId = DEFAULT_UTTERANCE_ID + System.nanoTime();
        synchronized (lock) {
            if (listenersMap.containsKey(utteranceId)) {
                utteranceId = utteranceId + "_" + listenersMap.size();
            }
        }
        handleListener(utteranceId, listener);
        Map<String, Object> map = new HashMap<>();
        map.put(MAP_UTTERANCE_ID, utteranceId);
        map.put(MAP_SILENCE, durationInMillis);
        Log.i(TAG, "TTS - ready: " + b(isReady) + " | speaking: " + b(isSpeaking));
        if (tts == null) {
            initTts(status -> {
                Log.i(TAG, "TTS - instance initialized");
                if (status == TextToSpeech.SUCCESS) {
                    isReady = true;
                    playTheQueue(map);
                }
                else {
                    Log.e(TAG, "TTS - silence Status ERROR");
                    shutdown();
                }
            }, null);
        }
        else if (isReady) {
            playTheQueue(map);
        }
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

    private String b(boolean b) {
        return Boolean.valueOf(b).toString();
    }

    synchronized void resume() {
        if (tts == null) {
            initTts(status -> {
                Log.i(TAG, "TTS - instance initialized");
                if (status == TextToSpeech.SUCCESS) {
                    isReady = true;
                    run();
                }
                else {
                    shutdown();
                }
            }, null);
        }
        else if (isReady) {
            run();
        }
    }

    private void run() {
        Log.i(TAG, "TTS - resume group: <" + groupId + ">");
        checkForEmptyGroup();
        if (!isGroupQueueEmpty()) {
            isSpeaking = true;
            // Gets and plays the current message in the queue
            synchronized (lock) {
                playTheQueue(queue.get(groupId).poll());
            }
        }
        else {
            forceStop();
            Log.i(TAG, "TTS - Stream Finished - no more pending messages.");
        }
    }

    private void checkForEmptyGroup() {
        if (isGroupQueueEmpty()) {
            Log.v(TAG, "TTS - no more messages in the queue of the group <" + groupId + ">");
            moveToNextGroup();
        }
    }

    private void playTheQueue(Map<String, Object> map) {
        boolean isScoConnected = isBluetoothScoOn;
        // Sco Listener
        OnScoListener listener = new OnScoListener() {
            @Override
            public void onConnected() {
                if (audioManager.isBluetoothScoOn()) { Log.w(TAG, "TTS - Sco onConnected"); }
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
                Log.w(TAG, "TTS - Sco onDisconnected");
                if (isScoConnected) {
                    Log.w(TAG, "TTS - shutting down from Sco");
                    shutdown();
                }
            }
        };
        // Register for incoming calls
        registerPhoneStateReceiver();
        // Check whether Sco is connected or required
        Log.i(TAG, "TTS - is bluetooth Sco required: " + b(isBluetoothScoRequired()));
        if (!isBluetoothScoRequired() || isScoConnected) {
            Log.v(TAG, "TTS - bluetooth sco is: " + (isScoConnected ? "on" : "off"));
            listener.onConnected();
        }
        else {
            // Sco receivers
            registerScoReceivers(listener);
            // Start Bluetooth Sco
            startSco();
            Log.v(TAG, "TTS - waiting for bluetooth sco connection");
        }
    }

    synchronized void stop() {
        forceStop();
    }

    synchronized void shutdown() {
        Log.w(TAG, "TTS - shutting down");
        forceStop();
        if (tts != null) {
            try {
                Log.v(TAG, "TTS - shutting down");
                tts.shutdown();
            } catch (Exception ignored) {}
            tts = null;
            Log.v(TAG, "TTS - destroyed");
        }
        // Release and reset all resources
        release();
    }

    private void forceStop() {
        if (utteranceListener != null) utteranceListener.clearTimeout();
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
                Log.v(TAG, "TTS - stopped");
            } catch (Exception ignored) {}
        }
        isSpeaking = false;
        Log.w(TAG, "TTS - forced stopping!");
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
        isSpeaking = false;
        requestAudioExclusive = false;
        Log.v(TAG, "TTS - states and resources released");
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

    boolean isGroupQueueEmpty() {
        synchronized (lock) {
            return !queue.containsKey(groupId) || queue.get(groupId).isEmpty();
        }
    }

    boolean isEmpty() {
        synchronized (lock) {
            return queue.isEmpty() || (queue.containsKey(DEFAULT_GROUP) && queue.size() == 1 && queue.get(DEFAULT_GROUP).isEmpty());
        }
    }

    Set<String> getGroupQueue() {
        synchronized (lock) {
            return queue.keySet();
        }
    }

    private void moveToNextGroup() {
        // is empty, still contains the group id and it's not the default one
        synchronized (lock) {
            if (queue.containsKey(groupId) && !DEFAULT_GROUP.equals(groupId)) {
                Log.v(TAG, "TTS - remove empty group: <" + groupId + ">");
                queue.remove(groupId);
            }
        }
        boolean isLastGroupEquals = Objects.equals(lastGroup, groupId);
        groupId = getNextGroup();
        if (groupId == null) {
            groupId = DEFAULT_GROUP;
            if (isLastGroupEquals) {
                Log.v(TAG, "TTS - update last group from <" + lastGroup + "> to <" + groupId + ">");
                lastGroup = groupId;
            }
        }
        Log.v(TAG, "TTS - move to new group: <" + groupId + ">");
    }

    void addFilter(MessageFilter filter) {
        filters.add(filter);
    }

    private void registerPhoneStateReceiver() {
        if (!isPhoneStateReceiverRegistered) {
            Log.v(TAG, "TTS - register for phone receiver");
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
            Log.v(TAG, "TTS - register sco receiver");
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
            Log.v(TAG, "TTS - start bluetooth sco");
        }
    }

    private void stopSco() {
        if (audioManager.isBluetoothScoAvailableOffCall() && isBluetoothScoOn) {
            audioManager.setBluetoothScoOn(false);
            audioManager.stopBluetoothSco();
            isBluetoothScoOn = false;
            Log.v(TAG, "TTS - stop bluetooth sco");
        }
    }

    private void requestAudioFocusMayDuck() {
        if (!requestAudioFocusMayDuck) {
            Log.v(TAG, "TTS - request Audio Focus May Duck");
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
            Log.v(TAG, "TTS - abandon Audio Focus May Duck");
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
            Log.v(TAG, "TTS - request Audio Focus Exclusive");
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
            Log.v(TAG, "TTS - abandon Audio Focus Exclusive");
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
        synchronized (lock) {
            listenersMap.put(utteranceId, listener);
            Log.v(TAG, "TTS - added utterance listener -> size:  " + listenersMap.size());
        }
    }

    private void addToQueue(@NonNull String utteranceId, String message, long duration, @Nullable HashMap<String, String> params, @NonNull String groupId) {
        Map<String, Object> map = new HashMap<>();
        map.put(MAP_UTTERANCE_ID, utteranceId);
        if (message != null) map.put(MAP_MESSAGE, message);
        if (duration > 0) map.put(MAP_SILENCE, duration);
        if (params != null) map.put(MAP_PARAMS, params);
        synchronized (lock) {
            if (!queue.containsKey(groupId)) {
                Log.v(TAG, "TTS - added group: <" + groupId + ">");
                lastGroup = groupId;
                queue.put(groupId, new ConcurrentLinkedQueue<>());
            }
            queue.get(groupId).add(map);
            Log.v(TAG, "TTS - added message to queue <" + groupId + ">. Number of groups: " + queue.size());
            Log.v(TAG, "TTS - messages in the queue <" + groupId + ">: " + queue.get(groupId).size());
        }
    }

    private HashMap<String, String> buildParams(@NonNull String utteranceId, @NonNull String audioStream) {
        HashMap<String, String> params = new LinkedHashMap<>();
        params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);
        params.put(TextToSpeech.Engine.KEY_PARAM_VOLUME, "1.1");
        params.put(TextToSpeech.Engine.KEY_PARAM_STREAM, audioStream);
        params.put(TextToSpeech.Engine.KEY_FEATURE_NETWORK_TIMEOUT_MS, "5000");
        params.put(TextToSpeech.Engine.KEY_FEATURE_NETWORK_RETRIES_COUNT, "2");
        Log.v(TAG, "TTS - building params");
        return params;
    }

    private void initTts(TextToSpeech.OnInitListener onInitListener, OnTextToSpeechInitialisedListener onInit) {
        if (tts == null) {
            isReady = false;
            tts = createTextToSpeech(application, onInitListener);
            utteranceListener = initUtterancesListener(onInit);
            tts.setOnUtteranceProgressListener(utteranceListener);
            Log.i(TAG, "TTS - new instance created");
        }
        else if (isReady) {
            onInitListener.onInit(TextToSpeech.SUCCESS);
        }
    }

    private UtteranceAdapter initUtterancesListener(OnTextToSpeechInitialisedListener onInit) {
        return new UtteranceAdapter() {

            private long timestamp;
            private TimerTask task;
            private Timer timer;

            @Override
            protected void clearTimeout() {
                Log.i(TAG, "TTS - timeout cleared!");
                if (task != null) task.cancel();
                if (timer != null) timer.cancel();
            }

            @Override
            protected void startTimeout(String utteranceId) {
                Log.i(TAG, "TTS - started timeout!");
                timer = new Timer();
                task = new TimerTask() {
                    @Override
                    public void run() {
                        if (tts == null || !tts.isSpeaking()) {
                            Log.e(TAG, "TTS - is null or not speaking && reached timeout!");
                            shutdown();
                            onDone(utteranceId);
                        }
                        else {
                            if ((System.currentTimeMillis() - timestamp) > TimeUnit.SECONDS.toMillis(MAX_SPEECH_TIME)) {
                                Log.e(TAG, "TTS - exceeded " + MAX_SPEECH_TIME + " sec!");
                                shutdown();
                                onDone(utteranceId);
                            }
                            else {
                                clearTimeout();
                                startTimeout(utteranceId);
                            }
                        }
                    }
                };
                timer.schedule(task, TimeUnit.SECONDS.toMillis(5));
            }

            @Override
            public void onStart(String utteranceId) {
                synchronized (lock) {
                    Log.v(TAG, "TTS - on start -> utterance listener size: " + listenersMap.size());

                    startTimeout(utteranceId);
                    timestamp = System.currentTimeMillis();

                    if (listenersMap.size() > 0) {
                        UtteranceProgressListener listener = listenersMap.get(utteranceId);
                        if (listener != null) {
                            listener.onStart(utteranceId);
                        }
                    }
                }
            }

            @Override
            public void onDone(String utteranceId) {
                clearTimeout();
                if (utteranceId.equals(CHECKING_UTTERANCE_ID)) {
                    Log.v(TAG, "TTS - on done <" + groupId + "> -> stop timeout -> go to setup language");
                    checkLanguage(onInit, true);
                }
                else {
                    synchronized (lock) {
                        Log.v(TAG, "TTS - on done <" + groupId + "> -> stop timeout");
                        if (listenersMap.size() > 0) {
                            UtteranceProgressListener listener = listenersMap.remove(utteranceId);
                            if (listener != null) {
                                Log.v(TAG, "TTS - execute on done <" + groupId + ">");
                                listener.onDone(utteranceId);
                            }
                            else {
                                resume();
                            }
                        }
                        else {
                            resume();
                        }
                    }
                }
            }

            @Override
            @TargetApi(Build.VERSION_CODES.LOLLIPOP)
            public void onError(String utteranceId, int errorCode) {
                clearTimeout();
                synchronized (lock) {
                    Log.e(TAG, "TTS - on error -> stop timeout -> utterance listener size: " + listenersMap.size());
                    Log.e(TAG, "TTS - error code: " + getTextToSpeechErrorType(errorCode));
                    if (utteranceId.equals(CHECKING_UTTERANCE_ID)) {
                        shutdown();
                        if (errorCode == TextToSpeech.ERROR_NOT_INSTALLED_YET) {
                            onInit.execute(TEXT_TO_SPEECH_NOT_AVAILABLE_ERROR);
                        }
                        else {
                            onInit.execute(TEXT_TO_SPEECH_UNKNOWN_ERROR);
                        }
                    }
                    else {
                        if (listenersMap.size() > 0) {
                            UtteranceProgressListener listener = listenersMap.remove(utteranceId);
                            if (listener != null) {
                                listener.onError(utteranceId, errorCode);
                            }
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
                Log.v(TAG, "TTS - double checking!");
                setup(application, onInit);
            }
            else {
                reviewAgain = true;
                shutdown();
                Log.v(TAG, "TTS - checking error");
                onInit.execute(TEXT_TO_SPEECH_LANGUAGE_NOT_SUPPORTED_ERROR);
            }
        }
        else {
            // Everything has gone well!
            Log.i(TAG, "TTS - checking has passed!");
            shutdown();
            onInit.execute(TEXT_TO_SPEECH_AVAILABLE);
        }
    }

    private void tryToDownloadTtsData(OnTextToSpeechInitialisedListener onInit) {
        if (!triedToDownloadTtsData) {
            triedToDownloadTtsData = true;
            Log.v(TAG, "TTS - try to download audio data");
            try {
                // Try downloading data voice!
                speak(TESTING_STRING, "DOWNLOADING_TTS_DATA", null, CHECKING_UTTERANCE_ID);
            } catch (Exception e) {
                Log.e(TAG, "error when downloading audio data: " + e.getMessage());
                // Otherwise it reports the TextToSpeechStatus to the Callback
                checkLanguage(onInit, false);
            }
        }
        else {
            Log.e(TAG, "TTS - try to download audio data - ERROR");
            shutdown();
            onInit.execute(TEXT_TO_SPEECH_UNKNOWN_ERROR);
        }
    }

    private int getMainStreamType() {
        return isBluetoothScoRequired() ? AudioManager.STREAM_VOICE_CALL : AudioManager.STREAM_MUSIC;
    }

    private void executeOnTtsReady(String utteranceId, String text, HashMap<String, String> params) {
        //noinspection ConstantConditions
        String finalText = HtmlUtils.from(text).toString();

        for (MessageFilter filter : filters) {
            Log.v(TAG, "TTS - apply filter: " + filter);
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
        Log.i(TAG, "TTS - play silence");
        tts.playSilentUtterance(durationInMillis, TextToSpeech.QUEUE_ADD, utteranceId);
    }

    private void play(String utteranceId, String text, HashMap<String, String> params) {
        Log.i(TAG, "TTS - reading out loud: \"" + text + "\"");
        Bundle newParams = new Bundle();
        String paramStream = params.get(TextToSpeech.Engine.KEY_PARAM_STREAM);
        if (paramStream != null) {
            newParams.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, Integer.valueOf(paramStream));
        }
        tts.speak(text, TextToSpeech.QUEUE_ADD, newParams, utteranceId);
    }
}
