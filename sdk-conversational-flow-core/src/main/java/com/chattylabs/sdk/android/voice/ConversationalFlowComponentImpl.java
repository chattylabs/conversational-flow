package com.chattylabs.sdk.android.voice;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.speech.SpeechRecognizer;
import android.support.annotation.RequiresPermission;
import android.support.v4.content.ContextCompat;

import com.chattylabs.sdk.android.common.internal.ILogger;

import java.lang.ref.SoftReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

final class ConversationalFlowComponentImpl implements ConversationalFlowComponent {


    static class Instance {
        static SoftReference<ConversationalFlowComponent> instanceOf;
        static ConversationalFlowComponent get() {
            synchronized (Instance.class) {
                if ((instanceOf == null) || (instanceOf.get() == null))
                {
                    return new ConversationalFlowComponentImpl();
                }
                return instanceOf.get();
            }
        }
        private Instance(){}
    }

    // Resources
    private ComponentConfig configuration;
    private AndroidAudioHandler audioHandler;
    private BluetoothSco bluetoothSco;
    private SpeechSynthesizer speechSynthesizer;
    private SpeechRecognizer speechRecognizer;
    private PhoneStateHandler phoneStateHandler;

    // Log stuff
    private ILogger logger;

    ConversationalFlowComponentImpl() {
        configuration = new ComponentConfig.Builder()
                .setBluetoothScoRequired(() -> false)
                .setRecognizerServiceType(() -> ComponentConfig.RECOGNIZER_SERVICE_ANDROID_BUILTIN)
                .setSynthesizerServiceType(() -> ComponentConfig.SYNTHESIZER_SERVICE_ANDROID_BUILTIN)
                .setAudioExclusiveRequiredForSynthesizer(() -> false)
                .setAudioExclusiveRequiredForRecognizer(() -> true)
                .build();
        Instance.instanceOf = new SoftReference<>(this);
    }

    @Override
    public void setConfiguration(ComponentConfig configuration) {
        this.configuration = configuration;
        reset();
    }

    @Override
    public void updateConfiguration(ComponentConfig.Update update) {
        configuration = update.run(new ComponentConfig.Builder(configuration));
        reset();
    }

    @Override
    public void reset() {
        audioHandler = null;
        bluetoothSco = null;
        release();
    }

    private void release() {
        if (speechSynthesizer != null) {
            speechSynthesizer.release();
            speechSynthesizer = null;
        }
        if (speechRecognizer != null) {
            speechRecognizer.release();
            speechRecognizer = null;
        }
    }

    void setLogger(ILogger logger) {
        this.logger = logger;
    }

    @Override
    public String[] requiredPermissions() {
        return new String[]{Manifest.permission.RECORD_AUDIO};
    }

    private <T> T newInstance(String className, Object... parameters) throws
            ClassNotFoundException, IllegalAccessException,
            InvocationTargetException, InstantiationException {
        Class cls = Class.forName(className);
        //noinspection unchecked
        Constructor constructor = cls.getConstructors()[0];
        //noinspection unchecked
        return (T) constructor.newInstance(parameters);
    }

    private void init(Application application) {
        String[] perms = requiredPermissions();
        for (String perm : perms)
            if (ContextCompat.checkSelfPermission(application, perm) != PackageManager.PERMISSION_GRANTED)
                throw new IllegalAccessError("Permission \"" + perm + "\" is not granted");
        if (audioHandler == null) {
            AudioManager audioManager = (AudioManager) application.getSystemService(Context.AUDIO_SERVICE);
            audioHandler = new AndroidAudioHandler(audioManager, configuration, logger);
        }
        if (bluetoothSco == null) bluetoothSco = new BluetoothSco(application, audioHandler, logger);
        try {
            if (speechSynthesizer == null) {
                if (configuration.getSynthesizerServiceType() == ComponentConfig.SYNTHESIZER_SERVICE_ANDROID_BUILTIN) {
                    String className = "com.chattylabs.sdk.android.voice.AndroidSpeechSynthesizer";
                    speechSynthesizer = newInstance(className, application, configuration, audioHandler,
                            bluetoothSco, logger);
                }
                else if (configuration.getSynthesizerServiceType() == ComponentConfig.SYNTHESIZER_SERVICE_GOOGLE_BUILTIN) {
                    String className = "com.chattylabs.sdk.android.voice.GoogleSpeechSynthesizer";
                    speechSynthesizer = newInstance(className, application, configuration, audioHandler,
                            bluetoothSco, logger);
                }
            }
            if (speechRecognizer == null) {
                if (configuration.getRecognizerServiceType() == ComponentConfig.RECOGNIZER_SERVICE_ANDROID_BUILTIN) {
                    String className = "com.chattylabs.sdk.android.voice.AndroidSpeechRecognizer";
                    speechRecognizer = newInstance(className, application,
                            configuration, audioHandler, bluetoothSco, (SpeechRecognizerCreator) () ->
                                    android.speech.SpeechRecognizer.createSpeechRecognizer(application),
                            logger);
                }
                else if (configuration.getRecognizerServiceType() == ComponentConfig.RECOGNIZER_SERVICE_GOOGLE_SPEECH) {
                    String className = "com.chattylabs.sdk.android.voice.google.GoogleSpeechRecognizer";
                    speechRecognizer = newInstance(className, application, configuration, audioHandler,
                            bluetoothSco, logger);
                }
            }
        } catch (ClassNotFoundException | IllegalAccessException
                | InstantiationException | InvocationTargetException e) {
            logger.logException(e); throw new RuntimeException("Have you forgot to add addon the dependency?");
        }
        if (phoneStateHandler == null) phoneStateHandler = new PhoneStateHandler(application, logger);
        if (!phoneStateHandler.isPhoneStateReceiverRegistered()) {
            phoneStateHandler.registerReceiver(new PhoneStateListenerAdapter() {
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
    public void setup(Context context, OnSetup onSetup) {
        final Application application = (Application) context.getApplicationContext();
        init(application);
        speechSynthesizer.setup(synthesizerStatus -> {
            int androidRecognizerStatus = android.speech.SpeechRecognizer.isRecognitionAvailable(application) ?
                    RecognizerListener.RECOGNIZER_AVAILABLE : RecognizerListener.RECOGNIZER_NOT_AVAILABLE;
            int speechRecognizerStatus = configuration.getRecognizerServiceType() ==
                    ComponentConfig.RECOGNIZER_SERVICE_GOOGLE_SPEECH ?
                    RecognizerListener.RECOGNIZER_AVAILABLE : androidRecognizerStatus;
            onSetup.execute(new Status() {
                @SuppressLint("MissingPermission")
                @Override
                public boolean isAvailable() {
                    return synthesizerStatus == SynthesizerListener.AVAILABLE &&
                           speechRecognizerStatus == RecognizerListener.RECOGNIZER_AVAILABLE;
                }

                @SuppressLint("MissingPermission")
                @Override
                public int getSynthesizerStatus() {
                    return synthesizerStatus;
                }

                @SuppressLint("MissingPermission")
                @Override
                public int getRecognizerStatus() {
                    return speechRecognizerStatus;
                }
            });
        });
    }

    @Override
    public ConversationalFlowComponent.SpeechSynthesizer getSpeechSynthesizer(Context context) {
        init((Application) context.getApplicationContext());
        return speechSynthesizer;
    }

    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    @Override
    public ConversationalFlowComponent.SpeechRecognizer getSpeechRecognizer(Context context) {
        init((Application) context.getApplicationContext());
        return speechRecognizer;
    }

    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    @Override
    public Conversation create(Context context) {
        return new ConversationImpl(getSpeechSynthesizer(context), getSpeechRecognizer(context), logger);
    }

    @Override
    public void stop() {
        if (speechSynthesizer != null) speechSynthesizer.stop();
        if (speechRecognizer != null) speechRecognizer.stop();
        if (phoneStateHandler != null) phoneStateHandler.unregisterReceiver();
    }

    @Override
    public void shutdown() {
        if (speechSynthesizer != null) speechSynthesizer.shutdown();
        if (speechRecognizer != null) speechRecognizer.shutdown();
        if (phoneStateHandler != null) phoneStateHandler.unregisterReceiver();
        release();
    }
}
