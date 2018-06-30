package com.chattylabs.sdk.android.voice;

import com.chattylabs.sdk.android.common.internal.ILogger;

@dagger.Module
public abstract class VoiceInteractionModule {

    @dagger.Provides
    @dagger.Reusable
    public static VoiceInteractionComponent provideVoiceInteractionComponent(ILogger logger) {
        VoiceInteractionComponent component = VoiceInteractionComponent.Instance.getInstanceOf();
        component.setLogger(logger);
        return component;
    }
}
