package com.chattylabs.module.voice;

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

import com.chattylabs.module.core.HtmlUtils;
import com.chattylabs.module.core.StringUtils;
import com.chattylabs.module.core.Tag;
import com.chattylabs.module.voice.VoiceInteractionComponent.OnTextToSpeechDoneListener;
import com.chattylabs.module.voice.VoiceInteractionComponent.OnTextToSpeechErrorListener;
import com.chattylabs.module.voice.VoiceInteractionComponent.OnTextToSpeechInitialisedListener;
import com.chattylabs.module.voice.VoiceInteractionComponent.OnTextToSpeechStartedListener;
import com.chattylabs.module.voice.interaction.BuildConfig;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import static com.chattylabs.module.voice.VoiceInteractionComponent.MessageFilter;
import static com.chattylabs.module.voice.VoiceInteractionComponent.TEXT_TO_SPEECH_AVAILABLE;
import static com.chattylabs.module.voice.VoiceInteractionComponent.TEXT_TO_SPEECH_AVAILABLE_BUT_INACTIVE;
import static com.chattylabs.module.voice.VoiceInteractionComponent.TEXT_TO_SPEECH_LANGUAGE_NOT_SUPPORTED_ERROR;
import static com.chattylabs.module.voice.VoiceInteractionComponent.TEXT_TO_SPEECH_NOT_AVAILABLE_ERROR;
import static com.chattylabs.module.voice.VoiceInteractionComponent.TEXT_TO_SPEECH_UNKNOWN_ERROR;
import static com.chattylabs.module.voice.VoiceInteractionComponent.TextToSpeechListeners;
import static com.chattylabs.module.voice.VoiceInteractionComponent.getTextToSpeechErrorType;

final class TextToSpeechManager {
    private static final String TAG = Tag.make(TextToSpeechManager.class);

    private static final String DEFAULT_GROUP_ID = "default_group_id";
    private static final String CHECKING_UTTERANCE_ID = BuildConfig.APPLICATION_ID + ".checking";
    private static final String DEFAULT_UTTERANCE_ID = BuildConfig.APPLICATION_ID + ".utterance:";

    private static final String MAP_UTTERANCE_ID = "utteranceId";
    private static final String MAP_MESSAGE = "message";
    private static final String MAP_PARAMS = "params";

    private Map<String, UtteranceProgressListener> listenersMap;
    private Map<String, ConcurrentLinkedQueue<Map<String, Object>>> msgQueue;
    private List<MessageFilter> filters;

    private boolean isReady;
    private boolean isPaused;
    private boolean isSpeaking;
    private boolean reviewAgain;
    private boolean triedToDownloadTtsData;
    private boolean bluetoothScoRequired;
    private boolean requestAudioFocusMayDuck;
    private boolean requestAudioFocusExclusive;
    private boolean isPhoneStateReceiverRegistered;
    private boolean isScoReceiverRegistered;
    private boolean speakerphoneOn;
    private boolean requestAudioExclusive;

    private String groupId = DEFAULT_GROUP_ID;
    private int audioMode;
    private AudioManager audioManager;
    private AudioFocusRequest focusRequestMayDuck;
    private AudioFocusRequest focusRequestExclusive;
    private Application application;
    private TextToSpeech tts;

    TextToSpeechManager(Application application) {
        this.release();
        this.application = application;
        this.audioManager = (AudioManager) application.getSystemService(Context.AUDIO_SERVICE);
    }

    private TextToSpeech createTextToSpeech(Application application, TextToSpeech.OnInitListener listener) {
        return new TextToSpeech(application, listener);
    }

    synchronized void check(OnTextToSpeechInitialisedListener onInit) {
        Log.i(TAG, "TTS checking");
        try {
            initTts(status -> {
                if (status == TextToSpeech.SUCCESS) {
                    isReady = true;
                    // FIXME IllegalArgumentException: Invalid int: "OS" - Samsung Android 6
                    try {
                        tts.setLanguage(Locale.getDefault());
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
        Log.i(TAG, "TTS speak with no group");
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
        Log.i(TAG, "TTS ready: " + b(isReady) + ", speaking: " + b(isSpeaking));
        if (tts == null) {
            initTts(status -> {
                if (status == TextToSpeech.SUCCESS) {
                    Log.i(TAG, "TTS TextToSpeechStatus SUCCESS");
                    isReady = true;
                    playTheQueue(map);
                }
                else {
                    shutdown();
                }
            }, null);
        }
        else if (isReady && !isSpeaking) {
            playTheQueue(map);
        }
    }

    private synchronized void speak(String text, String groupId, @Nullable UtteranceProgressListener listener) {
        speak(text, groupId, listener, DEFAULT_UTTERANCE_ID + System.nanoTime());
    }

    private synchronized void speak(String text, String groupId, @Nullable UtteranceProgressListener listener, String utteranceId) {
        if (listenersMap.containsKey(utteranceId)) {
            utteranceId = utteranceId + "_" + msgQueue.size();
        }
        HashMap<String, String> params = buildParams(utteranceId, String.valueOf(getMainStreamType()));
        if (listener != null) handleListener(utteranceId, listener);
        addMessageToQueue(utteranceId, text, params, groupId);
        Log.i(TAG, "TTS ready: " + b(isReady) + ", speaking: " + b(isSpeaking));
        if (tts == null) {
            initTts(status -> {
                if (status == TextToSpeech.SUCCESS) {
                    Log.i(TAG, "TTS TextToSpeechStatus SUCCESS");
                    isReady = true;
                    resume();
                }
                else {
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

    private synchronized void resume() {
        if (isPaused) {
            Log.i(TAG, "TTS is paused");
            return;
        }
        Log.i(TAG, "TTS is resuming");
        if (isGroupQueueEmpty()) {
            Log.i(TAG, "TTS is empty");
            moveToNextGroup();
        }
        if (!isGroupQueueEmpty()) {
            isSpeaking = true;
            // Gets and plays the current message in the queue
            playTheQueue(msgQueue.get(groupId).poll());
        } else {
            abandonAudioFocusMayDuck();
            abandonAudioFocusExclusive();
            stopSco();
            isSpeaking = false;
            Log.i(TAG, "no more pending messages");
        }
    }

    private void playTheQueue(Map<String, Object> map) {
        // Sco Listener
        OnScoListener listener = new OnScoListener() {
            @Override
            public void onConnected() {
                if (requestAudioExclusive) requestAudioFocusExclusive();
                else requestAudioFocusMayDuck();
                //noinspection unchecked
                executeOnTtsReady((String) map.get(MAP_UTTERANCE_ID),
                                  (String) map.get(MAP_MESSAGE),
                                  (HashMap<String, String>) map.get(MAP_PARAMS));
            }

            @Override
            public void onDisconnected() {
                Log.i(TAG, "Sco onDisconnected - shutdown");
                shutdown();
            }
        };
        // Register for incoming calls and others
        registerGeneralReceivers();
        // Check whether Sco is connected or required
        if (!isBluetoothScoRequired() || audioManager.isBluetoothScoOn()) {
            Log.i(TAG, audioManager.isBluetoothScoOn() ? "bluetooth sco is on" : "no bluetooth sco");
            listener.onConnected();
        } else {
            Log.i(TAG, "waiting for bluetooth sco");
            // Sco receivers
            registerScoReceivers(listener);
            // Start Bluetooth Sco
            startSco();
        }
    }

    synchronized void playSilence() {
//        if (durationInMillis > 0) {
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
//                tts.playSilentUtterance(durationInMillis, TextToSpeech.QUEUE_ADD, utteranceId);
//            } else {
//                //noinspection deprecation
//                tts.playSilence(durationInMillis, TextToSpeech.QUEUE_ADD, params);
//            }
//            return;
//        }
    }

    synchronized void play() {
        Log.i(TAG, "TTS play");
        isPaused = false;
        resume();
    }

    synchronized void pause() {
        Log.i(TAG, "TTS pause");
        isPaused = true;
        isSpeaking = false;
    }

    synchronized boolean isPaused() {
        return isPaused;
    }

    synchronized void stop() {
        if (tts != null) {
            Log.i(TAG, "TTS stop");
            tts.stop();
        }
    }

    synchronized void shutdown() {
        // Release and reset all resources
        release();
        // Unregister all broadcast receivers
        unRegisterReceivers();
        // Stop Bluetooth Sco if required
        stopSco();
        // Audio focus
        abandonAudioFocusMayDuck();
        abandonAudioFocusExclusive();
        // Shutdown text to speech
        if (tts != null) {
            try {
                tts.shutdown();
            } catch (Exception ignored) {}
            tts = null;
            Log.i(TAG, "TTS destroyed");
        }
    }

    public TextToSpeech.EngineInfo getEngineByName(TextToSpeech tts, String name) {
        List<TextToSpeech.EngineInfo> engines = tts.getEngines();
        for (TextToSpeech.EngineInfo engineInfo : engines) {
            if (engineInfo.name.contains(name)) {
                return engineInfo;
            }
        }
        return null;
    }

    @SuppressWarnings({"unchecked", "TryWithIdenticalCatches"})
    public String getCurrentEngine(TextToSpeech tts) {
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
        listenersMap = new LinkedHashMap<>();
        msgQueue = new LinkedHashMap<>();
        msgQueue.put(DEFAULT_GROUP_ID, new ConcurrentLinkedQueue<>());
        filters = new LinkedList<>();
        bluetoothScoRequired = false;
        isReady = false;
        reviewAgain = true;
        triedToDownloadTtsData = false;
        isPaused = false;
        isSpeaking = false;
    }

    boolean isGroupQueueEmpty() {
        boolean isEmpty = !msgQueue.containsKey(groupId) || msgQueue.get(groupId).isEmpty();
        if (isEmpty && !groupId.equals(DEFAULT_GROUP_ID)) {
            Log.i(TAG, "remove group id: " + groupId);
            msgQueue.remove(groupId);
        }
        return isEmpty;
    }

    private void moveToNextGroup() {
        Set<String> keys = msgQueue.keySet();
        if (keys.size() > 1) {
            groupId = (String) keys.toArray()[1];
        } else {
            groupId = DEFAULT_GROUP_ID;
        }
        Log.i(TAG, "moved to group: " + groupId);
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

    void addFilter(MessageFilter filter) {
        filters.add(filter);
    }

    private PhoneStateReceiver phoneStateReceiver = new PhoneStateReceiver();

    private ScoReceiver scoReceiver = new ScoReceiver();

    private void registerGeneralReceivers() {
        if (!isPhoneStateReceiverRegistered) {
            Log.i(TAG, "register for phone receiver");
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
            Log.i(TAG, "register for sco receiver");
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
            Log.i(TAG, "start bluetooth sco");
        }
    }

    private void stopSco() {
        if (audioManager.isBluetoothScoAvailableOffCall() && audioManager.isBluetoothScoOn()) {
            audioManager.setBluetoothScoOn(false);
            audioManager.stopBluetoothSco();
            Log.i(TAG, "stop bluetooth sco");
        }
    }

    private boolean abandonAudioFocusMayDuck() {
        if (requestAudioFocusMayDuck && !requestAudioFocusExclusive) {
            Log.i(TAG, "abandon Audio Focus May Duck");
            requestAudioFocusMayDuck = false;
            unsetAudioMode();
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N_MR1) {
                //noinspection deprecation
                return AudioManager.AUDIOFOCUS_REQUEST_GRANTED == audioManager.abandonAudioFocus(null);
            } else {
                return focusRequestMayDuck == null || AudioManager.AUDIOFOCUS_REQUEST_GRANTED == audioManager
                        .abandonAudioFocusRequest(focusRequestMayDuck);
            }
        }
        return true;
    }

    private boolean abandonAudioFocusExclusive() {
        if (requestAudioFocusExclusive) {
            Log.i(TAG, "abandon Audio Focus Exclusive");
            requestAudioFocusExclusive = false;
            unsetAudioMode();
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N_MR1) {
                //noinspection deprecation
                return AudioManager.AUDIOFOCUS_REQUEST_GRANTED == audioManager.abandonAudioFocus(null);
            } else {
                return focusRequestExclusive == null || AudioManager.AUDIOFOCUS_REQUEST_GRANTED == audioManager
                        .abandonAudioFocusRequest(focusRequestExclusive);
            }
        }
        return true;
    }

    private boolean requestAudioFocusMayDuck() {
        if (!requestAudioFocusMayDuck && !requestAudioFocusExclusive) {
            Log.i(TAG, "request Audio Focus May Duck");
            requestAudioFocusMayDuck = true;
            setAudioMode();
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N_MR1) {
                //noinspection deprecation
                return AudioManager.AUDIOFOCUS_REQUEST_GRANTED == audioManager.requestAudioFocus(
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
                return AudioManager.AUDIOFOCUS_REQUEST_GRANTED == audioManager.requestAudioFocus(focusRequestMayDuck);
            }
        }
        return true;
    }

    private boolean requestAudioFocusExclusive() {
        if (!requestAudioFocusExclusive) {
            Log.i(TAG, "request Audio Focus Exclusive");
            requestAudioFocusMayDuck = false;
            requestAudioFocusExclusive = true;
            setAudioMode();
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N_MR1) {
                //noinspection deprecation
                return AudioManager.AUDIOFOCUS_REQUEST_GRANTED == audioManager.requestAudioFocus(
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
                return AudioManager.AUDIOFOCUS_REQUEST_GRANTED == audioManager.requestAudioFocus(focusRequestExclusive);
            }
        }
        return true;
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
        listenersMap.put(utteranceId, listener);
        Log.i(TAG, "Added a new listener, size:  " + listenersMap.size());
    }

    private void addMessageToQueue(@NonNull String utteranceId, String message, HashMap<String, String> params, @NonNull String groupId) {
        Map<String, Object> map = new HashMap<>();
        map.put(MAP_UTTERANCE_ID, utteranceId);
        map.put(MAP_MESSAGE, message);
        map.put(MAP_PARAMS, params);
        if (!msgQueue.containsKey(groupId)) {
            Log.i(TAG, "Added a new group: " + groupId);
            msgQueue.put(groupId, new ConcurrentLinkedQueue<>());
        }
        msgQueue.get(groupId).add(map);
        Log.i(TAG, "Added a message to the queue, size: " + msgQueue.size());
    }

    private HashMap<String, String> buildParams(@NonNull String utteranceId, @NonNull String audioStream) {
        HashMap<String, String> params = new LinkedHashMap<>();
        params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);
        params.put(TextToSpeech.Engine.KEY_PARAM_STREAM, audioStream);
        Log.i(TAG, "Building TTS params " + params);
        return params;
    }

    private void initTts(TextToSpeech.OnInitListener onInitListener, OnTextToSpeechInitialisedListener onInit) {
        if (tts == null) {
            isReady = false;
            tts = createTextToSpeech(application, onInitListener);
            tts.setOnUtteranceProgressListener(initUtterancesListener(onInit));
            Log.i(TAG, "TTS created");
        }
        else if (isReady) {
            onInitListener.onInit(TextToSpeech.SUCCESS);
        }
    }

    private UtteranceProgressListener initUtterancesListener(OnTextToSpeechInitialisedListener onInit) {
        return new UtteranceProgressListener() {

            @Override
            public void onStart(String utteranceId) {
                Log.i(TAG, "on start, listener map size: " + listenersMap.size());
                if (listenersMap.size() > 0) {
                    UtteranceProgressListener listener = listenersMap.get(utteranceId);
                    if (listener != null) {
                        listener.onStart(utteranceId);
                    }
                }
            }

            @Override
            public void onDone(String utteranceId) {
                if (utteranceId.equals(CHECKING_UTTERANCE_ID)) {
                    Log.i(TAG, "on done, check language");
                    checkLanguage(onInit, true);
                }
                else {
                    Log.i(TAG, "on done, listener map size: " + listenersMap.size());
                    if (listenersMap.size() > 0) {
                        UtteranceProgressListener listener = listenersMap.remove(utteranceId);
                        if (listener != null) {
                            Log.i(TAG, "reading message done");
                            listener.onDone(utteranceId);
                        }
                    }
                    resume();
                }
            }

            @Override
            public void onError(String utteranceId) {
                Log.i(TAG, "on error, listener map size: " + listenersMap.size());
                if (utteranceId.equals(CHECKING_UTTERANCE_ID)) {
                    shutdown();
                    onInit.execute(TEXT_TO_SPEECH_UNKNOWN_ERROR);
                }
                else {
                    if (listenersMap.size() > 0) {
                        UtteranceProgressListener listener = listenersMap.remove(utteranceId);
                        if (listener != null) {
                            //noinspection deprecation
                            listener.onError(utteranceId, -1);
                        }
                    }
                    resume();
                }
            }

            @Override
            @TargetApi(Build.VERSION_CODES.LOLLIPOP)
            public void onError(String utteranceId, int errorCode) {
                Log.i(TAG, "on error, listener map size: " + listenersMap.size());
                Log.i(TAG, "on error, code: " + getTextToSpeechErrorType(errorCode));
                if (utteranceId.equals(CHECKING_UTTERANCE_ID)) {
                    shutdown();
                    if (errorCode == TextToSpeech.ERROR_NOT_INSTALLED_YET) {
                        onInit.execute(TEXT_TO_SPEECH_NOT_AVAILABLE_ERROR);
                    } else {
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
                    resume();
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
                Log.i(TAG, "check again");
                check(onInit);
            }
            else {
                reviewAgain = true;
                shutdown();
                Log.i(TAG, "checking error");
                onInit.execute(TEXT_TO_SPEECH_LANGUAGE_NOT_SUPPORTED_ERROR);
            }
        }
        else {
            // Everything has gone well!
            Log.i(TAG, "checking has passed!");
            shutdown();
            onInit.execute(TEXT_TO_SPEECH_AVAILABLE);
        }
    }

    private void tryToDownloadTtsData(OnTextToSpeechInitialisedListener onInit) {
        if (!triedToDownloadTtsData) {
            triedToDownloadTtsData = true;
            Log.i(TAG, "try to download audio data");
            try {
                // Try downloading data voice!
                speak("", "DOWNLOADING_TTS_DATA", null, CHECKING_UTTERANCE_ID);
            } catch (Exception e) {
                Log.i(TAG, "error when downloading audio data: " + e.getMessage());
                // Otherwise it reports the TextToSpeechStatus to the Callback
                checkLanguage(onInit, false);
            }
        }
        else {
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

    private void play(String utteranceId, String text, HashMap<String, String> params) {
        Log.i(TAG, "read message: \"" + text + "\"");
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
