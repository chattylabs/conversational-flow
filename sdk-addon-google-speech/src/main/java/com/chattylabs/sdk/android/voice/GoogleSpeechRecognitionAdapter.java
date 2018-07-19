package com.chattylabs.sdk.android.voice;

import android.os.Bundle;
import android.support.annotation.CallSuper;

import com.chattylabs.sdk.android.common.Tag;

abstract class GoogleSpeechRecognitionAdapter {
    private static final String TAG = Tag.make("GoogleSpeechRecognitionAdapter");

    private ConversationalFlowComponent.OnRecognizerReady onReady;
    private ConversationalFlowComponent.OnRecognizerResults onResults;
    private ConversationalFlowComponent.OnRecognizerPartialResults onPartialResults;
    private ConversationalFlowComponent.OnRecognizerMostConfidentResult onMostConfidentResult;
    private ConversationalFlowComponent.OnRecognizerError onError;

    public void setOnReady(ConversationalFlowComponent.OnRecognizerReady onReady) {
        this.onReady = onReady;
    }

    public ConversationalFlowComponent.OnRecognizerResults getOnResults() {
        return onResults;
    }

    public GoogleSpeechRecognitionAdapter setOnResults(ConversationalFlowComponent.OnRecognizerResults onResults) {
        this.onResults = onResults;
        return this;
    }

    public ConversationalFlowComponent.OnRecognizerPartialResults getOnPartialResults() {
        return onPartialResults;
    }

    public GoogleSpeechRecognitionAdapter setOnPartialResults(
            ConversationalFlowComponent.OnRecognizerPartialResults onPartialResults) {
        this.onPartialResults = onPartialResults;
        return this;
    }

    public ConversationalFlowComponent.OnRecognizerMostConfidentResult getOnMostConfidentResult() {
        return onMostConfidentResult;
    }

    public GoogleSpeechRecognitionAdapter setOnMostConfidentResult(
            ConversationalFlowComponent.OnRecognizerMostConfidentResult onMostConfidentResult) {
        this.onMostConfidentResult = onMostConfidentResult;
        return this;
    }

    public ConversationalFlowComponent.OnRecognizerError getOnError() {
        return onError;
    }

    public GoogleSpeechRecognitionAdapter setOnError(ConversationalFlowComponent.OnRecognizerError onError) {
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
