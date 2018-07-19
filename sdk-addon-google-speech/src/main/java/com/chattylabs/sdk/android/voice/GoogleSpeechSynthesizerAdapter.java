package com.chattylabs.sdk.android.voice;

abstract class GoogleSpeechSynthesizerAdapter implements UtteranceListener {
    private ConversationalFlowComponent.OnSynthesizerStart onStartedListener;
    private ConversationalFlowComponent.OnSynthesizerDone onDoneListener;
    private ConversationalFlowComponent.OnSynthesizerError onErrorListener;

    @Override
    public ConversationalFlowComponent.OnSynthesizerStart getOnStartedListener() {
        return onStartedListener != null ? onStartedListener : item -> {};
    }

    @Override
    public GoogleSpeechSynthesizerAdapter setOnStartedListener(ConversationalFlowComponent.OnSynthesizerStart onStartedListener) {
        this.onStartedListener = onStartedListener;
        return this;
    }

    @Override
    public ConversationalFlowComponent.OnSynthesizerDone getOnDoneListener() {
        return onDoneListener != null ? onDoneListener : item -> {};
    }

    @Override
    public GoogleSpeechSynthesizerAdapter setOnDoneListener(ConversationalFlowComponent.OnSynthesizerDone onDoneListener) {
        this.onDoneListener = onDoneListener;
        return this;
    }

    @Override
    public ConversationalFlowComponent.OnSynthesizerError getOnErrorListener() {
        return onErrorListener != null ? onErrorListener : (item1, item2) -> {};
    }

    @Override
    public GoogleSpeechSynthesizerAdapter setOnErrorListener(ConversationalFlowComponent.OnSynthesizerError onErrorListener) {
        this.onErrorListener = onErrorListener;
        return this;
    }

    protected void clearTimeout() {
    }

    protected void startTimeout(String utteranceId) {
    }

    @Override
    public void onStart(String utteranceId) {
    }

    @Override
    public void onDone(String utteranceId) {
    }

    @Override
    public void onError(String utteranceId, int errorCode) {
    }
}
