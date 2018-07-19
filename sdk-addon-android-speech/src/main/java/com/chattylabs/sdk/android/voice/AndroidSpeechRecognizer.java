package com.chattylabs.sdk.android.voice;

import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import com.chattylabs.sdk.android.common.Tag;
import com.chattylabs.sdk.android.common.internal.ILogger;
import com.chattylabs.sdk.android.common.internal.android.AndroidHandler;
import com.chattylabs.sdk.android.common.internal.android.AndroidHandlerImpl;
import com.chattylabs.sdk.android.voice.ConversationalFlowComponent.SpeechRecognizerCreator;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.MIN_VOICE_RECOGNITION_TIME_LISTENING;
import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.OnRecognizerError;
import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.OnRecognizerMostConfidentResult;
import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.OnRecognizerPartialResults;
import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.OnRecognizerReady;
import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.OnRecognizerResults;
import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.RECOGNIZER_AFTER_PARTIALS_ERROR;
import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.RECOGNIZER_EMPTY_RESULTS_ERROR;
import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.RECOGNIZER_LOW_SOUND_ERROR;
import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.RECOGNIZER_NO_SOUND_ERROR;
import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.RECOGNIZER_RETRY_ERROR;
import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.RECOGNIZER_STOPPED_TOO_EARLY_ERROR;
import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.RECOGNIZER_UNAVAILABLE_ERROR;
import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.RECOGNIZER_UNKNOWN_ERROR;
import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.RecognizerListenerContract;
import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.selectMostConfidentResult;

public final class AndroidSpeechRecognizer implements ConversationalFlowComponent.SpeechRecognizer {
    private static final String TAG = Tag.make("AndroidSpeechRecognizer");

    private final ReentrantLock lock = new ReentrantLock();

    // States
    private boolean rmsDebug; // released
    private float noSoundThreshold; // released
    private float lowSoundThreshold; // released

    // Resources
    private final Application application;
    private final VoiceConfig config;
    private final AndroidHandler mainHandler;
    private final AndroidAudioHandler audioHandler;
    private final BluetoothSco bluetoothSco;
    private final Intent speechRecognizerIntent;
    private final SpeechRecognizerCreator recognizerCreator;
    private final ExecutorService executorService;
    private SpeechRecognizer speechRecognizer;

    // Listener
    private final AndroidSpeechRecognitionAdapter recognitionListener = new AndroidSpeechRecognitionAdapter() {
        private int intents;
        private long elapsedTime;
        private Timer timeout;
        private TimerTask task;

        private void releaseTimeout() {
            if (timeout != null) {
                logger.w(TAG, "ANDROID VOICE - releasing previous timeout");
                task.cancel();
                timeout.cancel();
                timeout = null;
                task = null;
            }
        }

        @Override
        public void startTimeout() {
            releaseTimeout();
            logger.w(TAG, "ANDROID VOICE - started timeout");
            timeout = new Timer();
            task = new TimerTask() {
                @Override
                public void run() {
                    logger.w(TAG, "ANDROID VOICE - reached timeout");
                    executorService.submit(() -> {
                        lock.lock();
                        try {
                            mainHandler.post(() -> {
                                if (speechRecognizer != null) speechRecognizer.stopListening();
                                executorService.submit(lock::unlock);
                            });
                        } catch (Exception e) {
                            lock.unlock();
                        }
                    });
                }
            };
            timeout.schedule(task, MIN_VOICE_RECOGNITION_TIME_LISTENING * 3);
        }

        private void cleanup() {
            elapsedTime = System.currentTimeMillis();
            intents = 0;
            logger.v(TAG, "ANDROID VOICE - cleanup elapsedTime & partial intents");
        }

        @Override
        public void reset() {
            releaseTimeout();
            audioHandler.abandonAudioFocus();
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
            if (!bluetoothSco.isBluetoothScoOn()) {
                audioHandler.requestAudioFocus(config.isAudioExclusiveRequiredForRecognizer());
            }
            //resetVolumeForBeep();
            super.onReadyForSpeech(params);
        }

        @Override
        public void onError(int error) {
            logger.e(TAG, "ANDROID VOICE - error: " + getErrorType(error));
            // We consider 2 sec as timeout for non speech
            boolean stoppedTooEarly = (System.currentTimeMillis() - elapsedTime) < ConversationalFlowComponent.MIN_VOICE_RECOGNITION_TIME_LISTENING;
            // Start checking for the error
            OnRecognizerError errorListener = getOnError();
            int soundLevel = getSoundLevel();
            logger.v(TAG, "ANDROID VOICE - Sound Level: " + getSoundLevelAsString(soundLevel));
            // Restart the recognizer
            cancel();
            if (errorListener != null) {
                if (needRetry(error)) {
                    errorListener.execute(RECOGNIZER_UNAVAILABLE_ERROR, error);
                }
                else if (stoppedTooEarly) {
                    errorListener.execute(RECOGNIZER_STOPPED_TOO_EARLY_ERROR, error);
                }
                else if (soundLevel == NO_SOUND) {
                    errorListener.execute(RECOGNIZER_NO_SOUND_ERROR, error);
                }
                else if (soundLevel == LOW_SOUND) {
                    errorListener.execute(RECOGNIZER_LOW_SOUND_ERROR, error);
                }
                else if (intents > 0) {
                    errorListener.execute(RECOGNIZER_AFTER_PARTIALS_ERROR, error);
                }
                else if (isTryAgain()) {
                    errorListener.execute(error == SpeechRecognizer.ERROR_NO_MATCH ?
                            RECOGNIZER_UNKNOWN_ERROR :
                            RECOGNIZER_RETRY_ERROR, error);
                }
                else { // Restore ANDROID VOICE
                    errorListener.execute(RECOGNIZER_UNKNOWN_ERROR, error);
                }
            }
        }

        @Override
        public void onResults(Bundle results) {
            releaseTimeout();
            OnRecognizerResults resultsListener = getOnResults();
            OnRecognizerMostConfidentResult mostConfidentResult = getOnMostConfidentResult();
            if (resultsListener == null && mostConfidentResult == null) return;
            List<String> textResults = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            float[] confidences = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES);
            if (textResults != null && (textResults.size() > 1 || (!textResults.isEmpty() && textResults.get(0).length() > 0))) {
                reset();
                if (resultsListener != null) {
                    logger.v(TAG, "ANDROID VOICE - results: " + textResults);
                    resultsListener.execute(textResults, confidences);
                }
                if (mostConfidentResult != null) {
                    String result = selectMostConfidentResult(textResults, confidences);
                    logger.v(TAG, "ANDROID VOICE - confident result: " + result);
                    mostConfidentResult.execute(result);
                }
            }
            else {
                logger.e(TAG, "ANDROID VOICE - NO results");
                OnRecognizerError listener = getOnError();
                reset();
                if (listener != null) listener.execute(RECOGNIZER_EMPTY_RESULTS_ERROR, -1);
            }
        }

        @Override
        public void onPartialResults(Bundle partialResults) {
            releaseTimeout();
            intents++;
            OnRecognizerPartialResults listener = getOnPartialResults();
            if (listener == null) return;
            List<String> textResults = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            float[] confidences = partialResults.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES);
            if (textResults != null && (textResults.size() > 1 || (!textResults.isEmpty() && textResults.get(0).length() > 0))) {
                logger.v(TAG, "ANDROID VOICE - partial results: " + textResults);
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

    // Log stuff
    private ILogger logger;

    AndroidSpeechRecognizer(Application application, VoiceConfig config,
                            AndroidAudioHandler audioHandler, BluetoothSco bluetoothSco,
                            SpeechRecognizerCreator recognizerCreator, ILogger logger) {
        this.application = application;
        this.config = config;
        this.audioHandler = audioHandler;
        this.bluetoothSco = bluetoothSco;
        this.logger = logger;
        this.executorService = Executors.newSingleThreadExecutor();
        this.release();
        this.mainHandler = new AndroidHandlerImpl(Looper.getMainLooper());
        this.speechRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        this.speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        this.speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, application.getPackageName());
        this.speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        this.speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L);
        this.recognizerCreator = recognizerCreator;
    }

    private void release() {
        if (mainHandler != null) {
            mainHandler.removeCallbacksAndMessages(null);
            executorService.submit(lock::unlock);
        }
        setRmsDebug(false);
        setNoSoundThreshold(0);
        setLowSoundThreshold(0);
        logger.i(TAG, "ANDROID VOICE - released");
    }

    @Override
    public void stop() {
        executorService.submit(() -> {
            lock.lock();
            recognitionListener.reset();
            bluetoothSco.stopSco();
            if (speechRecognizer != null) {
                logger.w(TAG, "ANDROID VOICE - do stop");
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

    @Override
    public void cancel() {
        recognitionListener.reset();
        if (speechRecognizer != null) {
            logger.w(TAG, "ANDROID VOICE - do cancel");
            speechRecognizer.setRecognitionListener(null);
            speechRecognizer.cancel();
        }
    }

    @Override
    public void shutdown() {
        executorService.submit(() -> {
            lock.lock();
            logger.w(TAG, "ANDROID VOICE - shutting down");
            recognitionListener.reset();
            bluetoothSco.stopSco();
            // Destroy current SpeechRecognizer
            try {
                mainHandler.post(() -> {
                    try {
                        if (speechRecognizer != null) {
                            speechRecognizer.setRecognitionListener(null);
                            speechRecognizer.destroy();
                            speechRecognizer = null;
                            logger.v(TAG, "ANDROID VOICE - speechRecognizer destroyed");
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

    @Override
    public void listen(RecognizerListenerContract... listeners) {
        logger.i(TAG, "ANDROID VOICE - start listening");
        handleListeners(listeners);
        // Check whether Sco is connected or required
        logger.i(TAG, "ANDROID VOICE - is bluetooth Sco required: " +
                Boolean.toString(config.isBluetoothScoRequired()));
        if (config.isBluetoothScoRequired() && !bluetoothSco.isBluetoothScoOn()) {
            // Sco Listener
            BluetoothScoListener listener = new BluetoothScoListener() {
                @Override
                public void onConnected() {
                    logger.w(TAG, "ANDROID VOICE - Sco onConnected");
                    startListening();
                }

                @Override
                public void onDisconnected() {
                    logger.w(TAG, "ANDROID VOICE - Sco onDisconnected");
                    if (bluetoothSco.isBluetoothScoOn()) {
                        logger.w(TAG, "ANDROID VOICE - shutdown from Sco");
                        shutdown();
                    }
                }
            };
            // Start Bluetooth Sco
            bluetoothSco.startSco(listener);
            logger.v(TAG, "ANDROID VOICE - waiting for bluetooth sco connection");
        }
        else {
            logger.v(TAG, "ANDROID VOICE - bluetooth sco is: " + (bluetoothSco.isBluetoothScoOn() ? "on" : "off"));
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
                        logger.v(TAG, "ANDROID VOICE - created");
                    }
                    recognitionListener.startTimeout();
                    recognitionListener.setRmsDebug(rmsDebug);
                    if (noSoundThreshold > 0) recognitionListener.setNoSoundThreshold(noSoundThreshold);
                    if (lowSoundThreshold > 0) recognitionListener.setLowSoundThreshold(lowSoundThreshold);
                    logger.i(TAG, "ANDROID VOICE - start listening");
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

    private void handleListeners(RecognizerListenerContract... listeners) {
        recognitionListener.reset();
        if (listeners != null && listeners.length > 0) {
            for (RecognizerListenerContract item : listeners) {
                if (item instanceof OnRecognizerReady) {
                    recognitionListener.setOnReady((OnRecognizerReady) item);
                }
                else if (item instanceof OnRecognizerResults) {
                    recognitionListener.setOnResults((OnRecognizerResults) item);
                }
                else if (item instanceof OnRecognizerMostConfidentResult) {
                    recognitionListener.setOnMostConfidentResult((OnRecognizerMostConfidentResult) item);
                }
                else if (item instanceof OnRecognizerPartialResults) {
                    recognitionListener.setOnPartialResults((OnRecognizerPartialResults) item);
                }
                else if (item instanceof OnRecognizerError) {
                    recognitionListener.setOnError((OnRecognizerError) item);
                }
            }
        }
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

    public static String getErrorType(int error) {
        switch (error) {
            case android.speech.SpeechRecognizer.ERROR_AUDIO:
                return "ERROR_AUDIO";
            case android.speech.SpeechRecognizer.ERROR_CLIENT:
                return "ERROR_CLIENT";
            case android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                return "ERROR_INSUFFICIENT_PERMISSIONS";
            case android.speech.SpeechRecognizer.ERROR_NETWORK:
                return "ERROR_NETWORK";
            case android.speech.SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                return "ERROR_NETWORK_TIMEOUT";
            case android.speech.SpeechRecognizer.ERROR_NO_MATCH:
                return "ERROR_NO_MATCH";
            case android.speech.SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                return "ERROR_RECOGNIZER_BUSY";
            case android.speech.SpeechRecognizer.ERROR_SERVER:
                return "ERROR_SERVER";
            case android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                return "ERROR_SPEECH_TIMEOUT";
            default:
                return "ERROR_UNKNOWN";
        }
    }
}
