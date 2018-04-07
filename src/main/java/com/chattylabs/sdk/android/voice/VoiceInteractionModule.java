package com.chattylabs.sdk.android.voice;

@dagger.Module
public abstract class VoiceInteractionModule {

    @dagger.Provides
    @dagger.Reusable
    public static VoiceInteractionComponent provideVoiceInteractionComponent() {
        return VoiceInteractionComponent.Instance.getInstanceOf();
    }
}
