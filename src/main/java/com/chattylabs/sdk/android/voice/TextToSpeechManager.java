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

import com.chattylabs.sdk.android.core.HtmlUtils;
import com.chattylabs.sdk.android.core.StringUtils;
import com.chattylabs.sdk.android.core.Tag;
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

    private static final String MAP_UTTERANCE_ID = "utteranceId";
    private static final String MAP_SILENCE = "silence";
    private static final String MAP_MESSAGE = "message";
    private static final String MAP_PARAMS = "params";

    // States
    private boolean isReady; // released
    private boolean isPaused; // released
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
    private String groupId = DEFAULT_GROUP; // released

    // Objects
    private final Map<String, UtteranceProgressListener> listenersMap;
    private final Map<String, ConcurrentLinkedQueue<Map<String, Object>>> queue;
    private final List<MessageFilter> filters;
    private String lastGroup;
    private AudioManager audioManager;
    private AudioFocusRequest focusRequestMayDuck;
    private AudioFocusRequest focusRequestExclusive;
    private Application application;
    private TextToSpeech tts; // released
    private UtteranceAdapter utterance; // released

    TextToSpeechManager(Application application) {
        this.listenersMap = new LinkedHashMap<>();
        this.queue = new LinkedHashMap<>();
        this.filters = new LinkedList<>();
        this.release();
        this.application = application;
        this.audioManager = (AudioManager) application.getSystemService(Context.AUDIO_SERVICE);
    }

    private TextToSpeech createTextToSpeech(Application application, TextToSpeech.OnInitListener listener) {
        return new TextToSpeech(application, listener);
    }

    synchronized void setup(Application application, OnTextToSpeechInitialisedListener onInit) {
        Log.i(TAG, "TTS - checking");
        this.application = application;
        try {
            initTts(status -> {
                if (status == TextToSpeech.SUCCESS) {
                    isReady = true;
                    // FIXME IllegalArgumentException: Invalid int: "OS" - Samsung Android 6
                    try {
                        tts.setLanguage(Locale.getDefault());
                        // try to select network synthesis
//                        for (Voice voice : tts.getVoices()) {
//                            if (voice.isNetworkConnectionRequired()) {
//                                tts.setVoice(voice);
//                            }
//                        }
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

    synchronized void speak(String text, TextToSpeechListeners... listeners) {
        Log.i(TAG, "TTS - prepare to speak \"" + text + "\" with no Group");
        UtteranceAdapter listener = generateUtteranceListener(listeners);
        String utteranceId = DEFAULT_UTTERANCE_ID + System.nanoTime();
        synchronized (listenersMap) {
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
        Log.i(TAG, "TTS - ready: " + b(isReady) + ", speaking: " + b(isSpeaking));
        if (tts == null) {
            initTts(status -> {
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
        else if (isReady && !isSpeaking) {
            playTheQueue(map);
        }
    }

    synchronized void playSilence(long durationInMillis, TextToSpeechListeners... listeners) {
        if (durationInMillis <= 0) throw new IllegalArgumentException("Silence duration must be greater than 0");
        Log.i(TAG, "TTS - play silence with no Group");
        UtteranceAdapter listener = generateUtteranceListener(listeners);
        String utteranceId = DEFAULT_UTTERANCE_ID + System.nanoTime();
        synchronized (listenersMap) {
            if (listenersMap.containsKey(utteranceId)) {
                utteranceId = utteranceId + "_" + listenersMap.size();
            }
        }
        HashMap<String, String> params = buildParams(utteranceId, String.valueOf(getMainStreamType()));
        handleListener(utteranceId, listener);
        Map<String, Object> map = new HashMap<>();
        map.put(MAP_UTTERANCE_ID, utteranceId);
        map.put(MAP_SILENCE, durationInMillis);
        map.put(MAP_PARAMS, params);
        Log.i(TAG, "TTS - ready: " + b(isReady) + ", speaking: " + b(isSpeaking));
        if (tts == null) {
            initTts(status -> {
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
        else if (isReady && !isSpeaking) {
            playTheQueue(map);
        }
    }

    synchronized void playSilence(long durationInMillis, String groupId, TextToSpeechListeners... listeners) {
        if (durationInMillis <= 0) throw new IllegalArgumentException("Silence duration must be greater than 0");
        Log.i(TAG, "TTS - play silence with Group: " + groupId);
        UtteranceAdapter listener = generateUtteranceListener(listeners);
        String utteranceId = DEFAULT_UTTERANCE_ID + System.nanoTime();
        synchronized (listenersMap) {
            if (listenersMap.containsKey(utteranceId)) {
                utteranceId = utteranceId + "_" + listenersMap.size();
            }
        }
        HashMap<String, String> params = buildParams(utteranceId, String.valueOf(getMainStreamType()));
        handleListener(utteranceId, listener);
        addToQueue(utteranceId, null, durationInMillis, params, groupId);
        Log.i(TAG, "TTS - ready: " + b(isReady) + ", speaking: " + b(isSpeaking));
        if (tts == null) {
            initTts(status -> {
                if (status == TextToSpeech.SUCCESS) {
                    isReady = true;
                    resume();
                }
                else {
                    Log.e(TAG, "TTS - Status ERROR");
                    shutdown();
                }
            }, null);
        }
        else if (isReady && !isSpeaking) {
            resume();
        }
    }

    private synchronized void speak(String text, String groupId, @Nullable UtteranceProgressListener listener) {
        speak(text, groupId, listener, DEFAULT_UTTERANCE_ID + System.nanoTime());
    }

    private synchronized void speak(String text, String groupId, @Nullable UtteranceProgressListener listener, String utteranceId) {
        Log.i(TAG, "TTS - prepare to speak \"" + text + "\" with Group: " + groupId);
        synchronized (listenersMap) {
            if (listenersMap.containsKey(utteranceId)) {
                utteranceId = utteranceId + "_" + listenersMap.size();
            }
        }
        HashMap<String, String> params = buildParams(utteranceId, String.valueOf(getMainStreamType()));
        if (listener != null) handleListener(utteranceId, listener);
        addToQueue(utteranceId, text, -1, params, groupId);
        Log.i(TAG, "TTS - ready: " + b(isReady) + ", speaking: " + b(isSpeaking));
        if (tts == null) {
            initTts(status -> {
                if (status == TextToSpeech.SUCCESS) {
                    isReady = true;
                    resume();
                }
                else {
                    Log.e(TAG, "TTS - Status ERROR");
                    shutdown();
                }
            }, null);
        }
        else if (isReady && !isSpeaking) {
            resume();
        }
    }

    private UtteranceAdapter generateUtteranceListener(TextToSpeechListeners... listeners) {
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
        if (listeners != null && listeners.length > 0) {
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
        resume(false);
    }

    private synchronized void resume(boolean isFromUtterance) {
        if (isPaused) {
            Log.i(TAG, "TTS - is paused, no resume");
            if (isFromUtterance) {
                Log.i(TAG, "TTS - stopped speaking");
                isSpeaking = false;
            }
            return;
        }
        Log.i(TAG, "TTS - resuming group: " + groupId);
        checkForEmptyGroup();
        if (!isGroupQueueEmpty()) {
            isSpeaking = true;
            // Gets and plays the current message in the queue
            synchronized (queue) {
                playTheQueue(queue.get(groupId).poll());
            }
        } else {
            abandonAudioFocusMayDuck();
            abandonAudioFocusExclusive();
            stopSco();
            isSpeaking = false;
            Log.i(TAG, "TTS - unPause isSpeaking");
            Log.i(TAG, "TTS - Finished - no more pending messages.");
        }
    }

    private void checkForEmptyGroup() {
        if (isGroupQueueEmpty()) {
            Log.v(TAG, "TTS - no more messages on the group queue");
            moveToNextGroup();
        }
    }

    private void playTheQueue(Map<String, Object> map) {
        //isSpeaking = true; // TODO.. da algunos conflictos al ponerlo aqui
        // Sco Listener
        OnScoListener listener = new OnScoListener() {
            @Override
            public void onConnected() {
                if (requestAudioExclusive) requestAudioFocusExclusive();
                else requestAudioFocusMayDuck();
                if (map.containsKey(MAP_MESSAGE)) {
                    //noinspection unchecked
                    executeOnTtsReady((String) map.get(MAP_UTTERANCE_ID),
                                      (String) map.get(MAP_MESSAGE),
                                      (HashMap<String, String>) map.get(MAP_PARAMS));
                } else {
                    //noinspection unchecked
                    playInSilence((String) map.get(MAP_UTTERANCE_ID),
                                  (long) map.get(MAP_SILENCE),
                                  (HashMap<String, String>) map.get(MAP_PARAMS));
                }
            }

            @Override
            public void onDisconnected() {
                Log.w(TAG, "Sco onDisconnected - shutdown");
                shutdown();
            }
        };
        // Register for incoming calls and others
        registerGeneralReceivers();
        // Check whether Sco is connected or required
        if (!isBluetoothScoRequired() || audioManager.isBluetoothScoOn()) {
            Log.v(TAG, "TTS - " + (audioManager.isBluetoothScoOn() ? "bluetooth sco on" : "bluetooth sco off"));
            listener.onConnected();
        } else {
            Log.v(TAG, "TTS - waiting for bluetooth sco");
            // Sco receivers
            registerScoReceivers(listener);
            // Start Bluetooth Sco
            startSco();
        }
    }

    synchronized void unPause() {
        Log.w(TAG, "TTS - unPause Pause");
        isPaused = false;
    }

    synchronized void doPause() {
        Log.w(TAG, "TTS - do Pause");
        isPaused = true;
        isSpeaking = false;
    }

    synchronized boolean isPaused() {
        return isPaused;
    }

    synchronized void stop() {
        if (tts != null) {
            Log.w(TAG, "TTS - do stop");
            tts.stop();
        }
        if (utterance != null) utterance.clearTimeout();
    }

    synchronized void shutdown() {
        Log.w(TAG, "TTS - shutting down");
        // Unregister all broadcast receivers
        unRegisterReceivers();
        forceStop();
        // Release and reset all resources
        release();
    }

    private void forceStop() {
        // Stop Bluetooth Sco if required
        stopSco();
        // Audio focus
        abandonAudioFocusMayDuck();
        abandonAudioFocusExclusive();
        // Shutdown text to speech
        if (tts != null) {
            try {
                tts.shutdown();
                Log.v(TAG, "TTS - properly shutdown");
            } catch (Exception ignored) {}
            tts = null;
            Log.v(TAG, "TTS - destroyed");
        }
        if (utterance != null) utterance.clearTimeout();
        isReady = false;
        isPaused = false;
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
        synchronized (listenersMap) {
            listenersMap.clear();
        }
        synchronized (queue) {
            queue.clear();
            queue.put(DEFAULT_GROUP, new ConcurrentLinkedQueue<>());
        }
        synchronized (filters) {
            filters.clear();
        }
        groupId = DEFAULT_GROUP;
        bluetoothScoRequired = false;
        reviewAgain = true;
        triedToDownloadTtsData = false;
        isReady = false;
        isPaused = false;
        isSpeaking = false;
        requestAudioExclusive = false;
        Log.v(TAG, "TTS - released");
    }

    boolean isBluetoothScoRequired() {
        return bluetoothScoRequired;
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
        synchronized (queue) {
            Set<String> keys = queue.keySet();
            if (keys.size() > 1) {
                return (String) keys.toArray()[1];
            }
            else {
                return null;
            }
        }
    }

    boolean isGroupQueueEmpty() {
        synchronized (queue) {
            return !queue.containsKey(groupId) || queue.get(groupId).isEmpty();
        }
    }

    private void moveToNextGroup() {
        // is empty, still contains the group id and it's not the default one
        if (queue.containsKey(groupId) && !DEFAULT_GROUP.equals(groupId)) {
            Log.v(TAG, "TTS - remove empty group: " + groupId);
            queue.remove(groupId);
        }
        boolean isLastGroupEquals = Objects.equals(lastGroup, groupId);
        groupId = getNextGroup();
        if (groupId == null) {
            groupId = DEFAULT_GROUP;
            if (isLastGroupEquals) {
                Log.v(TAG, "TTS - update last group [\""+lastGroup+"\"] to [\""+groupId+"\"]");
                lastGroup = groupId;
            }
        }
        Log.v(TAG, "TTS - move to new group: " + groupId);
    }

    void addFilter(MessageFilter filter) {
        filters.add(filter);
    }

    private PhoneStateReceiver phoneStateReceiver = new PhoneStateReceiver();

    private ScoReceiver scoReceiver = new ScoReceiver();

    private void registerGeneralReceivers() {
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

    private void unRegisterReceivers() {
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
        if (audioManager.isBluetoothScoAvailableOffCall() && !audioManager.isBluetoothScoOn()) {
            audioManager.setBluetoothScoOn(true);
            audioManager.startBluetoothSco();
            isBluetoothScoOn = true;
            Log.v(TAG, "TTS - start bluetooth sco");
        }
    }

    private void stopSco() {
        if (audioManager.isBluetoothScoAvailableOffCall() && audioManager.isBluetoothScoOn()) {
            audioManager.setBluetoothScoOn(false);
            audioManager.stopBluetoothSco();
            isBluetoothScoOn = false;
            Log.v(TAG, "TTS - stop bluetooth sco");
        }
    }

    private void abandonAudioFocusMayDuck() {
        if (requestAudioFocusMayDuck) {
            Log.v(TAG, "TTS - abandon Audio Focus May Duck");
            requestAudioFocusMayDuck = false;
            unsetAudioMode();
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N_MR1) {
                //noinspection deprecation
                requestAudioFocusMayDuck = AudioManager.AUDIOFOCUS_REQUEST_GRANTED == audioManager.abandonAudioFocus(null);
            } else {
                requestAudioFocusMayDuck = focusRequestMayDuck == null || AudioManager.AUDIOFOCUS_REQUEST_GRANTED == audioManager
                        .abandonAudioFocusRequest(focusRequestMayDuck);
            }
        }
    }

    private void abandonAudioFocusExclusive() {
        if (requestAudioFocusExclusive) {
            Log.v(TAG, "TTS - abandon Audio Focus Exclusive");
            unsetAudioMode();
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N_MR1) {
                //noinspection deprecation
                requestAudioFocusExclusive = AudioManager.AUDIOFOCUS_REQUEST_GRANTED == audioManager.abandonAudioFocus(null);
            } else {
                requestAudioFocusExclusive = focusRequestExclusive == null || AudioManager.AUDIOFOCUS_REQUEST_GRANTED == audioManager
                        .abandonAudioFocusRequest(focusRequestExclusive);
            }
        }
    }

    private void requestAudioFocusMayDuck() {
        if (!requestAudioFocusMayDuck) {
            Log.v(TAG, "TTS - request Audio Focus May Duck");
            setAudioMode();
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N_MR1) {
                //noinspection deprecation
                requestAudioFocusMayDuck = AudioManager.AUDIOFOCUS_REQUEST_GRANTED == audioManager.requestAudioFocus(
                        null,
                        getMainStreamType(),
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
            } else {
                focusRequestMayDuck = new AudioFocusRequest
                        .Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                        .setAudioAttributes(new AudioAttributes.Builder()
                                                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                                                    .setLegacyStreamType(getMainStreamType())
                                                    .build()).build();
                requestAudioFocusMayDuck = AudioManager.AUDIOFOCUS_REQUEST_GRANTED == audioManager.requestAudioFocus(focusRequestMayDuck);
            }
        }
    }

    private void requestAudioFocusExclusive() {
        if (!requestAudioFocusExclusive) {
            Log.v(TAG, "TTS - request Audio Focus Exclusive");
            setAudioMode();
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N_MR1) {
                //noinspection deprecation
                requestAudioFocusExclusive = AudioManager.AUDIOFOCUS_REQUEST_GRANTED == audioManager.requestAudioFocus(
                        null,
                        getMainStreamType(),
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE);

            } else {
                focusRequestExclusive = new AudioFocusRequest
                        .Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                        .setAudioAttributes(new AudioAttributes.Builder()
                                                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                                    .setUsage(
                                                            isBluetoothScoRequired() ? AudioAttributes.USAGE_VOICE_COMMUNICATION
                                                                                     : AudioAttributes.USAGE_MEDIA
                                                    )
                                                    .setLegacyStreamType(getMainStreamType())
                                                    .build()).build();
                requestAudioFocusExclusive = AudioManager.AUDIOFOCUS_REQUEST_GRANTED == audioManager.requestAudioFocus(focusRequestExclusive);
            }
        }
    }

    private void setAudioMode() {
        audioMode = audioManager.getMode();
        speakerphoneOn = audioManager.isSpeakerphoneOn();
        audioManager.setMode(isBluetoothScoRequired() ? AudioManager.MODE_IN_CALL : AudioManager.MODE_NORMAL);
        audioManager.setSpeakerphoneOn(!isBluetoothScoRequired());
    }

    private void unsetAudioMode() {
        audioManager.setMode(audioMode);
        audioManager.setSpeakerphoneOn(speakerphoneOn);
    }

    private void handleListener(@NonNull String utteranceId, @NonNull UtteranceProgressListener listener) {
        //Preconditions.checkNotNull(utteranceId);
        synchronized (listenersMap) {
            listenersMap.put(utteranceId, listener);
        }
        Log.v(TAG, "TTS - added utterance listener, size:  " + listenersMap.size());
    }

    private void addToQueue(@NonNull String utteranceId, String message, long duration, HashMap<String, String> params, @NonNull String groupId) {
        Map<String, Object> map = new HashMap<>();
        map.put(MAP_UTTERANCE_ID, utteranceId);
        if (message != null) map.put(MAP_MESSAGE, message);
        if (duration > 0) map.put(MAP_SILENCE, duration);
        map.put(MAP_PARAMS, params);
        synchronized (queue) {
            if (!queue.containsKey(groupId)) {
                Log.v(TAG, "TTS - added group: " + groupId);
                lastGroup = groupId;
                queue.put(groupId, new ConcurrentLinkedQueue<>());
            }
            queue.get(groupId).add(map);
            Log.v(TAG, "TTS - added message to queue, number of groups: " + queue.size());
            Log.v(TAG, "TTS - messages in the queue: " + queue.get(groupId).size());
        }
    }

    private HashMap<String, String> buildParams(@NonNull String utteranceId, @NonNull String audioStream) {
        HashMap<String, String> params = new LinkedHashMap<>();
        params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);
        params.put(TextToSpeech.Engine.KEY_PARAM_STREAM, audioStream);
        params.put(TextToSpeech.Engine.KEY_FEATURE_NETWORK_TIMEOUT_MS, "5000");
        params.put(TextToSpeech.Engine.KEY_FEATURE_NETWORK_RETRIES_COUNT, "3");
        Log.v(TAG, "TTS - building params " + params);
        return params;
    }

    private void initTts(TextToSpeech.OnInitListener onInitListener, OnTextToSpeechInitialisedListener onInit) {
        if (tts == null) {
            isReady = false;
            tts = createTextToSpeech(application, onInitListener);
            utterance = initUtterancesListener(onInit);
            tts.setOnUtteranceProgressListener(utterance);
            Log.i(TAG, "TTS - created");
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
                task.cancel();
                timer.cancel();
            }

            @Override
            protected void startTimeout(String utteranceId) {
                Log.i(TAG, "TTS - started timeout!");
                timer = new Timer();
                task = new TimerTask() {
                    @Override
                    public void run() {
                        if (!tts.isSpeaking()) {
                            Log.e(TAG, "TTS - reached timeout!");
                            forceStop();
                            onDone(utteranceId);
                        }
                        else {
                            if (System.currentTimeMillis() - timestamp > TimeUnit.SECONDS.toMillis(30)) {
                                Log.e(TAG, "TTS - over 30 sec!");
                                forceStop();
                                onDone(utteranceId);
                            } else startTimeout(utteranceId);
                        }
                    }
                };
                timer.schedule(task, TimeUnit.SECONDS.toMillis(5));
            }

            @Override
            public void onStart(String utteranceId) {
                Log.v(TAG, "TTS - on start, utterance listener size: " + listenersMap.size());

                startTimeout(utteranceId);
                timestamp = System.currentTimeMillis();

                synchronized (listenersMap) {
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
                    Log.v(TAG, "TTS - on done, stop timeout, go to setup language");
                    checkLanguage(onInit, true);
                }
                else {
                    synchronized (listenersMap) {
                        Log.v(TAG, "TTS - on done, stop timeout, utterance listener size: " + listenersMap.size());
                        if (listenersMap.size() > 0) {
                            UtteranceProgressListener listener = listenersMap.remove(utteranceId);
                            if (listener != null) {
                                listener.onDone(utteranceId);
                            }
                        }
                        resume(true);
                    }
                }
            }

            @Override
            @TargetApi(Build.VERSION_CODES.LOLLIPOP)
            public void onError(String utteranceId, int errorCode) {
                clearTimeout();
                Log.e(TAG, "TTS - on error, stop timeout, utterance listener size: " + listenersMap.size());
                Log.e(TAG, "TTS - error code: " + getTextToSpeechErrorType(errorCode));
                if (utteranceId.equals(CHECKING_UTTERANCE_ID)) {
                    shutdown();
                    if (errorCode == TextToSpeech.ERROR_NOT_INSTALLED_YET) {
                        onInit.execute(TEXT_TO_SPEECH_NOT_AVAILABLE_ERROR);
                    } else {
                        onInit.execute(TEXT_TO_SPEECH_UNKNOWN_ERROR);
                    }
                }
                else {
                    synchronized (listenersMap) {
                        if (listenersMap.size() > 0) {
                            UtteranceProgressListener listener = listenersMap.remove(utteranceId);
                            if (listener != null) {
                                listener.onError(utteranceId, errorCode);
                            }
                        }
                        resume(true);
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
                speak("", "DOWNLOADING_TTS_DATA", null, CHECKING_UTTERANCE_ID);
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
        String finalText = HtmlUtils.from(text).toString();

        for (MessageFilter filter : filters) {
            Log.v(TAG, "TTS - apply filter: " + filter);
            finalText = filter.apply(finalText);
        }

        if (finalText.length() > TextToSpeech.getMaxSpeechInputLength()) {
            String[] split = StringUtils.split(finalText, TextToSpeech.getMaxSpeechInputLength());
            for (String item : split) {
                play(utteranceId, item, params);
            }
        } else {
            play(utteranceId, finalText, params);
        }
    }

    private void playInSilence(String utteranceId, long durationInMillis, HashMap<String, String> params) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts.playSilentUtterance(durationInMillis, TextToSpeech.QUEUE_ADD, utteranceId);
        } else {
            //noinspection deprecation
            tts.playSilence(durationInMillis, TextToSpeech.QUEUE_ADD, params);
        }
    }

    private void play(String utteranceId, String text, HashMap<String, String> params) {
        Log.i(TAG, "TTS - reading out loud: \"" + text + "\"");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Bundle newParams = new Bundle();
            String paramStream = params.get(TextToSpeech.Engine.KEY_PARAM_STREAM);
            if (paramStream != null) {
                newParams.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, Integer.valueOf(paramStream));
            }
            tts.speak(text, TextToSpeech.QUEUE_ADD, newParams, utteranceId);
        } else {
            //noinspection deprecation
            tts.speak(text, TextToSpeech.QUEUE_ADD, params);
        }
    }
}
