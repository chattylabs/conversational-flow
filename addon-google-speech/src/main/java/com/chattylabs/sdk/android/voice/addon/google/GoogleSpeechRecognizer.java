package com.chattylabs.sdk.android.voice.addon.google;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.speech.SpeechRecognizer;
import android.text.TextUtils;

import com.chattylabs.sdk.android.common.Tag;
import com.chattylabs.sdk.android.common.internal.ILogger;
import com.chattylabs.sdk.android.voice.AndroidAudioHandler;
import com.chattylabs.sdk.android.voice.BluetoothSco;
import com.chattylabs.sdk.android.voice.BluetoothScoListener;
import com.chattylabs.sdk.android.voice.VoiceConfig;
import com.chattylabs.sdk.android.voice.VoiceInteractionComponent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

import static com.chattylabs.sdk.android.voice.VoiceInteractionComponent.MIN_VOICE_RECOGNITION_TIME_LISTENING;
import static com.chattylabs.sdk.android.voice.VoiceInteractionComponent.RECOGNIZER_AFTER_PARTIALS_ERROR;
import static com.chattylabs.sdk.android.voice.VoiceInteractionComponent.RECOGNIZER_EMPTY_RESULTS_ERROR;
import static com.chattylabs.sdk.android.voice.VoiceInteractionComponent.RECOGNIZER_STOPPED_TOO_EARLY_ERROR;
import static com.chattylabs.sdk.android.voice.VoiceInteractionComponent.RECOGNIZER_UNKNOWN_ERROR;
import static com.chattylabs.sdk.android.voice.VoiceInteractionComponent.selectMostConfidentResult;

public class GoogleSpeechRecognizer implements VoiceInteractionComponent.SpeechRecognizer {
    private static final String TAG = Tag.make("GoogleSpeechRecognizer");

    private final ReentrantLock lock = new ReentrantLock();

    // Resources
    private final Application application;
    private final VoiceConfig config;
    private final AndroidAudioHandler audioHandler;
    private final BluetoothSco bluetoothSco;
    private final ExecutorService executorService;
    private GoogleSpeechService mSpeechService;

    // Log stuff
    private ILogger logger;

    private VoiceRecorder mVoiceRecorder;
    private final VoiceRecorder.Callback mVoiceCallback = new VoiceRecorder.Callback() {

        @Override
        public void onVoiceStart() {
            if (mSpeechService != null) {
                recognitionListener.onBeginningOfSpeech();
                mSpeechService.startRecognizing(mVoiceRecorder.getSampleRate());
            }
        }

        @Override
        public void onVoice(byte[] data, int size) {
            if (mSpeechService != null) {
                mSpeechService.recognize(data, size);
            }
        }

        @Override
        public void onVoiceError(int error) {
            if (mSpeechService != null) {
                recognitionListener.onError(error);
            }
        }

        @Override
        public void onVoiceEnd() {
            if (mSpeechService != null) {
                stopVoiceRecorder();
                mSpeechService.finishRecognizing();
                recognitionListener.onEndOfSpeech();
            }
        }

    };

    private final GoogleSpeechService.Listener mSpeechServiceListener =
            new GoogleSpeechService.Listener() {

                @Override
                public void onSpeechReady() {
                    recognitionListener.onReadyForSpeech(null);
                }

                @Override
                public void onSpeechRecognized(final String text, final boolean isFinal) {
                    if (!TextUtils.isEmpty(text)) {
                        Bundle bundle = new Bundle();
                        bundle.putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION,
                                (ArrayList<String>) Collections.singletonList(text));
                        bundle.putFloatArray(SpeechRecognizer.CONFIDENCE_SCORES, new float[]{1});
                        if (isFinal) {
                            mVoiceCallback.onVoiceEnd();
                            recognitionListener.onResults(bundle);
                        } else {
                            recognitionListener.onPartialResults(bundle);
                        }
                    }
                }
            };

    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder binder) {
            mSpeechService = GoogleSpeechService.from(binder);
            mSpeechService.addListener(mSpeechServiceListener);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mSpeechService = null;
        }

    };

    // Listener
    private final GoogleSpeechRecognitionAdapter recognitionListener = new GoogleSpeechRecognitionAdapter() {
        private int intents;
        private long elapsedTime;
        private Timer timeout;
        private TimerTask task;

        private void releaseTimeout() {
            if (timeout != null) {
                logger.w(TAG, "GOOGLE VOICE - releasing previous timeout");
                task.cancel();
                timeout.cancel();
                timeout = null;
                task = null;
            }
        }

        @Override
        public void startTimeout() {
            releaseTimeout();
            logger.w(TAG, "GOOGLE VOICE - started timeout");
            timeout = new Timer();
            task = new TimerTask() {
                @Override
                public void run() {
                    logger.w(TAG, "GOOGLE VOICE - reached timeout");
                    executorService.submit(() -> {
                        lock.lock();
                        try {
                            mVoiceCallback.onVoiceEnd();
                            lock.unlock();
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
            logger.v(TAG, "GOOGLE VOICE - cleanup elapsedTime & partial intents");
        }

        @Override
        public void reset() {
            releaseTimeout();
            audioHandler.abandonAudioFocus();
            cleanup();
            //super.setTryAgain(false);
            setOnError(null);
            setOnPartialResults(null);
            setOnResults(null);
            setOnMostConfidentResult(null);
            setOnReady(null);
            //setSoundLevel(UNKNOWN);
        }

        @Override
        public void onReadyForSpeech(Bundle params) {
            if (!bluetoothSco.isBluetoothScoOn()) {
                audioHandler.requestAudioFocus(config.isAudioExclusiveRequiredForRecognizer());
            }
            //beep();
            super.onReadyForSpeech(params);
        }

        @Override
        public void onError(int error) {
            //logger.e(TAG, "ANDROID VOICE - error: " + getErrorType(error));
            // We consider 2 sec as timeout for non speech
            boolean stoppedTooEarly = (System.currentTimeMillis() - elapsedTime) < VoiceInteractionComponent.MIN_VOICE_RECOGNITION_TIME_LISTENING;
            // Start checking for the error
            VoiceInteractionComponent.OnRecognizerError errorListener = getOnError();
            //int soundLevel = getSoundLevel();
            //logger.v(TAG, "ANDROID VOICE - Sound Level: " + getSoundLevelAsString(soundLevel));
            // Restart the recognizer
            cancel();
            if (errorListener != null) {
//                if (needRetry(error)) {
//                    errorListener.execute(RECOGNIZER_UNAVAILABLE_ERROR, error);
//                }
//                else
                    if (stoppedTooEarly) {
                    errorListener.execute(RECOGNIZER_STOPPED_TOO_EARLY_ERROR, error);
                }
//                else if (soundLevel == NO_SOUND) {
//                    errorListener.execute(RECOGNIZER_NO_SOUND_ERROR, error);
//                }
//                else if (soundLevel == LOW_SOUND) {
//                    errorListener.execute(RECOGNIZER_LOW_SOUND_ERROR, error);
//                }
                else if (intents > 0) {
                    errorListener.execute(RECOGNIZER_AFTER_PARTIALS_ERROR, error);
                }
//                else if (isTryAgain()) {
//                    errorListener.execute(error == SpeechRecognizer.ERROR_NO_MATCH ?
//                            RECOGNIZER_UNKNOWN_ERROR :
//                            RECOGNIZER_RETRY_ERROR, error);
//                }
                else { // Restore ANDROID VOICE
                    errorListener.execute(RECOGNIZER_UNKNOWN_ERROR, error);
                }
            }
        }

        @Override
        public void onResults(Bundle results) {
            releaseTimeout();
            VoiceInteractionComponent.OnRecognizerResults resultsListener = getOnResults();
            VoiceInteractionComponent.OnRecognizerMostConfidentResult mostConfidentResult = getOnMostConfidentResult();
            if (resultsListener == null && mostConfidentResult == null) return;
            List<String> textResults = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            float[] confidences = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES);
            if (textResults != null && (textResults.size() > 1 || (!textResults.isEmpty() && textResults.get(0).length() > 0))) {
                reset();
                if (resultsListener != null) {
                    logger.v(TAG, "GOOGLE VOICE - results: " + textResults);
                    resultsListener.execute(textResults, confidences);
                }
                if (mostConfidentResult != null) {
                    String result = selectMostConfidentResult(textResults, confidences);
                    logger.v(TAG, "GOOGLE VOICE - confident result: " + result);
                    mostConfidentResult.execute(result);
                }
            }
            else {
                logger.e(TAG, "GOOGLE VOICE - NO results");
                VoiceInteractionComponent.OnRecognizerError listener = getOnError();
                reset();
                if (listener != null) listener.execute(RECOGNIZER_EMPTY_RESULTS_ERROR, -1);
            }
        }

        @Override
        public void onPartialResults(Bundle partialResults) {
            releaseTimeout();
            intents++;
            VoiceInteractionComponent.OnRecognizerPartialResults listener = getOnPartialResults();
            if (listener == null) return;
            List<String> textResults = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            float[] confidences = partialResults.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES);
            if (textResults != null && (textResults.size() > 1 || (!textResults.isEmpty() && textResults.get(0).length() > 0))) {
                logger.v(TAG, "ANDROID VOICE - partial results: " + textResults);
                listener.execute(textResults, confidences);
            }
        }
    };

    public GoogleSpeechRecognizer(Application application,
                                  VoiceConfig config,
                                  AndroidAudioHandler audioHandler,
                                  BluetoothSco bluetoothSco, ILogger logger) {
        this.application = application;
        this.config = config;
        this.audioHandler = audioHandler;
        this.bluetoothSco = bluetoothSco;
        this.logger = logger;
        this.executorService = Executors.newSingleThreadExecutor();
    }

    @Override
    public <T extends VoiceInteractionComponent.RecognizerListenerContract> void listen(
             T... listeners) {
        logger.i(TAG, "GOOGLE VOICE - start listening");
        handleListeners(listeners);
        checkForBluetoothScoRequired(this::startListening);
    }

    private void checkInitialized() {
        // Prepare Cloud Speech API
        application.bindService(new Intent(application, GoogleSpeechService.class),
                mServiceConnection, Context.BIND_AUTO_CREATE);
    }

    private void checkForBluetoothScoRequired(Runnable starter) {
        logger.i(TAG, "GOOGLE VOICE - is bluetooth Sco required: " +
                Boolean.toString(config.isBluetoothScoRequired()));
        if (config.isBluetoothScoRequired() && !bluetoothSco.isBluetoothScoOn()) {
            // Sco Listener
            BluetoothScoListener listener = new BluetoothScoListener() {
                @Override
                public void onConnected() {
                    logger.w(TAG, "GOOGLE VOICE - Sco onConnected");
                    starter.run();
                }

                @Override
                public void onDisconnected() {
                    logger.w(TAG, "GOOGLE VOICE - Sco onDisconnected");
                    if (bluetoothSco.isBluetoothScoOn()) {
                        logger.w(TAG, "GOOGLE VOICE - shutdown from Sco");
                        shutdown();
                    }
                }
            };
            // Start Bluetooth Sco
            bluetoothSco.startSco(listener);
            logger.v(TAG, "GOOGLE VOICE - waiting for bluetooth sco connection");
        }
        else {
            logger.v(TAG, "GOOGLE VOICE - bluetooth sco is: " + (bluetoothSco.isBluetoothScoOn() ? "on" : "off"));
            starter.run();
        }
    }

    private void startListening() {
        executorService.submit(() -> {
            lock.lock();
            try {
                checkInitialized();
                recognitionListener.startTimeout();
//                    recognitionListener.setRmsDebug(rmsDebug);
//                    if (noSoundThreshold > 0) recognitionListener.setNoSoundThreshold(noSoundThreshold);
//                    if (lowSoundThreshold > 0) recognitionListener.setLowSoundThreshold(lowSoundThreshold);
//                    logger.i(TAG, "VOICE - start listening");
//                    speechRecognizer.setRecognitionListener(recognitionListener);
                startVoiceRecorder();
                lock.unlock();
//                });
            } catch (Exception e) {
                lock.unlock();
            }
        });
    }

    private void handleListeners(VoiceInteractionComponent.RecognizerListenerContract... listeners) {
        recognitionListener.reset();
        if (listeners != null && listeners.length > 0) {
            for (VoiceInteractionComponent.RecognizerListenerContract item : listeners) {
                if (item instanceof VoiceInteractionComponent.OnRecognizerReady) {
                    recognitionListener.setOnReady((VoiceInteractionComponent.OnRecognizerReady) item);
                }
                else if (item instanceof VoiceInteractionComponent.OnRecognizerResults) {
                    recognitionListener.setOnResults((VoiceInteractionComponent.OnRecognizerResults) item);
                }
                else if (item instanceof VoiceInteractionComponent.OnRecognizerMostConfidentResult) {
                    recognitionListener.setOnMostConfidentResult((VoiceInteractionComponent.OnRecognizerMostConfidentResult) item);
                }
                else if (item instanceof VoiceInteractionComponent.OnRecognizerPartialResults) {
                    recognitionListener.setOnPartialResults((VoiceInteractionComponent.OnRecognizerPartialResults) item);
                }
                else if (item instanceof VoiceInteractionComponent.OnRecognizerError) {
                    recognitionListener.setOnError((VoiceInteractionComponent.OnRecognizerError) item);
                }
            }
        }
    }

    private void startVoiceRecorder() {
        if (mVoiceRecorder != null) {
            mVoiceRecorder.stop();
        }
        mVoiceRecorder = new VoiceRecorder(mVoiceCallback);
        mVoiceRecorder.start();
    }

    private void stopVoiceRecorder() {
        if (mVoiceRecorder != null) {
            mVoiceRecorder.stop();
            mVoiceRecorder = null;
        }
    }

    @Override
    public void stop() {
        // Stop listening to voice
        stopVoiceRecorder();
    }

    @Override
    public void shutdown() {
        stop();

        // Stop Cloud Speech API
        mSpeechService.removeListener(mSpeechServiceListener);
        application.unbindService(mServiceConnection);
        mSpeechService = null;
    }

    @Override
    public void cancel() {

    }

    @Override
    public void setRmsDebug(boolean debug) {

    }

    @Override
    public void setNoSoundThreshold(float maxValue) {

    }

    @Override
    public void setLowSoundThreshold(float maxValue) {

    }
}
