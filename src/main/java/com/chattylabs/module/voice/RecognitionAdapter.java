package com.chattylabs.module.voice;

import android.os.Bundle;
import android.speech.RecognitionListener;
import android.support.annotation.CallSuper;

abstract class RecognitionAdapter implements RecognitionListener {

    private VoiceInteractionComponent.OnVoiceRecognitionReadyListener onReady;
    private VoiceInteractionComponent.OnVoiceRecognitionResultsListener onResults;
    private VoiceInteractionComponent.OnVoiceRecognitionPartialResultsListener onPartialResults;
    private VoiceInteractionComponent.OnVoiceRecognitionMostConfidentResultListener onMostConfidentResult;
    private VoiceInteractionComponent.OnVoiceRecognitionErrorListener onError;

    private boolean tryAgain;

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
