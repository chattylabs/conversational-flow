package com.chattylabs.sdk.android.voice;

import android.Manifest;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.support.v4.content.ContextCompat;

import com.chattylabs.sdk.android.common.internal.ILogger;

import java.lang.ref.SoftReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;

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
    private AndroidAudioHandler audioHandler;
    private BluetoothSco bluetoothSco;
    private VoiceConfig voiceConfig;
    private SpeechSynthesizer speechSynthesizer;
    private SpeechRecognizer speechRecognizer;
    private PhoneStateHandler phoneStateHandler;

    // Log stuff
    private ILogger logger;

    ConversationalFlowComponentImpl() {
        voiceConfig = new VoiceConfig.Builder()
                .setBluetoothScoRequired(() -> false)
                .setRecognizerServiceType(() -> VoiceConfig.RECOGNIZER_SERVICE_ANDROID_BUILTIN)
                .setGoogleAccessToken(() -> () -> null)
                .setAudioExclusiveRequiredForSynthesizer(() -> false)
                .setAudioExclusiveRequiredForRecognizer(() -> true)
                .build();
        Instance.instanceOf = new SoftReference<>(this);
    }

    @Override
    public void setVoiceConfig(VoiceConfig voiceConfig) {
        this.voiceConfig = voiceConfig;
    }

    @Override
    public void updateVoiceConfig(VoiceConfig.Update update) {
        voiceConfig = update.run(new VoiceConfig.Builder(voiceConfig));
        audioHandler = null;
        bluetoothSco = null;
        speechSynthesizer = null;
        speechRecognizer = null;
    }

    void setLogger(ILogger logger) {
        this.logger = logger;
    }

    @Override
    public String[] requiredPermissions() {
        return new String[]{Manifest.permission.RECORD_AUDIO};
    }

    private <T> T newInstance(String className, Object... parameters) throws
            ClassNotFoundException, NoSuchMethodException, IllegalAccessException,
            InvocationTargetException, InstantiationException {
        Class cls = Class.forName(className);
        Class<?>[] cs = new Class[parameters.length];
        for (int a = 0; a < parameters.length; a++) cs[a] = parameters[a].getClass();
        //noinspection unchecked
        Constructor constructor = cls.getConstructor(cs);
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
            audioHandler = new AndroidAudioHandler(audioManager, voiceConfig, logger);
        }
        if (bluetoothSco == null) bluetoothSco = new BluetoothSco(application, audioHandler, logger);
        try {
            if (speechSynthesizer == null) {
                if (voiceConfig.getSynthesizerServiceType() == VoiceConfig.SYNTHESIZER_SERVICE_ANDROID_BUILTIN) {
                    String className = "com.chattylabs.sdk.android.voice.AndroidSpeechSynthesizer";
                    speechSynthesizer = newInstance(className, application, voiceConfig, audioHandler, bluetoothSco, logger);
                }
                else if (voiceConfig.getSynthesizerServiceType() == VoiceConfig.SYNTHESIZER_SERVICE_GOOGLE_BUILTIN) {
                    String className = "com.chattylabs.sdk.android.voice.GoogleSpeechSynthesizer";
                }
            }
            if (speechRecognizer == null) {
                if (voiceConfig.getRecognizerServiceType() == VoiceConfig.RECOGNIZER_SERVICE_ANDROID_BUILTIN) {
                    String className = "com.chattylabs.sdk.android.voice.AndroidSpeechRecognizer";
                    speechRecognizer = newInstance(className, application,
                            voiceConfig, audioHandler, bluetoothSco, (SpeechRecognizerCreator) () ->
                                android.speech.SpeechRecognizer.createSpeechRecognizer(application), logger);
                }
                else if (voiceConfig.getRecognizerServiceType() == VoiceConfig.RECOGNIZER_SERVICE_GOOGLE_SPEECH) {
                    String className = "com.chattylabs.sdk.android.voice.google.GoogleSpeechRecognizer";
                    speechRecognizer = newInstance(className, application, voiceConfig, audioHandler, bluetoothSco, logger);
                }
            }
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException
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
        Application application = (Application) context.getApplicationContext();
        init(application);
        speechSynthesizer.setup(synthesizerStatus -> {
            int androidRecognizerStatus = android.speech.SpeechRecognizer.isRecognitionAvailable(application) ?
                    RECOGNIZER_AVAILABLE : RECOGNIZER_NOT_AVAILABLE;
            int speechRecognizerStatus = voiceConfig.getRecognizerServiceType() ==
                    VoiceConfig.RECOGNIZER_SERVICE_GOOGLE_SPEECH ? RECOGNIZER_AVAILABLE : androidRecognizerStatus;
            onSetup.execute(new Status() {
                @Override
                public boolean isAvailable() {
                    return synthesizerStatus == SYNTHESIZER_AVAILABLE &&
                           speechRecognizerStatus == RECOGNIZER_AVAILABLE;
                }

                @Override
                public int getSynthesizerStatus() {
                    return synthesizerStatus;
                }

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

    @Override
    public ConversationalFlowComponent.SpeechRecognizer getSpeechRecognizer(Context context) {
        init((Application) context.getApplicationContext());
        return speechRecognizer;
    }

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
    }
}
