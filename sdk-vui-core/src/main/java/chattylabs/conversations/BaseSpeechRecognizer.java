package chattylabs.conversations;

import androidx.annotation.CallSuper;

import com.chattylabs.android.commons.internal.ILogger;


/**
 * When implementing from this class you must create a constructor that receives the following parameters
 * <b>in the same order</b>:
 * <p/>
 * <pre>{@code
 * public ChildClassConstructor(Application, ComponentConfig, AndroidAudioManager, AndroidBluetooth, ILogger) {
 *     super(ComponentConfig, AndroidAudioManager, AndroidBluetooth, ILogger, String);
 *     //...
 * }
 * }</pre>
 * Otherwise the addon initialization will throw and Exception on runtime.
 *
 * @see SpeechRecognizer
 * @see android.app.Application
 * @see ComponentConfig
 * @see AndroidAudioManager
 * @see AndroidBluetooth
 * @see ILogger
 */
abstract class BaseSpeechRecognizer implements SpeechRecognizer {
    // Minimum constants
    int MIN_VOICE_RECOGNITION_TIME_LISTENING = 3000;

    // Resources
    private final ComponentConfig configuration;
    private final AndroidBluetooth bluetooth;
    private final AndroidAudioManager audioManager;

    // Log stuff
    protected final ILogger logger;
    private final String tag;

    BaseSpeechRecognizer(ComponentConfig configuration,
                         AndroidAudioManager audioManager,
                         AndroidBluetooth bluetooth,
                         ILogger logger,
                         String tag) {
        this.configuration = configuration;
        this.bluetooth = bluetooth;
        this.audioManager = audioManager;
        this.logger = logger;
        this.tag = tag;
    }

    abstract RecognizerUtteranceListener getRecognitionListener();

    abstract void startListening();

    public AndroidAudioManager getAudioManager() {
        return audioManager;
    }

    public ComponentConfig getConfiguration() {
        return configuration;
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

        if (bluetooth.isScoOn() || (bluetooth.isDeviceConnected() && configuration.isBluetoothScoRequired())) {
            // Start Bluetooth Sco
            logger.w(tag, "waiting for bluetooth Sco connection...");
            bluetooth.startSco(runnable);
        }
        else {
            audioManager.requestAudioFocus(null, configuration.isAudioExclusiveRequiredForRecognizer());
            runnable.run();
        }
    }

    @CallSuper
    @Override
    public void stop() {}
}
