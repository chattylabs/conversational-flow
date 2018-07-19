package com.chattylabs.sdk.android.voice;

import android.os.Bundle;
import android.speech.RecognitionListener;
import android.support.annotation.CallSuper;
import android.util.Log;

import com.chattylabs.sdk.android.common.Tag;


abstract class AndroidSpeechRecognitionAdapter implements RecognitionListener {
    private static final String TAG = Tag.make("AndroidSpeechRecognitionAdapter");

    // Settings
    private static final int MINIMUM_REACHED_LEVEL_INTENTS = 3;
    private static final float DEFAULT_THRESHOLD_LOW_SOUND = 5f;
    private static final float DEFAULT_THRESHOLD_NO_SOUND = 3f;

    // Sound states
    static final int UNKNOWN = -1;
    static final int NO_SOUND = 1;
    static final int LOW_SOUND = 2;
    static final int NORMAL_SOUND = 3;

    private ConversationalFlowComponent.OnRecognizerReady onReady;
    private ConversationalFlowComponent.OnRecognizerResults onResults;
    private ConversationalFlowComponent.OnRecognizerPartialResults onPartialResults;
    private ConversationalFlowComponent.OnRecognizerMostConfidentResult onMostConfidentResult;
    private ConversationalFlowComponent.OnRecognizerError onError;

    private boolean tryAgain;
    private int soundLevel = UNKNOWN;
    private boolean rmsDebug;
    private float noSoundThreshold = DEFAULT_THRESHOLD_NO_SOUND;
    private float lowSoundThreshold = DEFAULT_THRESHOLD_LOW_SOUND;
    private int lowSoundIntents;
    private int normalSoundIntents;

    public void setOnReady(ConversationalFlowComponent.OnRecognizerReady onReady) {
        this.onReady = onReady;
    }

    public ConversationalFlowComponent.OnRecognizerResults getOnResults() {
        return onResults;
    }

    public AndroidSpeechRecognitionAdapter setOnResults(ConversationalFlowComponent.OnRecognizerResults onResults) {
        this.onResults = onResults;
        return this;
    }

    public ConversationalFlowComponent.OnRecognizerPartialResults getOnPartialResults() {
        return onPartialResults;
    }

    public AndroidSpeechRecognitionAdapter setOnPartialResults(
            ConversationalFlowComponent.OnRecognizerPartialResults onPartialResults) {
        this.onPartialResults = onPartialResults;
        return this;
    }

    public ConversationalFlowComponent.OnRecognizerMostConfidentResult getOnMostConfidentResult() {
        return onMostConfidentResult;
    }

    public AndroidSpeechRecognitionAdapter setOnMostConfidentResult(
            ConversationalFlowComponent.OnRecognizerMostConfidentResult onMostConfidentResult) {
        this.onMostConfidentResult = onMostConfidentResult;
        return this;
    }

    public ConversationalFlowComponent.OnRecognizerError getOnError() {
        return onError;
    }

    public AndroidSpeechRecognitionAdapter setOnError(ConversationalFlowComponent.OnRecognizerError onError) {
        this.onError = onError;
        return this;
    }

    public AndroidSpeechRecognitionAdapter setNoSoundThreshold(float noSoundThreshold) {
        this.noSoundThreshold = noSoundThreshold;
        return this;
    }

    public AndroidSpeechRecognitionAdapter setLowSoundThreshold(float lowSoundThreshold) {
        this.lowSoundThreshold = lowSoundThreshold;
        return this;
    }

    public boolean isTryAgain() {
        return tryAgain;
    }

    public AndroidSpeechRecognitionAdapter setTryAgain(boolean tryAgain) {
        this.tryAgain = tryAgain;
        return this;
    }

    public void setRmsDebug(boolean rmsDebug) {
        this.rmsDebug = rmsDebug;
    }

    public int getSoundLevel() {
        return soundLevel;
    }

    public void setSoundLevel(int soundLevel) {
        this.soundLevel = soundLevel;
    }

    public abstract void startTimeout();

    public abstract void reset();

    String getSoundLevelAsString(int level) {
        switch (level) {
            case NO_SOUND:
                return "NO_SOUND";
            case LOW_SOUND:
                return "LOW_SOUND";
            case NORMAL_SOUND:
                return "NORMAL_SOUND";
            default:
                return "UNKNOWN";
        }
    }

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
        if (rmsdB % 1 == 0) return;
        if (rmsDebug) Log.v(TAG, "RECOGNITION - Rms db: " + rmsdB);

        if (rmsdB <= noSoundThreshold && soundLevel <= NO_SOUND) {
            soundLevel = NO_SOUND;
            // quiet
        } else if (rmsdB > noSoundThreshold && rmsdB < lowSoundThreshold && soundLevel <= LOW_SOUND) {
            lowSoundIntents++;
            if (lowSoundIntents > MINIMUM_REACHED_LEVEL_INTENTS)
                soundLevel = LOW_SOUND;
            // medium
        } else if (rmsdB >= lowSoundThreshold && soundLevel <= NORMAL_SOUND) {
            lowSoundIntents++;
            if (lowSoundIntents > MINIMUM_REACHED_LEVEL_INTENTS && soundLevel <= LOW_SOUND)
                soundLevel = LOW_SOUND;
            normalSoundIntents++;
            if (normalSoundIntents > MINIMUM_REACHED_LEVEL_INTENTS)
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
