package com.chattylabs.sdk.android.voice;

import com.chattylabs.sdk.android.common.internal.ILogger;

import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.*;
import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.SpeechRecognizer;

abstract class BaseSpeechRecognizer implements SpeechRecognizer {

    private final ComponentConfig configuration;
    private final AndroidAudioManager audioManager;
    private final BluetoothSco bluetoothSco;

    // Log stuff
    protected ILogger logger;

    public BaseSpeechRecognizer() {
    }

    abstract RecognizerUtteranceListener getRecognitionListener();

    abstract void startListening();

    abstract String getTag();

    @Override
    public <T extends RecognizerListener> void listen(T... listeners) {
        logger.i(getTag(), "VOICE - start listening");
        handleListeners(listeners);
        checkForBluetoothScoRequired(this::startListening);
    }

    private void handleListeners(RecognizerListener... listeners) {
        getRecognitionListener().reset();
        if (listeners != null && listeners.length > 0) {
            for (RecognizerListener item : listeners) {
                if (item instanceof OnRecognizerReady) {
                    getRecognitionListener()._setOnReady((OnRecognizerReady) item);
                }
                else if (item instanceof OnRecognizerResults) {
                    getRecognitionListener()._setOnResults((OnRecognizerResults) item);
                }
                else if (item instanceof OnRecognizerMostConfidentResult) {
                    getRecognitionListener()._setOnMostConfidentResult((OnRecognizerMostConfidentResult) item);
                }
                else if (item instanceof OnRecognizerPartialResults) {
                    getRecognitionListener()._setOnPartialResults((OnRecognizerPartialResults) item);
                }
                else if (item instanceof OnRecognizerError) {
                    getRecognitionListener()._setOnError((OnRecognizerError) item);
                }
            }
        }
    }

    private void checkForBluetoothScoRequired(Runnable runnable) {
        logger.i(getTag(), "VOICE - is bluetooth Sco required: %s",
                Boolean.toString(configuration.isBluetoothScoRequired()));
        if (configuration.isBluetoothScoRequired() && !bluetoothSco.isBluetoothScoOn()) {
            // Sco Listener
            BluetoothScoListener listener = new BluetoothScoListener() {
                @Override
                public void onConnected() {
                    logger.w(getTag(), "VOICE - Sco onConnected");
                    runnable.run();
                }

                @Override
                public void onDisconnected() {
                    logger.w(getTag(), "VOICE - Sco onDisconnected");
                    if (bluetoothSco.isBluetoothScoOn()) {
                        logger.w(getTag(), "VOICE - shutdown from Sco");
                        shutdown();
                    }
                }
            };
            // Start Bluetooth Sco
            bluetoothSco.startSco(listener);
            logger.v(getTag(), "VOICE - waiting for bluetooth sco connection");
        }
        else {
            logger.v(getTag(), "VOICE - bluetooth sco is: %s", (bluetoothSco.isBluetoothScoOn() ? "on" : "off"));
            runnable.run();
        }
    }
}
