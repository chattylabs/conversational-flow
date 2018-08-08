package com.chattylabs.sdk.android.voice;

import android.os.Bundle;

interface RecognizerUtteranceListener {
    void _setOnReady(RecognizerListener.OnReady onReady);

    RecognizerListener.OnResults _getOnResults();

    RecognizerUtteranceListener _setOnResults(RecognizerListener.OnResults onResults);

    RecognizerListener.OnPartialResults _getOnPartialResults();

    RecognizerUtteranceListener _setOnPartialResults(
            RecognizerListener.OnPartialResults onPartialResults);

    RecognizerListener.OnMostConfidentResult _getOnMostConfidentResult();

    RecognizerUtteranceListener _setOnMostConfidentResult(
            RecognizerListener.OnMostConfidentResult onMostConfidentResult);

    RecognizerListener.OnError _getOnError();

    RecognizerUtteranceListener _setOnError(RecognizerListener.OnError onError);

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
