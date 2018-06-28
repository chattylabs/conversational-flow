package com.chattylabs.sdk.android.voice;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.chattylabs.sdk.android.common.Tag;
import com.chattylabs.sdk.android.common.internal.ILogger;
import com.chattylabs.sdk.android.common.internal.android.AndroidHandler;
import com.chattylabs.sdk.android.common.internal.android.AndroidHandlerImpl;
import com.chattylabs.sdk.android.voice.VoiceInteractionComponent.SpeechRecognizerCreator;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static com.chattylabs.sdk.android.voice.VoiceInteractionComponent.MIN_LISTENING_TIME;
import static com.chattylabs.sdk.android.voice.VoiceInteractionComponent.OnVoiceRecognitionErrorListener;
import static com.chattylabs.sdk.android.voice.VoiceInteractionComponent.OnVoiceRecognitionMostConfidentResultListener;
import static com.chattylabs.sdk.android.voice.VoiceInteractionComponent.OnVoiceRecognitionPartialResultsListener;
import static com.chattylabs.sdk.android.voice.VoiceInteractionComponent.OnVoiceRecognitionReadyListener;
import static com.chattylabs.sdk.android.voice.VoiceInteractionComponent.OnVoiceRecognitionResultsListener;
import static com.chattylabs.sdk.android.voice.VoiceInteractionComponent.VOICE_RECOGNITION_AFTER_PARTIALS_ERROR;
import static com.chattylabs.sdk.android.voice.VoiceInteractionComponent.VOICE_RECOGNITION_EMPTY_RESULTS_ERROR;
import static com.chattylabs.sdk.android.voice.VoiceInteractionComponent.VOICE_RECOGNITION_LOW_SOUND_ERROR;
import static com.chattylabs.sdk.android.voice.VoiceInteractionComponent.VOICE_RECOGNITION_NO_SOUND_ERROR;
import static com.chattylabs.sdk.android.voice.VoiceInteractionComponent.VOICE_RECOGNITION_RETRY_ERROR;
import static com.chattylabs.sdk.android.voice.VoiceInteractionComponent.VOICE_RECOGNITION_STOPPED_TOO_EARLY_ERROR;
import static com.chattylabs.sdk.android.voice.VoiceInteractionComponent.VOICE_RECOGNITION_UNAVAILABLE_ERROR;
import static com.chattylabs.sdk.android.voice.VoiceInteractionComponent.VOICE_RECOGNITION_UNKNOWN_ERROR;
import static com.chattylabs.sdk.android.voice.VoiceInteractionComponent.VoiceRecognitionListeners;
import static com.chattylabs.sdk.android.voice.VoiceInteractionComponent.getVoiceRecognitionErrorType;
import static com.chattylabs.sdk.android.voice.VoiceInteractionComponent.selectMostConfidentResult;

final class AndroidSpeechRecognizer {
    private static final String TAG = Tag.make("AndroidSpeechRecognizer");
    private static final long LOCK_TIMEOUT = TimeUnit.SECONDS.toMillis(3);

    private final Application application;
    private final AudioManager audioManager;
    private final AndroidHandler mainHandler;
    private final Intent speechRecognizerIntent;
    private final ReentrantLock lock = new ReentrantLock();
    // States
    private int audioMode = AudioManager.MODE_CURRENT; // released
    private int dtmfVolume;
    private int systemVolume;
    private int ringVolume;
    private int alarmVolume;
    private int notifVolume;
    private int musicVolume;
    private int callVolume;
    private boolean bluetoothScoRequired; // released
    private boolean isScoReceiverRegistered; // released
    private boolean requestAudioFocusExclusive; // released
    private boolean speakerphoneOn; // released
    private boolean rmsDebug; // released
    private boolean isPhoneStateReceiverRegistered; // released
    private float noSoundThreshold; // released
    private float lowSoundThreshold; // released
    // Objects
    private AudioFocusRequest focusRequestExclusive;
    private SpeechRecognizer speechRecognizer;
    private SpeechRecognizerCreator recognizerCreator;
    private PhoneStateReceiver phoneStateReceiver = new PhoneStateReceiver();
    private ScoReceiver scoReceiver = new ScoReceiver();
    private ExecutorService executorService;
    private AudioAttributes.Builder audioAttributes = new AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setLegacyStreamType(AudioManager.STREAM_MUSIC);
    // Listener
    private final RecognitionAdapter recognitionListener = new RecognitionAdapter() {
        private int intents;
        private long elapsedTime;
        private Timer timeout;
        private TimerTask task;

        private void releaseTimeout() {
            if (timeout != null) {
                logger.w(TAG, "VOICE - releasing previous timeout - RecognitionAdapter");
                task.cancel();
                timeout.cancel();
                timeout = null;
                task = null;
            }
        }

        @Override
        public void startTimeout() {
            releaseTimeout();
            logger.w(TAG, "VOICE - started timeout - RecognitionAdapter");
            timeout = new Timer();
            task = new TimerTask() {
                @Override
                public void run() {
                    logger.w(TAG, "VOICE - reached timeout - RecognitionAdapter");
                    executorService.submit(() -> {
                        lock.lock();
                        try {
                            mainHandler.post(() -> {
                                speechRecognizer.stopListening();
                                executorService.submit(lock::unlock);
                            });
                        } catch (Exception e) {
                            lock.unlock();
                        }
                    });
                }
            };
            timeout.schedule(task, MIN_LISTENING_TIME * 3);
        }

        private void cleanup() {
            elapsedTime = System.currentTimeMillis();
            intents = 0;
            logger.v(TAG, "VOICE - cleanup elapsedTime & partial intents - RecognitionAdapter");
        }

        @Override
        public void reset() {
            releaseTimeout();
            abandonAudioFocusExclusive();
            unregisterReceivers();
            cleanup();
            super.setTryAgain(false);
            setOnError(null);
            setOnPartialResults(null);
            setOnResults(null);
            setOnMostConfidentResult(null);
            setOnReady(null);
            setSoundLevel(UNKNOWN);
        }

        @Override
        public void onReadyForSpeech(Bundle params) {
            if (!audioManager.isBluetoothScoOn()) requestAudioFocusExclusive();
            //resetVolumeForBeep();
            super.onReadyForSpeech(params);
        }

        @Override
        public void onError(int error) {
            logger.e(TAG, "VOICE - error: " + getVoiceRecognitionErrorType(error));
            // We consider 2 sec as timeout for non speech
            boolean stoppedTooEarly = (System.currentTimeMillis() - elapsedTime) < VoiceInteractionComponent.MIN_LISTENING_TIME;
            // Start checking for the error
            OnVoiceRecognitionErrorListener errorListener = getOnError();
            int soundLevel = getSoundLevel();
            logger.v(TAG, "VOICE - Sound Level: " + getSoundLevelAsString(soundLevel));
            // Restart the recognizer
            cancel();
            if (errorListener != null) {
                if (needRetry(error)) {
                    errorListener.execute(VOICE_RECOGNITION_UNAVAILABLE_ERROR, error);
                }
                else if (stoppedTooEarly) {
                    errorListener.execute(VOICE_RECOGNITION_STOPPED_TOO_EARLY_ERROR, error);
                }
                else if (soundLevel == NO_SOUND) {
                    errorListener.execute(VOICE_RECOGNITION_NO_SOUND_ERROR, error);
                }
                else if (soundLevel == LOW_SOUND) {
                    errorListener.execute(VOICE_RECOGNITION_LOW_SOUND_ERROR, error);
                }
                else if (intents > 0) {
                    errorListener.execute(VOICE_RECOGNITION_AFTER_PARTIALS_ERROR, error);
                }
                else if (isTryAgain()) {
                    errorListener.execute(error == SpeechRecognizer.ERROR_NO_MATCH ?
                                          VOICE_RECOGNITION_UNKNOWN_ERROR :
                                          VOICE_RECOGNITION_RETRY_ERROR, error);
                }
                else { // Restore VOICE
                    errorListener.execute(VOICE_RECOGNITION_UNKNOWN_ERROR, error);
                }
            }
        }

        @Override
        public void onResults(Bundle results) {
            releaseTimeout();
            if (getOnResults() == null && getOnMostConfidentResult() == null) return;
            List<String> textResults = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            float[] confidences = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES);
            if (textResults != null && (textResults.size() > 1 || (!textResults.isEmpty() && textResults.get(0).length() > 0))) {
                OnVoiceRecognitionResultsListener resultsListener = getOnResults();
                OnVoiceRecognitionMostConfidentResultListener mostConfidentResult = getOnMostConfidentResult();
                reset();
                if (resultsListener != null) {
                    logger.v(TAG, "VOICE - results: " + textResults);
                    resultsListener.execute(textResults, confidences);
                }
                if (mostConfidentResult != null) {
                    String result = selectMostConfidentResult(textResults, confidences);
                    logger.v(TAG, "VOICE - confident result: " + result);
                    mostConfidentResult.execute(result);
                }
            }
            else {
                logger.e(TAG, "VOICE - NO results");
                OnVoiceRecognitionErrorListener listener = getOnError();
                reset();
                if (listener != null) listener.execute(VOICE_RECOGNITION_EMPTY_RESULTS_ERROR, -1);
            }
        }

        @Override
        public void onPartialResults(Bundle partialResults) {
            releaseTimeout();
            intents++;
            OnVoiceRecognitionPartialResultsListener listener = getOnPartialResults();
            if (listener == null) return;
            List<String> textResults = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            float[] confidences = partialResults.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES);
            if (textResults != null && (textResults.size() > 1 || (!textResults.isEmpty() && textResults.get(0).length() > 0))) {
                logger.v(TAG, "VOICE - partial results: " + textResults);
                listener.execute(textResults, confidences);
            }
        }

        private boolean needRetry(int error) {
            switch (error) {
                case SpeechRecognizer.ERROR_AUDIO:
                case SpeechRecognizer.ERROR_NETWORK:
                case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                case SpeechRecognizer.ERROR_SERVER:
                    return true;
                default:
                    return false;
            }
        }
    };
    private ILogger logger;

    AndroidSpeechRecognizer(Application application, ILogger logger, SpeechRecognizerCreator recognizerCreator) {
        this.release();
        this.executorService = Executors.newSingleThreadExecutor();
        this.application = application;
        this.logger = logger;
        this.audioManager = (AudioManager) application.getSystemService(Context.AUDIO_SERVICE);
        this.mainHandler = new AndroidHandlerImpl(Looper.getMainLooper());
        this.speechRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        this.speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        this.speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, application.getPackageName());
        this.speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        this.speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L);
        this.recognizerCreator = recognizerCreator;
    }

    public void release() {
        if (mainHandler != null) {
            mainHandler.removeCallbacksAndMessages(null);
            executorService.submit(lock::unlock);
        }
        setRmsDebug(false);
        setNoSoundThreshold(0);
        setLowSoundThreshold(0);
        setBluetoothScoRequired(false);
        logger.i(TAG, "VOICE - released");
    }

    public void stopAndSendCapturedSpeech() {
        executorService.submit(() -> {
            lock.lock();
            recognitionListener.reset();
            stopSco();
            if (speechRecognizer != null) {
                logger.w(TAG, "VOICE - do stop");
                try {
                    mainHandler.post(() -> {
                        speechRecognizer.stopListening();
                        executorService.submit(lock::unlock);
                    });
                } catch (Exception e) {
                    lock.unlock();
                }
            } else {
                lock.unlock();
            }
        });
    }

    public void cancel() {
        recognitionListener.reset();
        if (speechRecognizer != null) {
            logger.w(TAG, "VOICE - do cancel");
            speechRecognizer.setRecognitionListener(null);
            speechRecognizer.cancel();
        }
    }

    public void shutdown() {
        executorService.submit(() -> {
            lock.lock();
            logger.w(TAG, "VOICE - shutting down");
            recognitionListener.reset();
            stopSco();
            // Destroy current SpeechRecognizer
            try {
                mainHandler.post(() -> {
                    try {
                        if (speechRecognizer != null) {
                            speechRecognizer.setRecognitionListener(null);
                            speechRecognizer.destroy();
                            speechRecognizer = null;
                            logger.v(TAG, "VOICE - speechRecognizer destroyed");
                        }
                    }
                    catch (Exception ignore) {}
                    finally {
                        // Release and reset all resources & lock
                        release();
                    }
                });
            } catch (Exception e) {
                lock.unlock();
            }
        });
    }

    public void start(VoiceRecognitionListeners... listeners) {
        logger.i(TAG, "VOICE - start conversation");
        handleListeners(listeners);
        // Register for incoming calls
        registerPhoneStateReceiver();
        if (isBluetoothScoRequired() && !audioManager.isBluetoothScoOn()) {
            // Sco Listener
            OnScoListener listener = new OnScoListener() {
                @Override
                public void onConnected() {
                    if (audioManager.isBluetoothScoOn()) {
                        logger.w(TAG, "VOICE - Sco onConnected");
                    }
                    startListening();
                }

                @Override
                public void onDisconnected() {
                    logger.w(TAG, "VOICE - Sco onDisconnected");
                    if (audioManager.isBluetoothScoOn()) {
                        logger.w(TAG, "VOICE - shutdown from Sco");
                        shutdown();
                    }
                }
            };
            registerScoReceiver(listener);
            startSco();
        }
        else {
            startListening();
        }
    }

    private void startListening() {
        executorService.submit(() -> {
            lock.lock();
            try {
                mainHandler.post(() -> {
                    if (speechRecognizer == null) {
                        speechRecognizer = recognizerCreator.create();
                        logger.v(TAG, "VOICE - created");
                    }
                    recognitionListener.startTimeout();
                    recognitionListener.setRmsDebug(rmsDebug);
                    if (noSoundThreshold > 0) recognitionListener.setNoSoundThreshold(noSoundThreshold);
                    if (lowSoundThreshold > 0) recognitionListener.setLowSoundThreshold(lowSoundThreshold);
                    logger.i(TAG, "VOICE - start listening");
                    speechRecognizer.setRecognitionListener(recognitionListener);
                    //adjustVolumeForBeep();
                    speechRecognizer.startListening(speechRecognizerIntent);
                    executorService.submit(lock::unlock);
                });
            } catch (Exception e) {
                lock.unlock();
            }
        });
    }

    private void registerPhoneStateReceiver() {
        if (!isPhoneStateReceiverRegistered) {
            logger.v(TAG, "VOICE - register for phone receiver");
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

    private void registerScoReceiver(OnScoListener onScoListener) {
        scoReceiver.setListener(onScoListener);
        if (!isScoReceiverRegistered) {
            logger.v(TAG, "VOICE - register sco receiver");
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
        if (audioManager.isBluetoothScoAvailableOffCall() && !audioManager.isBluetoothScoOn()) {
            audioManager.setBluetoothScoOn(true);
            audioManager.startBluetoothSco();
            logger.v(TAG, "VOICE - start bluetooth sco");
        }
    }

    private void stopSco() {
        if (audioManager.isBluetoothScoAvailableOffCall() && audioManager.isBluetoothScoOn()) {
            audioManager.setBluetoothScoOn(false);
            audioManager.stopBluetoothSco();
            logger.v(TAG, "VOICE - stop bluetooth sco");
        }
    }

    private void handleListeners(VoiceRecognitionListeners... listeners) {
        recognitionListener.reset();
        if (listeners != null && listeners.length > 0) {
            for (VoiceRecognitionListeners item : listeners) {
                if (item instanceof OnVoiceRecognitionReadyListener) {
                    recognitionListener.setOnReady((OnVoiceRecognitionReadyListener) item);
                }
                else if (item instanceof OnVoiceRecognitionResultsListener) {
                    recognitionListener.setOnResults((OnVoiceRecognitionResultsListener) item);
                }
                else if (item instanceof OnVoiceRecognitionMostConfidentResultListener) {
                    recognitionListener.setOnMostConfidentResult((OnVoiceRecognitionMostConfidentResultListener) item);
                }
                else if (item instanceof OnVoiceRecognitionPartialResultsListener) {
                    recognitionListener.setOnPartialResults((OnVoiceRecognitionPartialResultsListener) item);
                }
                else if (item instanceof OnVoiceRecognitionErrorListener) {
                    recognitionListener.setOnError((OnVoiceRecognitionErrorListener) item);
                }
            }
        }
    }

    private boolean isBluetoothScoRequired() {
        return bluetoothScoRequired;
    }

    public void setBluetoothScoRequired(boolean bluetoothScoRequired) {
        this.bluetoothScoRequired = bluetoothScoRequired;
    }

    public void setTryAgain(boolean tryAgain) {
        this.recognitionListener.setTryAgain(tryAgain);
    }

    public void setPartialResults(boolean partial) {
        this.speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, partial);
    }

    public void setRmsDebug(boolean rmsDebug) {
        this.rmsDebug = rmsDebug;
    }

    public void setNoSoundThreshold(float maxValue) {
        this.noSoundThreshold = maxValue;
    }

    public void setLowSoundThreshold(float maxValue) {
        this.lowSoundThreshold = maxValue;
    }

    private void adjustVolumeForBeep() {
        // Volume
        dtmfVolume = audioManager.getStreamVolume(AudioManager.STREAM_DTMF);
        audioManager.setStreamVolume(AudioManager.STREAM_DTMF, audioManager.getStreamMaxVolume(AudioManager.STREAM_DTMF), 0);
        systemVolume = audioManager.getStreamVolume(AudioManager.STREAM_SYSTEM);
        audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, audioManager.getStreamMaxVolume(AudioManager.STREAM_SYSTEM), 0);
        ringVolume = audioManager.getStreamVolume(AudioManager.STREAM_RING);
        audioManager.setStreamVolume(AudioManager.STREAM_RING, audioManager.getStreamMaxVolume(AudioManager.STREAM_RING), 0);
        alarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM);
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0);
        notifVolume = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION);
        audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, audioManager.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION), 0);
        musicVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0);
        callVolume = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL);
        audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL), 0);
    }

    private void resetVolumeForBeep() {
        // Volume
        audioManager.setStreamVolume(AudioManager.STREAM_DTMF, dtmfVolume, 0);
        audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, systemVolume, 0);
        audioManager.setStreamVolume(AudioManager.STREAM_RING, ringVolume, 0);
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, alarmVolume, 0);
        audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, notifVolume, 0);
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, musicVolume, 0);
        audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, callVolume, 0);
    }

    private void setAudioMode() {
        audioMode = audioManager.getMode();
        audioManager.setMode(AudioManager.MODE_NORMAL); // We put this mode because when over Sco we never gain focus!
    }

    private void unsetAudioMode() {
        audioManager.setMode(audioMode);
    }

    private void abandonAudioFocusExclusive() {
        if (requestAudioFocusExclusive) {
            logger.v(TAG, "VOICE - abandon Audio Focus Exclusive");
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N_MR1) {
                audioManager.abandonAudioFocus(null);
            }
            else {
                audioManager.abandonAudioFocusRequest(focusRequestExclusive);
            }
            unsetAudioMode();
            requestAudioFocusExclusive = false;
        }
    }

    private void requestAudioFocusExclusive() {
        if (!requestAudioFocusExclusive) {
            logger.v(TAG, "VOICE - request Audio Focus Exclusive");
            setAudioMode();
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N_MR1) {
                //noinspection deprecation
                requestAudioFocusExclusive = AudioManager.AUDIOFOCUS_REQUEST_GRANTED ==
                                             audioManager.requestAudioFocus(
                                                     null,
                                                     AudioManager.STREAM_MUSIC,
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
}
