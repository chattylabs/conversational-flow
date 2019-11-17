package chattylabs.conversations;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.media.AudioManager;

import androidx.annotation.RequiresPermission;

import com.chattylabs.android.commons.internal.ILogger;

import java.lang.ref.SoftReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Locale;

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
        shutdown();
    }

    @SuppressLint("MissingPermission")
    @Override
    public void resetConfiguration(Context context) {
        shutdown(() -> {
            reset();
            //noinspection Convert2MethodRef
            this.configuration = new ComponentConfig.Builder()
                    .setSpeechLanguage(() -> Locale.getDefault())
                    .setBluetoothScoRequired(() -> false)
                    .setAudioExclusiveRequiredForSynthesizer(() -> false)
                    .setAudioExclusiveRequiredForRecognizer(() -> true)
                    .setSpeechDictation(() -> false)
                    .setBluetoothScoAudioMode(() -> AudioManager.MODE_IN_COMMUNICATION)
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
        //noinspection unchecked
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
        if (phoneStateHandler == null) phoneStateHandler = new PhoneStateHandler(application, logger);
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

    @Override
    public void checkSpeechSynthesizerStatus(Context context, SynthesizerListener.OnStatusChecked listener) {
        final Application application = (Application) context.getApplicationContext();
        initDependencies(application);
        createSpeechSynthesizerInstance(application);
        speechSynthesizer.checkStatus(listener);
    }

    private void createSpeechSynthesizerInstance(Context context) {
        try {
            if (speechSynthesizer == null) {
                speechSynthesizer = newInstance(configuration.getSynthesizerServiceType(),
                        context, configuration, audioManager, bluetooth, logger);
            }
        } catch (Exception e) {
            logger.logException(e);
            throw new RuntimeException("Did you miss configuring the <addon-speech /> dependency?" +
                    " (" + configuration.getSynthesizerServiceType() + ")" +
                    "\n" + e);
        }
    }

    @Override
    public void checkSpeechRecognizerStatus(Context context, RecognizerListener.OnStatusChecked listener) {
        final Application application = (Application) context.getApplicationContext();
        initDependencies(application);
        createSpeechRecognizerInstance(application);
        speechRecognizer.checkStatus(listener);
    }

    private void createSpeechRecognizerInstance(Context context) {
        try {
            if (speechRecognizer == null) {
                speechRecognizer = newInstance(configuration.getRecognizerServiceType(),
                        context, configuration, audioManager, bluetooth, logger);
            }
        } catch (Exception e) {
            logger.logException(e);
            throw new RuntimeException("Did you miss configuring the <addon-speech /> dependency?" +
                    " (" + configuration.getRecognizerServiceType() + ")" +
                    "\n" + e);
        }
    }

    @Override
    public SpeechSynthesizer getSpeechSynthesizer(Context context) {
        initDependencies((Application) context.getApplicationContext());
        createSpeechSynthesizerInstance(context);
        return speechSynthesizer;
    }

    @SuppressLint("MissingPermission")
    @Override @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    public SpeechRecognizer getSpeechRecognizer(Context context) {
        initDependencies((Application) context.getApplicationContext());
        createSpeechRecognizerInstance(context);
        return speechRecognizer;
    }

    @SuppressLint("MissingPermission")
    @Override @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    public Conversation create(Context context) {
        conversation = new ConversationImpl(getSpeechSynthesizer(context), getSpeechRecognizer(context), logger);
        return conversation;
    }

    @Override
    public void shutdown() {
        shutdown(null);
    }

    @Override
    public void shutdown(Runnable onBluetoothScoDisconnected) {
        if (phoneStateHandler != null) phoneStateHandler.unregister();
        if (speechSynthesizer != null) speechSynthesizer.shutdown();
        if (speechRecognizer != null) speechRecognizer.stop();
        if (bluetooth != null) {
            bluetooth.stopSco(() -> {
                if (audioManager != null) audioManager.abandonAudioFocus();
                if (onBluetoothScoDisconnected != null) onBluetoothScoDisconnected.run();
            });
        } else {
            if (onBluetoothScoDisconnected != null) onBluetoothScoDisconnected.run();
        }
    }
}
