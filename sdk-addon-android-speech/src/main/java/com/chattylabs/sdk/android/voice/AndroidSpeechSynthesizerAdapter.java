package com.chattylabs.sdk.android.voice;

import android.speech.tts.UtteranceProgressListener;

abstract class AndroidSpeechSynthesizerAdapter extends UtteranceProgressListener {
    private VoiceInteractionComponent.OnSynthesizerStart onStartedListener;
    private VoiceInteractionComponent.OnSynthesizerDone onDoneListener;
    private VoiceInteractionComponent.OnSynthesizerError onErrorListener;

    public VoiceInteractionComponent.OnSynthesizerStart getOnStartedListener() {
        return onStartedListener != null ? onStartedListener : item -> {};
    }

    public AndroidSpeechSynthesizerAdapter setOnStartedListener(VoiceInteractionComponent.OnSynthesizerStart onStartedListener) {
        this.onStartedListener = onStartedListener;
        return this;
    }

    public VoiceInteractionComponent.OnSynthesizerDone getOnDoneListener() {
        return onDoneListener != null ? onDoneListener : item -> {};
    }

    public AndroidSpeechSynthesizerAdapter setOnDoneListener(VoiceInteractionComponent.OnSynthesizerDone onDoneListener) {
        this.onDoneListener = onDoneListener;
        return this;
    }

    public VoiceInteractionComponent.OnSynthesizerError getOnErrorListener() {
        return onErrorListener != null ? onErrorListener : (item1, item2) -> {};
    }

    public AndroidSpeechSynthesizerAdapter setOnErrorListener(VoiceInteractionComponent.OnSynthesizerError onErrorListener) {
        this.onErrorListener = onErrorListener;
        return this;
    }

    protected void clearTimeout() {
    }

    protected void startTimeout(String utteranceId) {
    }

    /**
     * @inherited
     */
    @Override
    public void onStart(String utteranceId) {
    }

    /**
     * @inherited
     */
    @Override
    public void onDone(String utteranceId) {
    }

    /**
     * @inherited
     */
    @Override @SuppressWarnings("deprecation")
    final public void onError(String utteranceId) {
    }
}
