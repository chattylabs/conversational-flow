package com.chattylabs.module.voice;

import android.os.Bundle;
import android.speech.RecognitionListener;
import android.support.annotation.CallSuper;
import android.util.Log;

import com.chattylabs.module.core.Tag;

abstract class RecognitionAdapter implements RecognitionListener {
    public static final float MIN_THRESHOLD = 15f;
    public static final float THRESHOLD_SOUND = 4f;
    private final boolean DEBUG = true;
    public static final int UNKNOWN = -1;
    public static final int NO_SOUND = 1;
    public static final int LOW_SOUND = 2;
    public static final int NORMAL_SOUND = 3;
    private static final String TAG = Tag.make(RecognitionAdapter.class);

    private VoiceInteractionComponent.OnVoiceRecognitionReadyListener onReady;
    private VoiceInteractionComponent.OnVoiceRecognitionResultsListener onResults;
    private VoiceInteractionComponent.OnVoiceRecognitionPartialResultsListener onPartialResults;
    private VoiceInteractionComponent.OnVoiceRecognitionMostConfidentResultListener onMostConfidentResult;
    private VoiceInteractionComponent.OnVoiceRecognitionErrorListener onError;

    private boolean tryAgain;
    private int soundLevel = UNKNOWN;

    public void setOnReady(VoiceInteractionComponent.OnVoiceRecognitionReadyListener onReady) {
        this.onReady = onReady;
    }

    public VoiceInteractionComponent.OnVoiceRecognitionResultsListener getOnResults() {
        return onResults;
    }

    public RecognitionAdapter setOnResults(VoiceInteractionComponent.OnVoiceRecognitionResultsListener onResults) {
        this.onResults = onResults;
        return this;
    }

    public VoiceInteractionComponent.OnVoiceRecognitionPartialResultsListener getOnPartialResults() {
        return onPartialResults;
    }

    public RecognitionAdapter setOnPartialResults(
            VoiceInteractionComponent.OnVoiceRecognitionPartialResultsListener onPartialResults) {
        this.onPartialResults = onPartialResults;
        return this;
    }

    public VoiceInteractionComponent.OnVoiceRecognitionMostConfidentResultListener getOnMostConfidentResult() {
        return onMostConfidentResult;
    }

    public RecognitionAdapter setOnMostConfidentResult(
            VoiceInteractionComponent.OnVoiceRecognitionMostConfidentResultListener onMostConfidentResult) {
        this.onMostConfidentResult = onMostConfidentResult;
        return this;
    }

    public VoiceInteractionComponent.OnVoiceRecognitionErrorListener getOnError() {
        return onError;
    }

    public RecognitionAdapter setOnError(VoiceInteractionComponent.OnVoiceRecognitionErrorListener onError) {
        this.onError = onError;
        return this;
    }

    public boolean isTryAgain() {
        return tryAgain;
    }

    public RecognitionAdapter setTryAgain(boolean tryAgain) {
        this.tryAgain = tryAgain;
        return this;
    }

    public int getSoundLevel() {
        return soundLevel;
    }

    public void setSoundLevel(int soundLevel) {
        this.soundLevel = soundLevel;
    }

    public abstract void releaseTimeout();

    public abstract void startTimeout();

    public abstract void reset();

    @Override @CallSuper
    public void onReadyForSpeech(Bundle params) {
       //if (BuildConfig.DEBUG) System.out.println("onReadyForSpeech: " + params);
        if (onReady != null) onReady.execute(params);
    }

    @Override
    public void onBeginningOfSpeech() {
    }

    @Override
    public void onRmsChanged(float rmsdB) {
        if (DEBUG) Log.v(TAG, "RECOGNITION - Rms db: " + rmsdB);

        if (rmsdB <= THRESHOLD_SOUND && soundLevel <= NO_SOUND) {
            soundLevel = NO_SOUND;
            // quiet
        } else if (rmsdB > THRESHOLD_SOUND && rmsdB < MIN_THRESHOLD && soundLevel <= LOW_SOUND) {
            soundLevel = LOW_SOUND;
            // medium
        } else if (rmsdB >= MIN_THRESHOLD && soundLevel <= NORMAL_SOUND) {
            soundLevel = NORMAL_SOUND;
            // loud
        }
    }

    @Override
    public void onBufferReceived(byte[] buffer) {
    }

    @Override
    public void onEndOfSpeech() {
    }

    @Override
    public void onError(int error) {
    }

    @Override
    public void onResults(Bundle results) {
    }

    @Override
    public void onPartialResults(Bundle partialResults) {
    }

    @Override
    public void onEvent(int eventType, Bundle params) {
        //if (BuildConfig.DEBUG) System.out.println("onEvent: " + eventType + " - " + params);
    }
}
