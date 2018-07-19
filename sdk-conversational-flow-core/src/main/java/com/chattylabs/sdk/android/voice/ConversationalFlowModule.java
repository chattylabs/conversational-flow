package com.chattylabs.sdk.android.voice;

import com.chattylabs.sdk.android.common.internal.ILogger;

@dagger.Module
public abstract class ConversationalFlowModule {

    @dagger.Provides
    @dagger.Reusable
    public static ConversationalFlowComponent provideComponent(ILogger logger) {
        ConversationalFlowComponent component = ConversationalFlowComponentImpl.Instance.get();
        ((ConversationalFlowComponentImpl)component).setLogger(logger);
        return component;
    }
}
