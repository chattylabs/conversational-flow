package com.chattylabs.sdk.android.voice;

/**
 * @see android.speech.tts.UtteranceProgressListener
 */
interface SynthesizerUtteranceListener {

    SynthesizerListener.OnStart _getOnStartedListener();

    SynthesizerUtteranceListener _setOnStartedListener(SynthesizerListener.OnStart onStartedListener);

    SynthesizerListener.OnDone _getOnDoneListener();

    SynthesizerUtteranceListener _setOnDoneListener(SynthesizerListener.OnDone onDoneListener);

    SynthesizerListener.OnError _getOnErrorListener();

    SynthesizerUtteranceListener _setOnErrorListener(SynthesizerListener.OnError onErrorListener);

    void clearTimeout(String utteranceId);

    void startTimeout(String utteranceId);

    void onStart(String utteranceId);

    void onDone(String utteranceId);

    void onError(String utteranceId, int errorCode);

}
