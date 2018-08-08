package com.chattylabs.sdk.android.voice;

import android.support.annotation.NonNull;

public interface ConversationFlowSource {
    ConversationFlowTarget from(@NonNull VoiceNode node);
}
