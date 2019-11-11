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
    private ComponentConfig configuration;
    private AndroidAudioManager audioManager;
    private AndroidBluetooth bluetooth;
    private SpeechSynthesizer speechSynthesizer;
    private SpeechRecognizer speechRecognizer;
    private PhoneStateHandler phoneStateHandler;

    // Log stuff
    private ILogger logger;

    private ConversationalFlowImpl() {
        //noinspection NullableProblems
        this.configuration = new ComponentConfig.Builder()
                .setSpeechLanguage(Locale::getDefault)
                .setBluetoothScoRequired(() -> false)
                .setAudioExclusiveRequiredForSynthesizer(() -> false)
                .setAudioExclusiveRequiredForRecognizer(() -> true)
                .setSpeechDictation(() -> false)
                .build();
        reset();
        Instance.instanceOf = new SoftReference<>(this);
    }

    @Override
    public void updateConfiguration(ComponentConfig.OnUpdate listener) {
        configuration = listener.run(new ComponentConfig.Builder(configuration));
        reset();
    }

    private void reset() {
        shutdown();
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
        if (bluetooth == null) bluetooth = new AndroidBluetooth(application, audioManager, logger);
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
        return new ConversationImpl(getSpeechSynthesizer(context), getSpeechRecognizer(context), logger);
    }

    @Override
    public void stop() {
        if (speechSynthesizer != null) speechSynthesizer.stop();
        if (speechRecognizer != null) speechRecognizer.stop();
        if (phoneStateHandler != null) phoneStateHandler.unregister();
    }

    @Override
    public void shutdown() {
        if (speechSynthesizer != null) speechSynthesizer.shutdown();
        if (speechRecognizer != null) speechRecognizer.shutdown();
        if (phoneStateHandler != null) phoneStateHandler.unregister();
    }
}
