package com.chattylabs.sdk.android.voice;

import android.support.annotation.CallSuper;

import com.chattylabs.sdk.android.common.internal.ILogger;

import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.*;
import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.SpeechRecognizer;

abstract class BaseSpeechRecognizer implements SpeechRecognizer {

    // Resources
    private final ComponentConfig configuration;
    private final BluetoothSco bluetoothSco;
    private final AndroidAudioManager audioManager;

    // Log stuff
    protected final ILogger logger;

    BaseSpeechRecognizer(ComponentConfig configuration,
                                BluetoothSco bluetoothSco,
                                AndroidAudioManager audioManager,
                                ILogger logger) {
        this.configuration = configuration;
        this.bluetoothSco = bluetoothSco;
        this.audioManager = audioManager;
        this.logger = logger;
    }

    abstract RecognizerUtteranceListener getRecognitionListener();

    abstract void startListening();

    abstract String getTag();

    public ComponentConfig getConfiguration() {
        return configuration;
    }

    void requestAudioFocus() {
        if (!bluetoothSco.isBluetoothScoOn()) {
            audioManager.requestAudioFocus(
                    getConfiguration().isAudioExclusiveRequiredForRecognizer());
        }
    }

    void abandonAudioFocus() {
        audioManager.abandonAudioFocus();
    }

    public void setTryAgain(boolean tryAgain) {
        getRecognitionListener().setTryAgain(tryAgain);
    }

    @CallSuper
    @Override
    public <T extends RecognizerListener> void listen(T... listeners) {
        logger.i(getTag(), "VOICE - start listening");
        handleListeners(listeners);
        checkForBluetoothScoRequired(this::startListening);
    }

    @CallSuper
    @Override
    public void stop() {
        bluetoothSco.stopSco();
        getRecognitionListener().reset();
    }

    @CallSuper
    @Override
    public void cancel() {
        bluetoothSco.stopSco();
        getRecognitionListener().reset();
    }

    @CallSuper
    @Override
    public void shutdown() {
        bluetoothSco.stopSco();
        getRecognitionListener().reset();
    }

    @CallSuper
    @Override
    public void release() {

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