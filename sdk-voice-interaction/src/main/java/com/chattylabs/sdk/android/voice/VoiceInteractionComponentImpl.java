package com.chattylabs.sdk.android.voice;

import android.Manifest;
import android.app.Application;
import android.content.Context;
import android.media.AudioManager;

import com.chattylabs.sdk.android.common.internal.ILogger;

import java.lang.ref.SoftReference;

final class VoiceInteractionComponentImpl implements VoiceInteractionComponent {

    // Resources
    private AudioManager audioManager;
    private AndroidAudioHandler audioHandler;
    private BluetoothSco bluetoothSco;
    private VoiceConfiguration voiceConfiguration;
    private SpeechSynthesizer speechSynthesizer;
    private SpeechRecognizer speechRecognizer;
    private PhoneStateHandler phoneStateHandler;

    // Log stuff
    private ILogger logger;

    VoiceInteractionComponentImpl() {
        voiceConfiguration = new VoiceConfiguration.Builder().build();
        Instance.instanceOf = new SoftReference<>(this);
    }

    @Override
    public void setVoiceConfiguration(VoiceConfiguration voiceConfiguration) {
        this.voiceConfiguration = voiceConfiguration;
    }

    @Override
    public void updateVoiceConfiguration(VoiceConfiguration.Update update) {
        update.run(new VoiceConfiguration.Builder(voiceConfiguration));
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
        if (audioManager == null) audioManager = (AudioManager) application.getSystemService(Context.AUDIO_SERVICE);
        if (audioHandler == null) audioHandler = new AndroidAudioHandler(audioManager, voiceConfiguration, logger);
        if (bluetoothSco == null) bluetoothSco = new BluetoothSco(application, audioManager, logger);
        if (speechSynthesizer == null) speechSynthesizer =
                new AndroidSpeechSynthesizer(application, voiceConfiguration, audioHandler, bluetoothSco, logger);
        if (speechRecognizer == null) speechRecognizer = new AndroidSpeechRecognizer(
                application, voiceConfiguration, audioHandler, bluetoothSco,
                () -> android.speech.SpeechRecognizer.createSpeechRecognizer(application), logger);
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
            int speechRecognizerStatus = android.speech.SpeechRecognizer.isRecognitionAvailable(application) ?
                    RECOGNIZER_AVAILABLE : RECOGNIZER_NOT_AVAILABLE;
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
