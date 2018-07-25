package com.chattylabs.sdk.android.voice;

import com.chattylabs.sdk.android.voice.ConversationalFlowComponent.OnSynthesizerDone;
import com.chattylabs.sdk.android.voice.ConversationalFlowComponent.OnSynthesizerError;
import com.chattylabs.sdk.android.voice.ConversationalFlowComponent.OnSynthesizerStart;

interface SynthesizerUtteranceListener {

    OnSynthesizerStart _getOnStartedListener();

    SynthesizerUtteranceListener _setOnStartedListener(OnSynthesizerStart onStartedListener);

    OnSynthesizerDone _getOnDoneListener();

    SynthesizerUtteranceListener _setOnDoneListener(OnSynthesizerDone onDoneListener);

    OnSynthesizerError _getOnErrorListener();

    SynthesizerUtteranceListener _setOnErrorListener(OnSynthesizerError onErrorListener);

    void clearTimeout(String utteranceId);

    void startTimeout(String utteranceId);

    void onStart(String utteranceId);

    void onDone(String utteranceId);

    void onError(String utteranceId, int errorCode);

}
