package com.chattylabs.sdk.android.voice;

import android.support.annotation.CallSuper;

import com.chattylabs.android.commons.internal.ILogger;


/**
 * When implementing from this class you must create a constructor that receives the following parameters
 * in the same order:
 * <p/>
 * <pre>{@code
 * public Constructor(Application, ComponentConfig, AndroidAudioManager, BluetoothSco, ILogger) {
 *     super(ComponentConfig, AndroidAudioManager, BluetoothSco, ILogger);
 *     //...
 * }
 * }</pre>
 * Otherwise the addon initialization will throw and Exception on runtime.
 *
 * @see SpeechRecognizerComponent
 * @see android.app.Application
 * @see ComponentConfig
 * @see AndroidAudioManager
 * @see BluetoothSco
 * @see ILogger
 */
abstract class BaseSpeechRecognizer implements SpeechRecognizerComponent {
    // Minimum constants
    int MIN_VOICE_RECOGNITION_TIME_LISTENING = 2000;

    // Resources
    private final ComponentConfig configuration;
    private final BluetoothSco bluetoothSco;
    private final AndroidAudioManager audioManager;

    // Log stuff
    protected final ILogger logger;

    BaseSpeechRecognizer(ComponentConfig configuration,
                         AndroidAudioManager audioManager,
                         BluetoothSco bluetoothSco,
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

    /**
     * Setting this option will forward a flag onto the {@link RecognizerUtteranceListener} implementation.
     * <br/>The developer can then opt to record again the user voice as a second chance if there is no match.
     */
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
        // No resources to release at this level
    }

    private void handleListeners(RecognizerListener... listeners) {
        getRecognitionListener().reset();
        if (listeners != null && listeners.length > 0) {
            for (RecognizerListener item : listeners) {
                if (item instanceof RecognizerListener.OnReady) {
                    getRecognitionListener()._setOnReady((RecognizerListener.OnReady) item);
                }
                else if (item instanceof RecognizerListener.OnResults) {
                    getRecognitionListener()._setOnResults((RecognizerListener.OnResults) item);
                }
                else if (item instanceof RecognizerListener.OnMostConfidentResult) {
                    getRecognitionListener()._setOnMostConfidentResult((RecognizerListener.OnMostConfidentResult) item);
                }
                else if (item instanceof RecognizerListener.OnPartialResults) {
                    getRecognitionListener()._setOnPartialResults((RecognizerListener.OnPartialResults) item);
                }
                else if (item instanceof RecognizerListener.OnError) {
                    getRecognitionListener()._setOnError((RecognizerListener.OnError) item);
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
