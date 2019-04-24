package chattylabs.conversations;

import androidx.annotation.CallSuper;

import com.chattylabs.android.commons.internal.ILogger;


/**
 * When implementing from this class you must create a constructor that receives the following parameters
 * <b>in the same order</b>:
 * <p/>
 * <pre>{@code
 * public ChildClassConstructor(Application, ComponentConfig, AndroidAudioManager, BluetoothSco, ILogger) {
 *     super(ComponentConfig, AndroidAudioManager, BluetoothSco, ILogger, String);
 *     //...
 * }
 * }</pre>
 * Otherwise the addon initialization will throw and Exception on runtime.
 *
 * @see SpeechRecognizer
 * @see android.app.Application
 * @see ComponentConfig
 * @see AndroidAudioManager
 * @see BluetoothSco
 * @see ILogger
 */
abstract class BaseSpeechRecognizer implements SpeechRecognizer {
    // Minimum constants
    int MIN_VOICE_RECOGNITION_TIME_LISTENING = 2000;

    // Resources
    private final ComponentConfig configuration;
    private final BluetoothSco bluetoothSco;
    private final AndroidAudioManager audioManager;

    // Log stuff
    protected final ILogger logger;
    private final String tag;

    BaseSpeechRecognizer(ComponentConfig configuration,
                         AndroidAudioManager audioManager,
                         BluetoothSco bluetoothSco,
                         ILogger logger,
                         String tag) {
        this.configuration = configuration;
        this.bluetoothSco = bluetoothSco;
        this.audioManager = audioManager;
        this.logger = logger;
        this.tag = tag;
    }

    abstract RecognizerUtteranceListener getRecognitionListener();

    abstract void startListening();

    public ComponentConfig getConfiguration() {
        return configuration;
    }

    void requestAudioFocus() {
        if (!bluetoothSco.isBluetoothScoOn()) {
            audioManager.requestAudioFocus(
                    getConfiguration().isAudioExclusiveRequiredForRecognizer());
        }
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
    public void listen(RecognizerListener... listeners) {
        logger.i(tag, "SPEECH - start listening");
        handleListeners(listeners);
        checkForBluetoothScoRequired(this::startListening);
    }

    @CallSuper
    @Override
    public void stop() {
        bluetoothSco.stopSco();
        audioManager.abandonAudioFocus();
        getRecognitionListener().reset();
    }

    @CallSuper
    @Override
    public void cancel() {
        bluetoothSco.stopSco();
        audioManager.abandonAudioFocus();
        getRecognitionListener().reset();
    }

    @CallSuper
    @Override
    public void shutdown() {
        bluetoothSco.stopSco();
        audioManager.abandonAudioFocus();
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
        logger.i(tag, "SPEECH - is bluetooth Sco required: %s",
                Boolean.toString(configuration.isBluetoothScoRequired()));
        if (configuration.isBluetoothScoRequired() && !bluetoothSco.isBluetoothScoOn()) {
            // Sco Listener
            BluetoothScoListener listener = new BluetoothScoListener() {
                @Override
                public void onConnected() {
                    logger.w(tag, "SPEECH - Sco onConnected");
                    runnable.run();
                }

                @Override
                public void onDisconnected() {
                    logger.w(tag, "SPEECH - Sco onDisconnected");
                    if (bluetoothSco.isBluetoothScoOn()) {
                        logger.w(tag, "SPEECH - shutdown from Sco");
                        shutdown();
                    }
                }
            };
            // Start Bluetooth Sco
            bluetoothSco.startSco(listener);
            logger.v(tag, "SPEECH - waiting for bluetooth sco connection");
        }
        else {
            logger.v(tag, "SPEECH - bluetooth sco is: %s", Boolean.toString(bluetoothSco.isBluetoothScoOn()));
            runnable.run();
        }
    }
}
