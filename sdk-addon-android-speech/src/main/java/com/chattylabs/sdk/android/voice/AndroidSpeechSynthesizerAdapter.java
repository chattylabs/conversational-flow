package com.chattylabs.sdk.android.voice;

import android.speech.tts.UtteranceProgressListener;

import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.*;

abstract class AndroidSpeechSynthesizerAdapter extends UtteranceProgressListener
        implements SynthesizerUtteranceListener {
    private SynthesizerListener.OnStart onStartedListener;
    private SynthesizerListener.OnDone onDoneListener;
    private SynthesizerListener.OnError onErrorListener;

    @Override
    public SynthesizerListener.OnStart _getOnStartedListener() {
        return onStartedListener != null ? onStartedListener : item -> {};
    }

    @Override
    public AndroidSpeechSynthesizerAdapter _setOnStartedListener(SynthesizerListener.OnStart onStartedListener) {
        this.onStartedListener = onStartedListener;
        return this;
    }

    @Override
    public SynthesizerListener.OnDone _getOnDoneListener() {
        return onDoneListener != null ? onDoneListener : item -> {};
    }

    @Override
    public AndroidSpeechSynthesizerAdapter _setOnDoneListener(SynthesizerListener.OnDone onDoneListener) {
        this.onDoneListener = onDoneListener;
        return this;
    }

    @Override
    public SynthesizerListener.OnError _getOnErrorListener() {
        return onErrorListener != null ? onErrorListener : (item1, item2) -> {};
    }

    @Override
    public AndroidSpeechSynthesizerAdapter _setOnErrorListener(SynthesizerListener.OnError onErrorListener) {
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
