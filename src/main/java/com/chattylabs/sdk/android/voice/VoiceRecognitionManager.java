package com.chattylabs.sdk.android.voice;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import com.chattylabs.sdk.android.core.Tag;
import com.chattylabs.sdk.android.core.internal.android.AndroidHandler;
import com.chattylabs.sdk.android.core.internal.android.AndroidHandlerImpl;
import com.chattylabs.sdk.android.voice.VoiceInteractionComponent.SpeechRecognizerCreator;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

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

final class VoiceRecognitionManager {
    private static final String TAG = Tag.make(VoiceRecognitionManager.class);

    // States
    private int audioMode = AudioManager.MODE_CURRENT; // released
    private boolean bluetoothScoRequired; // released
    private boolean requestAudioFocusExclusive; // released
    private boolean speakerphoneOn; // released
    private boolean rmsDebug; // released


    //private final Application application;
    private final AudioManager audioManager;
    private final AndroidHandler mainHandler;
    private final Intent speechRecognizerIntent;
    private AudioFocusRequest focusRequestExclusive;
    private SpeechRecognizer speechRecognizer;
    private SpeechRecognizerCreator recognizerCreator;

    // Listener
    private final RecognitionAdapter recognitionListener = new RecognitionAdapter() {
        private int intents;
        private long elapsedTime;
        private Timer timeout;
        private TimerTask task;

        @Override
        public void releaseTimeout() {
            if (timeout != null) {
                Log.w(TAG, "VOICE releasing previous timeout");
                task.cancel();
                timeout.cancel();
                timeout = null;
                task = null;
            }
        }

        @Override
        public void startTimeout() {
            releaseTimeout();
            Log.w(TAG, "VOICE started timeout");
            timeout = new Timer();
            task = new TimerTask() {
                @Override
                public void run() {
                    Log.w(TAG, "VOICE reached timeout");
                    mainHandler.post(() -> speechRecognizer.stopListening());
                }
            };
            timeout.schedule(task, MIN_LISTENING_TIME * 3);
        }

        @Override
        public void reset() {
            cleanup();
            setTryAgain(false);
            setOnError(null);
            setOnPartialResults(null);
            setOnResults(null);
            setOnMostConfidentResult(null);
            setOnReady(null);
            setSoundLevel(UNKNOWN);
        }

        private void cleanup() {
            Log.v(TAG, "VOICE cleanup listener");
            releaseTimeout();
            elapsedTime = System.currentTimeMillis();
            intents = 0;
        }

        @Override
        public void onError(int error) {
            Log.e(TAG, "VOICE error: " + getVoiceRecognitionErrorType(error));
            // We consider 2 sec as timeout for non speech
            boolean stoppedTooEarly = (System.currentTimeMillis() - elapsedTime) < VoiceInteractionComponent.MIN_LISTENING_TIME;
            // Start checking for the error
            OnVoiceRecognitionErrorListener errorListener = getOnError();
            int soundLevel = getSoundLevel();
            Log.v(TAG, "Sound Level: " + soundLevel);
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
                else { // Restore TTS
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
            if (textResults != null && (textResults.size() > 1 || (textResults.size() > 0 && textResults.get(0).length() > 0))) {
                cleanup();
                if (getOnResults() != null) {
                    Log.v(TAG, "VOICE results: " + textResults);
                    getOnResults().execute(textResults, confidences);
                }
                if (getOnMostConfidentResult() != null) {
                    String result = selectMostConfidentResult(textResults, confidences);
                    Log.v(TAG, "VOICE confident result: " + result);
                    getOnMostConfidentResult().execute(result);
                }
            }
            else {
                Log.e(TAG, "VOICE NO results");
                cleanup();
                if (getOnError() != null) getOnError().execute(VOICE_RECOGNITION_EMPTY_RESULTS_ERROR, -1);
            }
        }

        @Override
        public void onPartialResults(Bundle partialResults) {
            releaseTimeout();
            intents++;
            if (getOnPartialResults() == null) return;
            List<String> textResults = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            float[] confidences = partialResults.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES);
            if (textResults != null && (textResults.size() > 1 || (textResults.size() > 0 && textResults.get(0).length() > 0))) {
                cleanup();
                Log.v(TAG, "VOICE partial results: " + textResults);
                getOnPartialResults().execute(textResults, confidences);
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

    VoiceRecognitionManager(Application application, SpeechRecognizerCreator recognizerCreator) {
        this.release();
        //this.application = application;
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
        Log.i(TAG, "VOICE release");
        if (mainHandler != null) {
            mainHandler.removeCallbacksAndMessages(null);
        }
        recognitionListener.reset();
        setRmsDebug(false);
        setBluetoothScoRequired(false);
    }

    public void stop() {
        recognitionListener.reset();
        if (speechRecognizer != null) {
            Log.w(TAG, "VOICE do stop");
            mainHandler.post(() -> speechRecognizer.stopListening());
        }
    }

    public void cancel() {
        recognitionListener.reset();
        if (speechRecognizer != null) {
            Log.w(TAG, "VOICE do cancel");
            speechRecognizer.setRecognitionListener(null);
            speechRecognizer.cancel();
        }
    }

    public void shutdown() {
        Log.w(TAG, "VOICE shutdown");
        recognitionListener.releaseTimeout();
        abandonAudioFocusExclusive();
        // Destroy current SpeechRecognizer
        mainHandler.post(() -> {
            try {
                if (speechRecognizer != null) {
                    speechRecognizer.setRecognitionListener(null);
                    speechRecognizer.destroy();
                    speechRecognizer = null;
                    Log.v(TAG, "VOICE destroyed");
                }
            }
            catch (Exception ignored) {}
            finally {
                // Release and reset all resources
                release();
            }
        });
    }

    public void start(VoiceRecognitionListeners... listeners) {
        Log.i(TAG, "VOICE - start conversation");
        handleListeners(listeners);
        requestAudioFocusExclusive();
        mainHandler.post(() -> {
            if (speechRecognizer == null) {
                speechRecognizer = recognizerCreator.create();
                Log.v(TAG, "VOICE created");
            }
            recognitionListener.startTimeout();
            recognitionListener.setRmsDebug(rmsDebug);
            Log.i(TAG, "VOICE start listening");
            speechRecognizer.setRecognitionListener(recognitionListener);
            speechRecognizer.startListening(speechRecognizerIntent);
        });
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

    public boolean isBluetoothScoRequired() {
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

    private void abandonAudioFocusExclusive() {
        if (requestAudioFocusExclusive) {
            Log.v(TAG, "VOICE abandon Audio Focus Exclusive");
            requestAudioFocusExclusive = false;
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

    private void requestAudioFocusExclusive() {
        if (!requestAudioFocusExclusive) {
            Log.v(TAG, "VOICE request Audio Focus Exclusive");
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
//                                                            isBluetoothScoRequired() ? AudioAttributes.USAGE_VOICE_COMMUNICATION
//                                                                                     : AudioAttributes.USAGE_MEDIA
                                                            AudioAttributes.USAGE_VOICE_COMMUNICATION
                                                    )
                                                    .setLegacyStreamType(getMainStreamType())
                                                    .build()).build();
                requestAudioFocusExclusive = AudioManager.AUDIOFOCUS_REQUEST_GRANTED == audioManager.requestAudioFocus(focusRequestExclusive);
            }
        }
    }

    private int getMainStreamType() {
        //return isBluetoothScoRequired() ? AudioManager.STREAM_VOICE_CALL : AudioManager.STREAM_MUSIC;
        return AudioManager.STREAM_VOICE_CALL;
    }
}
