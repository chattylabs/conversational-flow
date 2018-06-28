package com.chattylabs.sdk.android.voice;

import android.support.annotation.Nullable;

import com.chattylabs.sdk.android.common.internal.ILogger;
import com.chattylabs.sdk.android.common.internal.ILoggerImpl;

@dagger.Module
public abstract class VoiceInteractionModule {

    @dagger.Provides
    @dagger.Reusable
    public static VoiceInteractionComponent provideVoiceInteractionComponent(@Nullable ILogger logger) {
        VoiceInteractionComponent component = VoiceInteractionComponent.Instance.getInstanceOf();
        component.setLogger(logger == null ? new ILoggerImpl() : logger);
        return component;
    }
}
