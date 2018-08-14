package com.chattylabs.sdk.android.voice;

import com.chattylabs.sdk.android.common.internal.ILogger;

public class AmazonSpeechRecognizer extends BaseSpeechRecognizer {

    AmazonSpeechRecognizer(ComponentConfig configuration,
                           AndroidAudioManager audioManager,
                           BluetoothSco bluetoothSco,
                           ILogger logger) {
        super(configuration, audioManager, bluetoothSco, logger);
    }

    @Override
    RecognizerUtteranceListener getRecognitionListener() {
        return null;
    }

    @Override
    void startListening() {

    }

    @Override
    String getTag() {
        return null;
    }

    @Override
    public void setRmsDebug(boolean debug) {

    }

    @Override
    public void setNoSoundThreshold(float maxValue) {

    }

    @Override
    public void setLowSoundThreshold(float maxValue) {

    }
}
