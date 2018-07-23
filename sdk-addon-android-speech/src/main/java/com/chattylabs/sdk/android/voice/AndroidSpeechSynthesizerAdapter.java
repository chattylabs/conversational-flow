package com.chattylabs.sdk.android.voice;

import android.speech.tts.UtteranceProgressListener;

import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.*;

abstract class AndroidSpeechSynthesizerAdapter extends UtteranceProgressListener implements UtteranceListener {
    private OnSynthesizerStart onStartedListener;
    private OnSynthesizerDone onDoneListener;
    private OnSynthesizerError onErrorListener;

    @Override
    public OnSynthesizerStart _getOnStartedListener() {
        return onStartedListener != null ? onStartedListener : item -> {};
    }

    @Override
    public AndroidSpeechSynthesizerAdapter _setOnStartedListener(OnSynthesizerStart onStartedListener) {
        this.onStartedListener = onStartedListener;
        return this;
    }

    @Override
    public OnSynthesizerDone _getOnDoneListener() {
        return onDoneListener != null ? onDoneListener : item -> {};
    }

    @Override
    public AndroidSpeechSynthesizerAdapter _setOnDoneListener(OnSynthesizerDone onDoneListener) {
        this.onDoneListener = onDoneListener;
        return this;
    }

    @Override
    public OnSynthesizerError _getOnErrorListener() {
        return onErrorListener != null ? onErrorListener : (item1, item2) -> {};
    }

    @Override
    public AndroidSpeechSynthesizerAdapter _setOnErrorListener(OnSynthesizerError onErrorListener) {
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

    @Override @SuppressWarnings("deprecation")
    final public void onError(String utteranceId) {
    }
}
