package com.chattylabs.module.voice;

import android.Manifest;
import android.app.Application;
import android.content.Context;
import android.speech.SpeechRecognizer;

import com.chattylabs.module.core.Preconditions;

import java.lang.ref.SoftReference;

import javax.inject.Inject;

@dagger.Reusable
final class VoiceInteractionComponentImpl implements VoiceInteractionComponent {

    private TextToSpeechManager textToSpeechManager;
    private VoiceRecognitionManager voiceRecognitionManager;

    @Inject
    public VoiceInteractionComponentImpl() {
        Instance.instanceOf = new SoftReference<>(this);
    }

    @Override
    public String[] requiredPermissions() {
        return new String[]{Manifest.permission.RECORD_AUDIO};
    }

    private void init(Application application) {
        if (textToSpeechManager == null) textToSpeechManager = new TextToSpeechManager(application);
        if (voiceRecognitionManager == null) voiceRecognitionManager = new VoiceRecognitionManager(
                application, () -> SpeechRecognizer.createSpeechRecognizer(application));
    }

    @Override
    public void setup(Context context, OnSetupListener onSetupListener) {
        Application application = (Application) context.getApplicationContext();
        init(application);
        textToSpeechManager.check(textToSpeechStatus -> {
            int speechRecognizerStatus = SpeechRecognizer.isRecognitionAvailable(application) ?
                                         VOICE_RECOGNITION_AVAILABLE : VOICE_RECOGNITION_NOT_AVAILABLE;
            onSetupListener.execute(new VoiceInteractionStatus() {
                @Override
                public boolean isAvailable() {
                    return textToSpeechStatus == TEXT_TO_SPEECH_AVAILABLE &&
                           speechRecognizerStatus == VOICE_RECOGNITION_AVAILABLE;
                }

                @Override
                public int getTextToSpeechStatus() {
                    return textToSpeechStatus;
                }

                @Override
                public int getSpeechRecognizerStatus() {
                    return speechRecognizerStatus;
                }
            });
        });
    }

    @Override
    public void addSpeechFilter(Context context, MessageFilter filter) {
        init((Application) context.getApplicationContext());
        textToSpeechManager.addFilter(filter);
    }

    @Override
    public void setBluetoothScoRequired(Context context, boolean required) {
        init((Application) context.getApplicationContext());
        textToSpeechManager.setBluetoothScoRequired(required);
    }

    @Override
    public <T extends TextToSpeechListeners> void play(Context context, String text, String groupId, T... listeners) {
        init((Application) context.getApplicationContext());
        textToSpeechManager.speak(text, groupId, listeners);
    }

    @Override
    public <T extends TextToSpeechListeners> void playSilence(Context context, long durationInMillis, String groupId, T... listeners) {
        init((Application) context.getApplicationContext());
        textToSpeechManager.playSilence(durationInMillis, groupId, listeners);
    }

    @Override
    public <T extends TextToSpeechListeners> void play(Context context, String text, T... listeners) {
        init((Application) context.getApplicationContext());
        textToSpeechManager.speak(text, listeners);
    }

    @Override
    public <T extends TextToSpeechListeners> void playSilence(Context context, long durationInMillis, T... listeners) {
        init((Application) context.getApplicationContext());
        textToSpeechManager.playSilence(durationInMillis, listeners);
    }
                                                       @Override
    public <T extends VoiceRecognitionListeners> void listen(Context context, T... listeners) {
        init((Application) context.getApplicationContext());
        voiceRecognitionManager.start(listeners);
    }

    @Override
    public boolean hasNextSpeech() {
        Preconditions.checkNotNull(textToSpeechManager);
        return !textToSpeechManager.isGroupQueueEmpty();
    }

    @Override
    public boolean isSpeechPaused() {
        Preconditions.checkNotNull(textToSpeechManager);
        return textToSpeechManager.isPaused();
    }

    @Override
    public String lastSpeechGroup() {
        Preconditions.checkNotNull(textToSpeechManager);
        return textToSpeechManager.getLastGroup();
    }

    @Override
    public String speechGroup() {
        Preconditions.checkNotNull(textToSpeechManager);
        return textToSpeechManager.getGroupId();
    }

    @Override
    public void pauseSpeech() {
        Preconditions.checkNotNull(textToSpeechManager);
        textToSpeechManager.doPause();
    }

    @Override
    public void releaseSpeech() {
        Preconditions.checkNotNull(textToSpeechManager);
        textToSpeechManager.releasePause();
    }

    @Override
    public void resumeSpeech() {
        Preconditions.checkNotNull(textToSpeechManager);
        textToSpeechManager.resume();
    }

    @Override
    public void stop() {
        if (textToSpeechManager != null) textToSpeechManager.stop();
        if (voiceRecognitionManager != null) voiceRecognitionManager.cancel();
    }

    @Override
    public void shutdown() {
        if (textToSpeechManager != null) textToSpeechManager.shutdown();
        if (voiceRecognitionManager != null) voiceRecognitionManager.shutdown();
    }

    @Override
    public void shutdownTextToSpeech() {
        if (textToSpeechManager != null) textToSpeechManager.shutdown();
    }

    @Override
    public void shutdownVoiceRecognition() {
        if (voiceRecognitionManager != null) voiceRecognitionManager.shutdown();
    }

    @Override
    public void cancelVoiceRecognition() {
        if (voiceRecognitionManager != null) voiceRecognitionManager.cancel();
    }
}
