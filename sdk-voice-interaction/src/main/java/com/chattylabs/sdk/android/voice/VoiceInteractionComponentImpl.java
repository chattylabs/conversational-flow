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

final class VoiceInteractionComponentImpl implements VoiceInteractionComponent {

    static class Instance {
        static SoftReference<VoiceInteractionComponent> instanceOf;
        static VoiceInteractionComponent get() {
            synchronized (Instance.class) {
                if ((instanceOf == null) || (instanceOf.get() == null))
                {
                    return new VoiceInteractionComponentImpl();
                }
                return instanceOf.get();
            }
        }
        private Instance(){}
    }

    // Resources
    private AudioManager audioManager;
    private AndroidAudioHandler audioHandler;
    private BluetoothSco bluetoothSco;
    private VoiceConfig voiceConfig;
    private SpeechSynthesizer speechSynthesizer;
    private SpeechRecognizer speechRecognizer;
    private PhoneStateHandler phoneStateHandler;

    // Log stuff
    private ILogger logger;

    VoiceInteractionComponentImpl() {
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
        speechSynthesizer = null;
        speechRecognizer = null;
    }

    @Override
    public void setLogger(ILogger logger) {
        this.logger = logger;
    }

    @Override
    public String[] requiredPermissions() {
        return new String[]{Manifest.permission.RECORD_AUDIO};
    }

    private void init(Application application) {
        String[] perms = requiredPermissions();
        for (String perm : perms)
            if (ContextCompat.checkSelfPermission(application, perm) != PackageManager.PERMISSION_GRANTED)
                throw new IllegalAccessError("Permission \"" + perm + "\" was not granted.");
        if (audioManager == null) audioManager = (AudioManager) application.getSystemService(Context.AUDIO_SERVICE);
        if (audioHandler == null) audioHandler = new AndroidAudioHandler(audioManager, voiceConfig, logger);
        if (bluetoothSco == null) bluetoothSco = new BluetoothSco(application, audioManager, logger);
        if (speechSynthesizer == null) {
            speechSynthesizer =
                    new AndroidSpeechSynthesizer(application, voiceConfig, audioHandler, bluetoothSco, logger);
        }
        if (speechRecognizer == null) {
            if (voiceConfig.getRecognizerServiceType() == VoiceConfig.RECOGNIZER_SERVICE_ANDROID_BUILTIN)
                speechRecognizer = new AndroidSpeechRecognizer(
                        application, voiceConfig, audioHandler, bluetoothSco,
                        () -> android.speech.SpeechRecognizer.createSpeechRecognizer(application), logger);
            else if (voiceConfig.getRecognizerServiceType() == VoiceConfig.RECOGNIZER_SERVICE_GOOGLE_SPEECH) {
                String className = "com.chattylabs.sdk.android.voice.addon.google.GoogleSpeechRecognizer";
                try {
                    Class cls = Class.forName(className);
                    Constructor constructor = cls.getConstructor(Application.class,
                            VoiceConfig.class,
                            AndroidAudioHandler.class,
                            BluetoothSco.class, ILogger.class);
                    speechRecognizer = (SpeechRecognizer) constructor.newInstance(application,
                            voiceConfig, audioHandler, bluetoothSco, logger);
                } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException
                        | InstantiationException | InvocationTargetException e) {
                    logger.logException(e);
                    throw new RuntimeException("Have you forgot to add the GoogleSpeechRecognizer dependency?");
                }
            }
        }
        if (phoneStateHandler == null) phoneStateHandler = new PhoneStateHandler(application, logger);
        if (!phoneStateHandler.isPhoneStateReceiverRegistered()) {
            phoneStateHandler.registerReceiver(new PhoneStateListener() {
                @Override
                public void onOutgoingCallStarts() {
                    shutdown();
                }

                @Override
                public void onIncomingCallRinging() {
                    shutdown();
                }

                @Override
                public void onOutgoingCallEnds() {
                }

                @Override
                public void onIncomingCallEnds() {
                }
            });
        }
    }

    @Override
    public void setup(Context context, OnSetup onSetup) {
        Application application = (Application) context.getApplicationContext();
        init(application);
        speechSynthesizer.setup(application, synthesizerStatus -> {
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
    public VoiceInteractionComponent.SpeechSynthesizer getSpeechSynthesizer(Context context) {
        init((Application) context.getApplicationContext());
        return speechSynthesizer;
    }

    @Override
    public VoiceInteractionComponent.SpeechRecognizer getSpeechRecognizer(Context context) {
        init((Application) context.getApplicationContext());
        return speechRecognizer;
    }

    @Override
    public Conversation createConversation(Context context) {
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
