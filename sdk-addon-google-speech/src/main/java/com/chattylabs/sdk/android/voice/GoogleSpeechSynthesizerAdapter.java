package com.chattylabs.sdk.android.voice;

import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.*;

abstract class GoogleSpeechSynthesizerAdapter implements UtteranceListener {
    private OnSynthesizerStart onStartedListener;
    private OnSynthesizerDone onDoneListener;
    private OnSynthesizerError onErrorListener;

    @Override
    public OnSynthesizerStart getOnStartedListener() {
        return onStartedListener != null ? onStartedListener : item -> {};
    }

    @Override
    public GoogleSpeechSynthesizerAdapter setOnStartedListener(OnSynthesizerStart onStartedListener) {
        this.onStartedListener = onStartedListener;
        return this;
    }

    @Override
    public OnSynthesizerDone getOnDoneListener() {
        return onDoneListener != null ? onDoneListener : item -> {};
    }

    @Override
    public GoogleSpeechSynthesizerAdapter setOnDoneListener(OnSynthesizerDone onDoneListener) {
        this.onDoneListener = onDoneListener;
        return this;
    }

    @Override
    public OnSynthesizerError getOnErrorListener() {
        return onErrorListener != null ? onErrorListener : (item1, item2) -> {};
    }

    @Override
    public GoogleSpeechSynthesizerAdapter setOnErrorListener(OnSynthesizerError onErrorListener) {
        this.onErrorListener = onErrorListener;
        return this;
    }

    @Override
    public void clearTimeout() {
    }

    @Override
    public void startTimeout(String utteranceId) {
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
