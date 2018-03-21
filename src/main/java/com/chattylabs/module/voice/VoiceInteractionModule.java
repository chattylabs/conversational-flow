package com.chattylabs.module.voice;

@dagger.Module
public abstract class VoiceInteractionModule {

    @dagger.Provides
    @dagger.Reusable
    public static VoiceInteractionComponent provideVoiceInteractionComponent() {
        return VoiceInteractionComponent.Instance.getInstanceOf();
    }
}
