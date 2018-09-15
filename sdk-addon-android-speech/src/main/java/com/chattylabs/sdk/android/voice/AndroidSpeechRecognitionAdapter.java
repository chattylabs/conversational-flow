package com.chattylabs.sdk.android.voice;

import android.os.Bundle;
import android.speech.RecognitionListener;
import android.support.annotation.CallSuper;


abstract class AndroidSpeechRecognitionAdapter
        implements RecognitionListener, RecognizerUtteranceListener {
    // Settings
    private static final int MINIMUM_REACHED_LEVEL_INTENTS = 3;
    private static final float DEFAULT_THRESHOLD_LOW_SOUND = 5f;
    private static final float DEFAULT_THRESHOLD_NO_SOUND = 3f;

    // Sound states
    static final int UNKNOWN = -1;
    static final int NO_SOUND = 1;
    static final int LOW_SOUND = 2;
    static final int NORMAL_SOUND = 3;

    private RecognizerListener.OnReady onReady;
    private RecognizerListener.OnResults onResults;
    private RecognizerListener.OnPartialResults onPartialResults;
    private RecognizerListener.OnMostConfidentResult onMostConfidentResult;
    private RecognizerListener.OnError onError;

    private boolean tryAgain;
    private int soundLevel = UNKNOWN;
    private boolean rmsDebug;
    private float noSoundThreshold = DEFAULT_THRESHOLD_NO_SOUND;
    private float lowSoundThreshold = DEFAULT_THRESHOLD_LOW_SOUND;
    private int lowSoundIntents;
    private int normalSoundIntents;

    @Override
    public void _setOnReady(RecognizerListener.OnReady onReady) {
        this.onReady = onReady;
    }

    @Override
    public RecognizerListener.OnResults _getOnResults() {
        return onResults;
    }

    @Override
    public AndroidSpeechRecognitionAdapter _setOnResults(RecognizerListener.OnResults onResults) {
        this.onResults = onResults;
        return this;
    }

    @Override
    public RecognizerListener.OnPartialResults _getOnPartialResults() {
        return onPartialResults;
    }

    @Override
    public AndroidSpeechRecognitionAdapter _setOnPartialResults(
            RecognizerListener.OnPartialResults onPartialResults) {
        this.onPartialResults = onPartialResults;
        return this;
    }

    @Override
    public RecognizerListener.OnMostConfidentResult _getOnMostConfidentResult() {
        return onMostConfidentResult;
    }

    @Override
    public AndroidSpeechRecognitionAdapter _setOnMostConfidentResult(
            RecognizerListener.OnMostConfidentResult onMostConfidentResult) {
        this.onMostConfidentResult = onMostConfidentResult;
        return this;
    }

    @Override
    public RecognizerListener.OnError _getOnError() {
        return onError;
    }

    @Override
    public AndroidSpeechRecognitionAdapter _setOnError(RecognizerListener.OnError onError) {
        this.onError = onError;
        return this;
    }

    @Override
    public AndroidSpeechRecognitionAdapter setTryAgain(boolean tryAgain) {
        this.tryAgain = tryAgain;
        return this;
    }

    @Override
    public boolean isTryAgain() {
        return tryAgain;
    }

    public AndroidSpeechRecognitionAdapter setNoSoundThreshold(float noSoundThreshold) {
        this.noSoundThreshold = noSoundThreshold;
        return this;
    }

    public AndroidSpeechRecognitionAdapter setLowSoundThreshold(float lowSoundThreshold) {
        this.lowSoundThreshold = lowSoundThreshold;
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
        if (onReady != null) onReady.execute(params);
    }

    @Override
    public void onBeginningOfSpeech() {
    }

    @Override
    public void onRmsChanged(float rmsDb) {
        if (rmsDb % 1 == 0) return;
        if (rmsDebug) System.out.printf("Rms db: %s", rmsDb);

        if (rmsDb <= noSoundThreshold && soundLevel <= NO_SOUND) {
            soundLevel = NO_SOUND;
            // quiet
        } else if (rmsDb > noSoundThreshold && rmsDb < lowSoundThreshold && soundLevel <= LOW_SOUND) {
            lowSoundIntents++;
            if (lowSoundIntents > MINIMUM_REACHED_LEVEL_INTENTS)
                soundLevel = LOW_SOUND;
            // medium
        } else if (rmsDb >= lowSoundThreshold && soundLevel <= NORMAL_SOUND) {
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
    }
}
