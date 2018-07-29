package com.chattylabs.sdk.android.voice;

import android.os.Bundle;

interface RecognizerUtteranceListener {
    void _setOnReady(ConversationalFlowComponent.OnRecognizerReady onReady);

    ConversationalFlowComponent.OnRecognizerResults _getOnResults();

    RecognizerUtteranceListener _setOnResults(ConversationalFlowComponent.OnRecognizerResults onResults);

    ConversationalFlowComponent.OnRecognizerPartialResults _getOnPartialResults();

    RecognizerUtteranceListener _setOnPartialResults(
            ConversationalFlowComponent.OnRecognizerPartialResults onPartialResults);

    ConversationalFlowComponent.OnRecognizerMostConfidentResult _getOnMostConfidentResult();

    RecognizerUtteranceListener _setOnMostConfidentResult(
            ConversationalFlowComponent.OnRecognizerMostConfidentResult onMostConfidentResult);

    ConversationalFlowComponent.OnRecognizerError _getOnError();

    RecognizerUtteranceListener _setOnError(ConversationalFlowComponent.OnRecognizerError onError);

    RecognizerUtteranceListener setTryAgain(boolean tryAgain);

    boolean isTryAgain();

    void startTimeout();

    void reset();

    void onReadyForSpeech(Bundle params);

    void onBeginningOfSpeech();

    void onEndOfSpeech();

    void onError(int error);

    void onResults(Bundle results);

    void onPartialResults(Bundle partialResults);
}
