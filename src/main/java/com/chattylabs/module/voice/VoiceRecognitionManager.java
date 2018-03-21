package com.chattylabs.module.voice;

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

import com.chattylabs.module.core.Tag;
import com.chattylabs.module.core.internal.android.AndroidHandler;
import com.chattylabs.module.core.internal.android.AndroidHandlerImpl;
import com.chattylabs.module.voice.VoiceInteractionComponent.SpeechRecognizerCreator;

import java.util.List;

import static com.chattylabs.module.voice.VoiceInteractionComponent.OnVoiceRecognitionErrorListener;
import static com.chattylabs.module.voice.VoiceInteractionComponent.OnVoiceRecognitionMostConfidentResultListener;
import static com.chattylabs.module.voice.VoiceInteractionComponent.OnVoiceRecognitionPartialResultsListener;
import static com.chattylabs.module.voice.VoiceInteractionComponent.OnVoiceRecognitionReadyListener;
import static com.chattylabs.module.voice.VoiceInteractionComponent.OnVoiceRecognitionResultsListener;
import static com.chattylabs.module.voice.VoiceInteractionComponent.VOICE_RECOGNITION_AFTER_PARTIALS_ERROR;
import static com.chattylabs.module.voice.VoiceInteractionComponent.VOICE_RECOGNITION_EMPTY_RESULTS_ERROR;
import static com.chattylabs.module.voice.VoiceInteractionComponent.VOICE_RECOGNITION_NO_MATCH_ERROR;
import static com.chattylabs.module.voice.VoiceInteractionComponent.VOICE_RECOGNITION_RETRY_ERROR;
import static com.chattylabs.module.voice.VoiceInteractionComponent.VOICE_RECOGNITION_UNAVAILABLE_ERROR;
import static com.chattylabs.module.voice.VoiceInteractionComponent.VOICE_RECOGNITION_UNEXPECTED_ERROR;
import static com.chattylabs.module.voice.VoiceInteractionComponent.VOICE_RECOGNITION_UNKNOWN_ERROR;
import static com.chattylabs.module.voice.VoiceInteractionComponent.VoiceRecognitionListeners;
import static com.chattylabs.module.voice.VoiceInteractionComponent.selectMostConfidentResult;

final class VoiceRecognitionManager {
    private static final String TAG = Tag.make(VoiceRecognitionManager.class);
    private int audioMode;

    private boolean bluetoothScoRequired;

    private boolean requestAudioFocusExclusive;
    private boolean speakerphoneOn;

    private final Application application;
    private final AudioManager audioManager;
    private final AndroidHandler handler;
    private final Intent speechRecognizerIntent;
    private AudioFocusRequest focusRequestExclusive;
    private SpeechRecognizer speechRecognizer;
    private SpeechRecognizerCreator recognizerCreator;

    // Listener
    private final RecognitionAdapter recognitionListener = new RecognitionAdapter() {
        private boolean tryAgainDone;
        private int shortTimeRepeated;
        private boolean needRetryDone;
        private int intents;
        private boolean gotPartialResults;
        private long elapsedTime;

        @Override
        public void reset() {
            elapsedTime = System.currentTimeMillis();
            shortTimeRepeated = intents = 0;
            needRetryDone = tryAgainDone = gotPartialResults = false;
        }

        @Override
        public void onError(int error) {
            // We consider 2 sec as timeout for non speech
            boolean shortTime = System.currentTimeMillis() - elapsedTime < 2000;
            // Start checking for the error
            OnVoiceRecognitionErrorListener errorListener = getOnError();
            if (errorListener != null) {
                if (needRetry(error) && !needRetryDone) {
                    needRetryDone = true;
                    errorListener.execute(VOICE_RECOGNITION_UNAVAILABLE_ERROR, error);
                }
                else if (shortTime && shortTimeRepeated <= 3) {
                    shortTimeRepeated++;
                    errorListener.execute(VOICE_RECOGNITION_UNEXPECTED_ERROR, error);
                }
                else if (gotPartialResults && intents > 0) {
                    gotPartialResults = false;
                    errorListener.execute(VOICE_RECOGNITION_AFTER_PARTIALS_ERROR, error);
                }
                else if (isTryAgain() && !tryAgainDone) {
                    tryAgainDone = true;
                    errorListener.execute(error == SpeechRecognizer.ERROR_NO_MATCH ?
                                          VOICE_RECOGNITION_NO_MATCH_ERROR :
                                          VOICE_RECOGNITION_RETRY_ERROR, error);
                }
                else { // Restore TTS
                    errorListener.execute(VOICE_RECOGNITION_UNKNOWN_ERROR, error);
                }
            }
            reset();
            // Restart the recognizer
            speechRecognizer.cancel();
        }

        @Override
        public void onResults(Bundle results) {
            intents = 0;
            gotPartialResults = false;
            if (getOnResults() == null && getOnMostConfidentResult() == null) return;
            List<String> textResults = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            float[] confidences = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES);
            if (textResults != null && (textResults.size() > 1 || (textResults.size() > 0 && textResults.get(0).length() > 0))) {
                reset();
                if (getOnResults() != null) getOnResults().execute(textResults, confidences);
                if (getOnMostConfidentResult() != null) {
                    getOnMostConfidentResult().execute(selectMostConfidentResult(textResults, confidences));
                }
            }
            else {
                if (getOnError() != null) getOnError().execute(VOICE_RECOGNITION_EMPTY_RESULTS_ERROR, -1);
            }
        }

        @Override
        public void onPartialResults(Bundle partialResults) {
            List<String> textResults = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            float[] confidences = partialResults.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES);
            if (!gotPartialResults && textResults != null &&
                ((intents > 0) || (textResults.size() > 1 || (textResults.size() > 0 && textResults.get(0).length() > 0)))) {
                gotPartialResults = true;
            } intents++;
            if (getOnPartialResults() == null) return;
            if (textResults != null && (textResults.size() > 1 || (textResults.size() > 0 && textResults.get(0).length() > 0))) {
                reset();
                if (getOnPartialResults() != null) getOnPartialResults().execute(textResults, confidences);
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
        this.application = application;
        this.audioManager = (AudioManager) application.getSystemService(Context.AUDIO_SERVICE);
        this.handler = new AndroidHandlerImpl(Looper.getMainLooper());
        this.speechRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        this.speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        this.speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, application.getPackageName());
        this.speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
        this.recognizerCreator = recognizerCreator;
    }

    public void release() {
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
        recognitionListener.reset();
    }

    public void stop() {
        if (speechRecognizer != null) speechRecognizer.stopListening();
    }

    public void cancel() {
        if (speechRecognizer != null) speechRecognizer.cancel();
    }

    public void shutdown() {
        abandonAudioFocusExclusive();
        // Destroy current CustomSpeechRecognition
        handler.post(() -> {
            try {
                if (speechRecognizer != null) {
                    speechRecognizer.destroy();
                    speechRecognizer = null;
                }
            }
            catch (Exception ignored) {}
            finally {
                release();
            }
        });
    }

    public void start(VoiceRecognitionListeners... listeners) {
        handleListeners(listeners);
        requestAudioFocusExclusive();
        handler.post(() -> {
            if (speechRecognizer == null) {
                speechRecognizer = recognizerCreator.create();
                speechRecognizer.setRecognitionListener(recognitionListener);
            }
            speechRecognizer.startListening(speechRecognizerIntent);
        });
    }

    private void handleListeners(VoiceRecognitionListeners... listeners) {
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

    private boolean requestAudioFocusExclusive() {
        if (!requestAudioFocusExclusive) {
            Log.i(TAG, "request Audio Focus Exclusive");
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

    private int getMainStreamType() {
        return isBluetoothScoRequired() ? AudioManager.STREAM_VOICE_CALL : AudioManager.STREAM_MUSIC;
    }
}
