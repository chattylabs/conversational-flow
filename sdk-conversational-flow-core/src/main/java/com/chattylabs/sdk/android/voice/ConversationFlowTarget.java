package com.chattylabs.sdk.android.voice;

import android.support.annotation.NonNull;

public interface ConversationFlowTarget {
    void to(@NonNull VoiceNode node, VoiceNode... optNodes);
}
