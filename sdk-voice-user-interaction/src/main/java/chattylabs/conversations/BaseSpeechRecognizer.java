package chattylabs.conversations;

import android.text.TextUtils;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

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

    @Override
    public String selectMostConfidentResult(@NonNull ArrayList<String> results, float[] confidences) {
        String message = null;
        if (!results.isEmpty()) {
            float last = 0;
            if (confidences != null && confidences.length > 0) {
                for (int a = 0; a < confidences.length; a++) {
                    if (confidences[a] >= last) {
                        last = confidences[a];
                        message = results.get(a);
                    }
                }
            }
            else {
                message = results.get(0);
            }
        } else {
            throw new NullPointerException("results can't be empty");
        }
        return message;
    }

    @Override
    public boolean anyMatch(@NonNull List<String> data, @NonNull List<String> expected) {
        if (!expected.isEmpty()) {
            String expectedJoined = TextUtils.join("|", expected);
            if (data.size() > 1) {
                for (String str : data) {
                    if (matches(str, expectedJoined)) {
                        return true;
                    }
                }
            } else {
                return matches(data.get(0), expectedJoined);
            }
        }
        return false;
    }

    @Override
    public boolean matches(@NonNull String str, @NonNull String patternStr) {
        return Pattern.compile("\\b(" + patternStr + ")\\b", Pattern.CASE_INSENSITIVE)
                      .matcher(str).find();
    }

    @CallSuper
    @Override
    public void listen(RecognizerListener... listeners) {
        logger.i(TAG, "- start listening");
        boolean granted = getAudioManager().requestAudioFocus(focusChange -> {}, getConfiguration().isAudioExclusiveRequiredForRecognizer());
        if (granted) {
            handleListeners(listeners);
            checkForBluetoothScoRequired(this::startListening);
        }
    }

    private void handleListeners(RecognizerListener... listeners) {
        getRecognitionListener().reset();
        if (listeners != null && listeners.length > 0) {
            for (RecognizerListener item : listeners) {
                if (item instanceof RecognizerListener.OnReady) {
                    getRecognitionListener()._setOnReady((RecognizerListener.OnReady) item);
                }
                else if (item instanceof RecognizerListener.OnResults) {
                    getRecognitionListener()._setOnResults(((RecognizerListener.OnResults) item));
                }
                else if (item instanceof RecognizerListener.OnMostConfidentResult) {
                    getRecognitionListener()._setOnMostConfidentResult(((RecognizerListener.OnMostConfidentResult) item));
                }
                else if (item instanceof RecognizerListener.OnPartialResults) {
                    getRecognitionListener()._setOnPartialResults((RecognizerListener.OnPartialResults) item);
                }
                else if (item instanceof RecognizerListener.OnError) {
                    getRecognitionListener()._setOnError(((RecognizerListener.OnError) item));
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
