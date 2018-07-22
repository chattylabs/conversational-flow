package com.chattylabs.sdk.android.voice;

interface UtteranceListener {

    ConversationalFlowComponent.OnSynthesizerStart _getOnStartedListener();

    UtteranceListener _setOnStartedListener(ConversationalFlowComponent.OnSynthesizerStart onStartedListener);

    ConversationalFlowComponent.OnSynthesizerDone _getOnDoneListener();

    UtteranceListener _setOnDoneListener(ConversationalFlowComponent.OnSynthesizerDone onDoneListener);

    ConversationalFlowComponent.OnSynthesizerError _getOnErrorListener();

    UtteranceListener _setOnErrorListener(ConversationalFlowComponent.OnSynthesizerError onErrorListener);

    void clearTimeout();

    void startTimeout(String utteranceId);

    void onStart(String utteranceId);

    void onDone(String utteranceId);

    void onError(String utteranceId, int errorCode);

}
