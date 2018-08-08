package com.chattylabs.sdk.android.voice;

import android.os.Bundle;
import android.support.annotation.CallSuper;

abstract class GoogleSpeechRecognitionAdapter implements RecognizerUtteranceListener {
    private boolean tryAgain;
    private RecognizerListener.OnReady onReady;
    private RecognizerListener.OnResults onResults;
    private RecognizerListener.OnPartialResults onPartialResults;
    private RecognizerListener.OnMostConfidentResult onMostConfidentResult;
    private RecognizerListener.OnError onError;

    @Override
    public void _setOnReady(RecognizerListener.OnReady onReady) {
        this.onReady = onReady;
    }

    @Override
    public RecognizerListener.OnResults _getOnResults() {
        return onResults;
    }

    @Override
    public GoogleSpeechRecognitionAdapter _setOnResults(RecognizerListener.OnResults onResults) {
        this.onResults = onResults;
        return this;
    }

    @Override
    public RecognizerListener.OnPartialResults _getOnPartialResults() {
        return onPartialResults;
    }

    @Override
    public GoogleSpeechRecognitionAdapter _setOnPartialResults(
            RecognizerListener.OnPartialResults onPartialResults) {
        this.onPartialResults = onPartialResults;
        return this;
    }

    @Override
    public RecognizerListener.OnMostConfidentResult _getOnMostConfidentResult() {
        return onMostConfidentResult;
    }

    @Override
    public GoogleSpeechRecognitionAdapter _setOnMostConfidentResult(
            RecognizerListener.OnMostConfidentResult onMostConfidentResult) {
        this.onMostConfidentResult = onMostConfidentResult;
        return this;
    }

    @Override
    public RecognizerListener.OnError _getOnError() {
        return onError;
    }

    @Override
    public GoogleSpeechRecognitionAdapter _setOnError(RecognizerListener.OnError onError) {
        this.onError = onError;
        return this;
    }

    @Override
    public GoogleSpeechRecognitionAdapter setTryAgain(boolean tryAgain) {
        this.tryAgain = tryAgain;
        return this;
    }

    @Override
    public boolean isTryAgain() {
        return tryAgain;
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
