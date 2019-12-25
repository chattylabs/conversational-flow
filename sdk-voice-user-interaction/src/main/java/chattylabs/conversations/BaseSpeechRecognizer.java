package chattylabs.conversations;

import androidx.annotation.CallSuper;

import chattylabs.android.commons.Tag;
import chattylabs.android.commons.internal.ILogger;


/**
 * When implementing from this class you must create a constructor that receives the following parameters
 * <b>in the same order</b>:
 * <p/>
 * <pre>{@code
 * public ChildClassConstructor(Application, ComponentConfig, AndroidAudioManager, AndroidBluetooth, ILogger) {
 *     super(ComponentConfig, AndroidAudioManager, AndroidBluetooth, ILogger);
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
    private static final String TAG = Tag.make("BaseSpeechRecognizer");

    // Minimum constants
    int MIN_VOICE_RECOGNITION_TIME_LISTENING = 3000;

    // Resources
    private final ComponentConfig configuration;
    private final AndroidBluetooth bluetooth;
    private final AndroidAudioManager audioManager;

    // Log stuff
    protected final ILogger logger;

    BaseSpeechRecognizer(ComponentConfig configuration,
                         AndroidAudioManager audioManager,
                         AndroidBluetooth bluetooth,
                         ILogger logger) {
        this.configuration = configuration;
        this.bluetooth = bluetooth;
        this.audioManager = audioManager;
        this.logger = logger;
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
        logger.i(TAG, "- start listening");
        handleListeners(listeners);
        checkForBluetoothScoRequired(this::startListening);
    }

    private void handleListeners(RecognizerListener... listeners) {
        getRecognitionListener().reset();
        if (listeners != null && listeners.length > 0) {
            for (RecognizerListener item : listeners) {
                if (item instanceof RecognizerListener.OnReady) {
                    getRecognitionListener()._setOnReady(
                            params -> {
                                audioManager.requestAudioFocus(null, configuration.isAudioExclusiveRequiredForRecognizer());
                                ((RecognizerListener.OnReady) item).execute(params);
                            });
                }
                else if (item instanceof RecognizerListener.OnResults) {
                    getRecognitionListener()._setOnResults((results, confidences) -> {
                        stop();
                        ((RecognizerListener.OnResults) item).execute(results, confidences);
                    });
                }
                else if (item instanceof RecognizerListener.OnMostConfidentResult) {
                    getRecognitionListener()._setOnMostConfidentResult(result -> {
                        stop();
                        ((RecognizerListener.OnMostConfidentResult) item).execute(result);
                    });
                }
                else if (item instanceof RecognizerListener.OnPartialResults) {
                    getRecognitionListener()._setOnPartialResults((RecognizerListener.OnPartialResults) item);
                }
                else if (item instanceof RecognizerListener.OnError) {
                    getRecognitionListener()._setOnError((error, originalError) -> {
                        stop();
                        ((RecognizerListener.OnError) item).execute(error, originalError);
                    });
                }
            }
        }
    }

    private void checkForBluetoothScoRequired(Runnable runnable) {
        logger.i(TAG, "- is bluetooth Sco required: %s",
                Boolean.toString(configuration.isBluetoothScoRequired()));

        if (bluetooth.isScoOn() || (bluetooth.isDeviceConnected() && configuration.isBluetoothScoRequired())) {
            // Start Bluetooth Sco
            logger.w(TAG, "- waiting for bluetooth Sco connection...");
            bluetooth.startSco(runnable);
        }
        else {
            runnable.run();
        }
    }

    @CallSuper
    @Override
    public void stop() {
        getAudioManager().abandonAudioFocus(getConfiguration().isAudioExclusiveRequiredForRecognizer());
    }
}
