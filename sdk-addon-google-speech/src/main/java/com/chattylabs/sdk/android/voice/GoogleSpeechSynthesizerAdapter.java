package com.chattylabs.sdk.android.voice;

abstract class GoogleSpeechSynthesizerAdapter {
    private VoiceInteractionComponent.OnSynthesizerStart onStartedListener;
    private VoiceInteractionComponent.OnSynthesizerDone onDoneListener;
    private VoiceInteractionComponent.OnSynthesizerError onErrorListener;

    public VoiceInteractionComponent.OnSynthesizerStart getOnStartedListener() {
        return onStartedListener != null ? onStartedListener : item -> {};
    }

    public GoogleSpeechSynthesizerAdapter setOnStartedListener(VoiceInteractionComponent.OnSynthesizerStart onStartedListener) {
        this.onStartedListener = onStartedListener;
        return this;
    }

    public VoiceInteractionComponent.OnSynthesizerDone getOnDoneListener() {
        return onDoneListener != null ? onDoneListener : item -> {};
    }

    public GoogleSpeechSynthesizerAdapter setOnDoneListener(VoiceInteractionComponent.OnSynthesizerDone onDoneListener) {
        this.onDoneListener = onDoneListener;
        return this;
    }

    public VoiceInteractionComponent.OnSynthesizerError getOnErrorListener() {
        return onErrorListener != null ? onErrorListener : (item1, item2) -> {};
    }

    public GoogleSpeechSynthesizerAdapter setOnErrorListener(VoiceInteractionComponent.OnSynthesizerError onErrorListener) {
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
