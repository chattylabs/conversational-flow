package com.chattylabs.sdk.android.voice;

interface UtteranceListener {

    ConversationalFlowComponent.OnSynthesizerStart getOnStartedListener();

    UtteranceListener setOnStartedListener(ConversationalFlowComponent.OnSynthesizerStart onStartedListener);

    ConversationalFlowComponent.OnSynthesizerDone getOnDoneListener();

    UtteranceListener setOnDoneListener(ConversationalFlowComponent.OnSynthesizerDone onDoneListener);

    ConversationalFlowComponent.OnSynthesizerError getOnErrorListener();

    UtteranceListener setOnErrorListener(ConversationalFlowComponent.OnSynthesizerError onErrorListener);

    void clearTimeout();

    void startTimeout(String utteranceId);

    void onStart(String utteranceId);

    void onDone(String utteranceId);

    void onError(String utteranceId, int errorCode);

}
