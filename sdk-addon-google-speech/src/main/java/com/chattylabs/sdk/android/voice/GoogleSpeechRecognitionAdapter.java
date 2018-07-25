package com.chattylabs.sdk.android.voice;

import android.os.Bundle;
import android.support.annotation.CallSuper;

import com.chattylabs.sdk.android.voice.ConversationalFlowComponent.OnRecognizerError;
import com.chattylabs.sdk.android.voice.ConversationalFlowComponent.OnRecognizerMostConfidentResult;
import com.chattylabs.sdk.android.voice.ConversationalFlowComponent.OnRecognizerPartialResults;
import com.chattylabs.sdk.android.voice.ConversationalFlowComponent.OnRecognizerReady;
import com.chattylabs.sdk.android.voice.ConversationalFlowComponent.OnRecognizerResults;

abstract class GoogleSpeechRecognitionAdapter implements RecognizerUtteranceListener {
    private OnRecognizerReady onReady;
    private OnRecognizerResults onResults;
    private OnRecognizerPartialResults onPartialResults;
    private OnRecognizerMostConfidentResult onMostConfidentResult;
    private OnRecognizerError onError;

    @Override
    public void _setOnReady(OnRecognizerReady onReady) {
        this.onReady = onReady;
    }

    @Override
    public OnRecognizerResults _getOnResults() {
        return onResults;
    }

    @Override
    public GoogleSpeechRecognitionAdapter _setOnResults(OnRecognizerResults onResults) {
        this.onResults = onResults;
        return this;
    }

    @Override
    public OnRecognizerPartialResults _getOnPartialResults() {
        return onPartialResults;
    }

    @Override
    public GoogleSpeechRecognitionAdapter _setOnPartialResults(
            OnRecognizerPartialResults onPartialResults) {
        this.onPartialResults = onPartialResults;
        return this;
    }

    @Override
    public OnRecognizerMostConfidentResult _getOnMostConfidentResult() {
        return onMostConfidentResult;
    }

    @Override
    public GoogleSpeechRecognitionAdapter _setOnMostConfidentResult(
            OnRecognizerMostConfidentResult onMostConfidentResult) {
        this.onMostConfidentResult = onMostConfidentResult;
        return this;
    }

    @Override
    public OnRecognizerError _getOnError() {
        return onError;
    }

    @Override
    public GoogleSpeechRecognitionAdapter _setOnError(OnRecognizerError onError) {
        this.onError = onError;
        return this;
    }

    @CallSuper
    @Override
    public void onReadyForSpeech(Bundle params) {
        if (onReady != null) onReady.execute(params);
    }

    @Override
    public void onBeginningOfSpeech() {
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
}
