package com.chattylabs.sdk.android.voice;

abstract class GoogleSpeechSynthesizerAdapter {
    private ConversationalFlowComponent.OnSynthesizerStart onStartedListener;
    private ConversationalFlowComponent.OnSynthesizerDone onDoneListener;
    private ConversationalFlowComponent.OnSynthesizerError onErrorListener;

    public ConversationalFlowComponent.OnSynthesizerStart getOnStartedListener() {
        return onStartedListener != null ? onStartedListener : item -> {};
    }

    public GoogleSpeechSynthesizerAdapter setOnStartedListener(ConversationalFlowComponent.OnSynthesizerStart onStartedListener) {
        this.onStartedListener = onStartedListener;
        return this;
    }

    public ConversationalFlowComponent.OnSynthesizerDone getOnDoneListener() {
        return onDoneListener != null ? onDoneListener : item -> {};
    }

    public GoogleSpeechSynthesizerAdapter setOnDoneListener(ConversationalFlowComponent.OnSynthesizerDone onDoneListener) {
        this.onDoneListener = onDoneListener;
        return this;
    }

    public ConversationalFlowComponent.OnSynthesizerError getOnErrorListener() {
        return onErrorListener != null ? onErrorListener : (item1, item2) -> {};
    }

    public GoogleSpeechSynthesizerAdapter setOnErrorListener(ConversationalFlowComponent.OnSynthesizerError onErrorListener) {
        this.onErrorListener = onErrorListener;
        return this;
    }

    protected void clearTimeout() {
    }

    protected void startTimeout(String utteranceId) {
    }

    public void onStart(String utteranceId) {
    }

    public void onDone(String utteranceId) {
    }

    public void onError(String utteranceId, int errorCode) {
    }
}
