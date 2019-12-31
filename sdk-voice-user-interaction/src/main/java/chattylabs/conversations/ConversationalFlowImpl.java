package chattylabs.conversations;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.media.AudioManager;

import java.lang.ref.SoftReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Locale;

import chattylabs.android.commons.internal.ILogger;

final class ConversationalFlowImpl implements ConversationalFlow {

    static class Instance {
        static SoftReference<ConversationalFlow> instanceOf;
        static ConversationalFlow get() {
            synchronized (Instance.class) {
                if ((instanceOf == null) || (instanceOf.get() == null))
                {
                    return new ConversationalFlowImpl();
                }
                return instanceOf.get();
            }
        }
        private Instance(){}
    }

    // Resources
    private Conversation conversation;
    private ComponentConfig configuration;
    private AndroidAudioManager audioManager;
    private AndroidBluetooth bluetooth;
    private SpeechSynthesizer speechSynthesizer;
    private SpeechRecognizer speechRecognizer;
    private PhoneStateHandler phoneStateHandler;

    // Log stuff
    private ILogger logger;

    private ConversationalFlowImpl() {
        resetConfiguration(null);
        Instance.instanceOf = new SoftReference<>(this);
    }

    @Override
    public void updateConfiguration(ComponentConfig.OnUpdate listener) {
        ComponentConfig newConfig = listener.run(new ComponentConfig.Builder(this.configuration));
        this.configuration.update(newConfig);
    }

    @SuppressWarnings("NullableProblems")
    @SuppressLint("MissingPermission")
    @Override
    public void resetConfiguration(Context context) {
        shutdown(() -> {
            reset();
            this.configuration = new ComponentConfig.Builder()
                                     .setSpeechLanguage(Locale::getDefault)
                                     .setSpeechDictation(() -> false)
                                     .setBluetoothScoRequired(() -> false)
                                     .setBluetoothScoAudioMode(() -> AudioManager.MODE_IN_COMMUNICATION)
                                     .setCustomBeepEnabled(() -> false)
                                     .setAudioExclusiveRequiredForSynthesizer(() -> false)
                                     .setAudioExclusiveRequiredForRecognizer(() -> true)
                                     .setForceLanguageDetection(() -> false)
                                     .setCustomVolume(() -> 60)
                                     .build();

            if (conversation != null && context != null) {
                ((ConversationImpl) conversation).resetSpeechSynthesizer(getSpeechSynthesizer(context));
                ((ConversationImpl) conversation).resetSpeechRecognizer(getSpeechRecognizer(context));
            }
        });
    }

    private void reset() {
        audioManager = null;
        bluetooth = null;
        speechSynthesizer = null;
        speechRecognizer = null;
    }

    void setLogger(ILogger logger) {
        this.logger = logger;
    }

    @Override
    public String[] requiredPermissions() {
        return new String[]{ Manifest.permission.RECORD_AUDIO };
    }

    private <T> T newInstance(Class cls, Object... parameters) throws
            IllegalAccessException, InvocationTargetException, InstantiationException {
        Constructor constructor = cls.getDeclaredConstructors()[0];
        //noinspection unchecked
        return (T) constructor.newInstance(parameters);
    }

    private void initDependencies(Application application) {
        if (audioManager == null) {
            AudioManager systemAudioManager = (AudioManager) application.getSystemService(Context.AUDIO_SERVICE);
            this.audioManager = new AndroidAudioManager(systemAudioManager, configuration, logger);
        }
        if (bluetooth == null) bluetooth = new AndroidBluetooth(application, audioManager, configuration, logger);
        if (phoneStateHandler == null) {
            phoneStateHandler = new PhoneStateHandler(application, logger);
            phoneStateHandler.register(new PhoneStateListenerAdapter() {
                @Override
                public void onOutgoingCallStarts() {
                    shutdown();
                }

                @Override
                public void onIncomingCallRinging() {
                    shutdown();
                }
            });
        }
    }

    @Override
    public void checkSpeechSynthesizerStatus(Context context, SynthesizerListener.OnStatusChecked listener) {
        final Application application = (Application) context.getApplicationContext();
        initDependencies(application);
        createSpeechSynthesizerInstance(application);
        speechSynthesizer.checkStatus(listener);
    }

    @Override
    public void checkSpeechRecognizerStatus(Context context, RecognizerListener.OnStatusChecked listener) {
        final Application application = (Application) context.getApplicationContext();
        initDependencies(application);
        createSpeechRecognizerInstance(application);
        speechRecognizer.checkStatus(listener);
    }

    private void createSpeechSynthesizerInstance(Context context) {
        try {
            if (speechSynthesizer == null) {
                speechSynthesizer = newInstance(configuration.getSynthesizerServiceType(),
                        context.getApplicationContext(), configuration, audioManager, bluetooth, logger);
            }
        } catch (Exception e) {
            logger.logException(e);
            throw new RuntimeException("Did you miss adding the \"addon-speech:version\" dependency? " +
                    "(" + configuration.getSynthesizerServiceType() + ")", e);
        }
    }

    private void createSpeechRecognizerInstance(Context context) {
        try {
            if (speechRecognizer == null) {
                speechRecognizer = newInstance(configuration.getRecognizerServiceType(),
                        context.getApplicationContext(), configuration, audioManager, bluetooth, logger);
            }
        } catch (Exception e) {
            logger.logException(e);
            throw new RuntimeException("Did you miss adding the \"addon-speech:version\" dependency? " +
                    "(" + configuration.getRecognizerServiceType() + ")", e);
        }
    }

    @Override
    public SpeechSynthesizer getSpeechSynthesizer(Context context) {
        initDependencies((Application) context.getApplicationContext());
        createSpeechSynthesizerInstance(context);
        return speechSynthesizer;
    }

    @SuppressLint("MissingPermission")
    @Override
    public SpeechRecognizer getSpeechRecognizer(Context context) {
        initDependencies((Application) context.getApplicationContext());
        createSpeechRecognizerInstance(context);
        return speechRecognizer;
    }

    @Override
    public void loadSynthesizerInstallation(Activity activity, SynthesizerListener.OnStatusChecked listener) {
        final Application application = (Application) activity.getApplicationContext();
        initDependencies(application);
        createSpeechSynthesizerInstance(application);
        speechSynthesizer.loadInstallation(activity, listener);
    }

    @Override public AndroidAudioManager getAudioManager(Context context) {
        final Application application = (Application) context.getApplicationContext();
        initDependencies(application);
        return audioManager;
    }

    public Conversation create(Context context) {
        logger.i(TAG, "----- Create");
        conversation = new ConversationImpl(context.getApplicationContext(),
                                            getSpeechSynthesizer(context),
                                            getSpeechRecognizer(context), logger);
        return conversation;
    }

    @Override
    public void shutdown() {
        shutdown(null);
    }

    @Override
    public void shutdown(Runnable onBluetoothScoDisconnected) {
        if (phoneStateHandler != null) {
            phoneStateHandler.unregister();
        }
        if (speechSynthesizer != null) {
            speechSynthesizer.shutdown();
        }
        if (speechRecognizer != null) {
            speechRecognizer.stop();
        }
        if (bluetooth != null) {
            bluetooth.stopSco(() -> {
                if (audioManager != null) {
                    audioManager.abandonAudioFocus(configuration.isAudioExclusiveRequiredForRecognizer());
                    audioManager.abandonAudioFocus(configuration.isAudioExclusiveRequiredForSynthesizer());
                }
                if (onBluetoothScoDisconnected != null) onBluetoothScoDisconnected.run();
            });
        } else {
            if (audioManager != null) {
                audioManager.abandonAudioFocus(configuration.isAudioExclusiveRequiredForRecognizer());
                audioManager.abandonAudioFocus(configuration.isAudioExclusiveRequiredForSynthesizer());
            }
            if (onBluetoothScoDisconnected != null) onBluetoothScoDisconnected.run();
        }
    }

    @Override public void showVolumeControls() {
        audioManager.showVolumeControls();
    }
}
