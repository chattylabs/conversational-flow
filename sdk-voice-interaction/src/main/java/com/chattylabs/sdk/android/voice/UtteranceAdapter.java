package com.chattylabs.sdk.android.voice;

import android.speech.tts.UtteranceProgressListener;

public abstract class UtteranceAdapter extends UtteranceProgressListener {
    private VoiceInteractionComponent.OnTextToSpeechStartedListener onStartedListener;
    private VoiceInteractionComponent.OnTextToSpeechDoneListener onDoneListener;
    private VoiceInteractionComponent.OnTextToSpeechErrorListener onErrorListener;

    public VoiceInteractionComponent.OnTextToSpeechStartedListener getOnStartedListener() {
        return onStartedListener != null ? onStartedListener : item -> {};
    }

    public UtteranceAdapter setOnStartedListener(VoiceInteractionComponent.OnTextToSpeechStartedListener onStartedListener) {
        this.onStartedListener = onStartedListener;
        return this;
    }

    public VoiceInteractionComponent.OnTextToSpeechDoneListener getOnDoneListener() {
        return onDoneListener != null ? onDoneListener : item -> {};
    }

    public UtteranceAdapter setOnDoneListener(VoiceInteractionComponent.OnTextToSpeechDoneListener onDoneListener) {
        this.onDoneListener = onDoneListener;
        return this;
    }

    public VoiceInteractionComponent.OnTextToSpeechErrorListener getOnErrorListener() {
        return onErrorListener != null ? onErrorListener : (item1, item2) -> {};
    }

    public UtteranceAdapter setOnErrorListener(VoiceInteractionComponent.OnTextToSpeechErrorListener onErrorListener) {
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
