package com.chattylabs.sdk.android.voice;

import android.os.Bundle;
import android.support.annotation.CallSuper;

import com.chattylabs.sdk.android.common.Tag;

abstract class GoogleSpeechRecognitionAdapter {
    private static final String TAG = Tag.make("GoogleSpeechRecognitionAdapter");

    private VoiceInteractionComponent.OnRecognizerReady onReady;
    private VoiceInteractionComponent.OnRecognizerResults onResults;
    private VoiceInteractionComponent.OnRecognizerPartialResults onPartialResults;
    private VoiceInteractionComponent.OnRecognizerMostConfidentResult onMostConfidentResult;
    private VoiceInteractionComponent.OnRecognizerError onError;

    public void setOnReady(VoiceInteractionComponent.OnRecognizerReady onReady) {
        this.onReady = onReady;
    }

    public VoiceInteractionComponent.OnRecognizerResults getOnResults() {
        return onResults;
    }

    public GoogleSpeechRecognitionAdapter setOnResults(VoiceInteractionComponent.OnRecognizerResults onResults) {
        this.onResults = onResults;
        return this;
    }

    public VoiceInteractionComponent.OnRecognizerPartialResults getOnPartialResults() {
        return onPartialResults;
    }

    public GoogleSpeechRecognitionAdapter setOnPartialResults(
            VoiceInteractionComponent.OnRecognizerPartialResults onPartialResults) {
        this.onPartialResults = onPartialResults;
        return this;
    }

    public VoiceInteractionComponent.OnRecognizerMostConfidentResult getOnMostConfidentResult() {
        return onMostConfidentResult;
    }

    public GoogleSpeechRecognitionAdapter setOnMostConfidentResult(
            VoiceInteractionComponent.OnRecognizerMostConfidentResult onMostConfidentResult) {
        this.onMostConfidentResult = onMostConfidentResult;
        return this;
    }

    public VoiceInteractionComponent.OnRecognizerError getOnError() {
        return onError;
    }

    public GoogleSpeechRecognitionAdapter setOnError(VoiceInteractionComponent.OnRecognizerError onError) {
        this.onError = onError;
        return this;
    }

    public abstract void startTimeout();

    public abstract void reset();

    @CallSuper
    public void onReadyForSpeech(Bundle params) {
        if (onReady != null) onReady.execute(params);
    }

    public void onBeginningOfSpeech() {
    }

    public void onEndOfSpeech() {
    }

    public void onError(int error) {
    }

    public void onResults(Bundle results) {
    }

    public void onPartialResults(Bundle partialResults) {
    }
}
