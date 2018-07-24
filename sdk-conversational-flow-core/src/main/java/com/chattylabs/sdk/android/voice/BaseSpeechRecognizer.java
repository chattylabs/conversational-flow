package com.chattylabs.sdk.android.voice;

import com.chattylabs.sdk.android.common.internal.ILogger;

import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.SpeechRecognizer;

abstract class BaseSpeechRecognizer implements SpeechRecognizer {

    private final ComponentConfig configuration;

    // Log stuff
    private ILogger logger;

    public BaseSpeechRecognizer() {
    }

    abstract String getTag();

    @Override
    public <T extends ConversationalFlowComponent.RecognizerListener> void listen(T... listeners) {
        logger.i(getTag(), "VOICE - start listening");
        handleListeners(listeners);
        checkForBluetoothScoRequired(this::startListening);
    }

    private void checkForBluetoothScoRequired(Runnable starter) {
        logger.i(getTag(), "VOICE - is bluetooth Sco required: " +
                Boolean.toString(configuration.isBluetoothScoRequired()));
        if (configuration.isBluetoothScoRequired() && !bluetoothSco.isBluetoothScoOn()) {
            // Sco Listener
            BluetoothScoListener listener = new BluetoothScoListener() {
                @Override
                public void onConnected() {
                    logger.w(getTag(), "VOICE - Sco onConnected");
                    starter.run();
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
            logger.v(getTag(), "VOICE - bluetooth sco is: " + (bluetoothSco.isBluetoothScoOn() ? "on" : "off"));
            starter.run();
        }
    }
}
