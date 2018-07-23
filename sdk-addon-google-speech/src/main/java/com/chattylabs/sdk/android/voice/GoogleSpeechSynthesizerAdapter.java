package com.chattylabs.sdk.android.voice;

import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.*;

abstract class GoogleSpeechSynthesizerAdapter implements UtteranceListener {
    private OnSynthesizerStart onStartedListener;
    private OnSynthesizerDone onDoneListener;
    private OnSynthesizerError onErrorListener;

    @Override
    public OnSynthesizerStart _getOnStartedListener() {
        return onStartedListener != null ? onStartedListener : item -> {};
    }

    @Override
    public GoogleSpeechSynthesizerAdapter _setOnStartedListener(OnSynthesizerStart onStartedListener) {
        this.onStartedListener = onStartedListener;
        return this;
    }

    @Override
    public OnSynthesizerDone _getOnDoneListener() {
        return onDoneListener != null ? onDoneListener : item -> {};
    }

    @Override
    public GoogleSpeechSynthesizerAdapter _setOnDoneListener(OnSynthesizerDone onDoneListener) {
        this.onDoneListener = onDoneListener;
        return this;
    }

    @Override
    public OnSynthesizerError _getOnErrorListener() {
        return onErrorListener != null ? onErrorListener : (item1, item2) -> {};
    }

    @Override
    public GoogleSpeechSynthesizerAdapter _setOnErrorListener(OnSynthesizerError onErrorListener) {
        this.onErrorListener = onErrorListener;
        return this;
    }

    @Override
    public void clearTimeout(String utteranceId) {
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
