package com.chattylabs.sdk.android.voice;


abstract class GoogleSpeechSynthesizerAdapter implements SynthesizerUtteranceListener {
    private SynthesizerListener.OnStart onStartedListener;
    private SynthesizerListener.OnDone onDoneListener;
    private SynthesizerListener.OnError onErrorListener;

    @Override
    public SynthesizerListener.OnStart _getOnStartedListener() {
        return onStartedListener != null ? onStartedListener : item -> {};
    }

    @Override
    public GoogleSpeechSynthesizerAdapter _setOnStartedListener(SynthesizerListener.OnStart onStartedListener) {
        this.onStartedListener = onStartedListener;
        return this;
    }

    @Override
    public SynthesizerListener.OnDone _getOnDoneListener() {
        return onDoneListener != null ? onDoneListener : item -> {};
    }

    @Override
    public GoogleSpeechSynthesizerAdapter _setOnDoneListener(SynthesizerListener.OnDone onDoneListener) {
        this.onDoneListener = onDoneListener;
        return this;
    }

    @Override
    public SynthesizerListener.OnError _getOnErrorListener() {
        return onErrorListener != null ? onErrorListener : (item1, item2) -> {};
    }

    @Override
    public GoogleSpeechSynthesizerAdapter _setOnErrorListener(SynthesizerListener.OnError onErrorListener) {
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
