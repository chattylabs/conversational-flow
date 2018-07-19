package com.chattylabs.sdk.android.voice;

import android.speech.tts.UtteranceProgressListener;

abstract class AndroidSpeechSynthesizerAdapter extends UtteranceProgressListener implements UtteranceListener {
    private ConversationalFlowComponent.OnSynthesizerStart onStartedListener;
    private ConversationalFlowComponent.OnSynthesizerDone onDoneListener;
    private ConversationalFlowComponent.OnSynthesizerError onErrorListener;

    @Override
    public ConversationalFlowComponent.OnSynthesizerStart getOnStartedListener() {
        return onStartedListener != null ? onStartedListener : item -> {};
    }

    @Override
    public AndroidSpeechSynthesizerAdapter setOnStartedListener(ConversationalFlowComponent.OnSynthesizerStart onStartedListener) {
        this.onStartedListener = onStartedListener;
        return this;
    }

    @Override
    public ConversationalFlowComponent.OnSynthesizerDone getOnDoneListener() {
        return onDoneListener != null ? onDoneListener : item -> {};
    }

    @Override
    public AndroidSpeechSynthesizerAdapter setOnDoneListener(ConversationalFlowComponent.OnSynthesizerDone onDoneListener) {
        this.onDoneListener = onDoneListener;
        return this;
    }

    @Override
    public ConversationalFlowComponent.OnSynthesizerError getOnErrorListener() {
        return onErrorListener != null ? onErrorListener : (item1, item2) -> {};
    }

    @Override
    public AndroidSpeechSynthesizerAdapter setOnErrorListener(ConversationalFlowComponent.OnSynthesizerError onErrorListener) {
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
