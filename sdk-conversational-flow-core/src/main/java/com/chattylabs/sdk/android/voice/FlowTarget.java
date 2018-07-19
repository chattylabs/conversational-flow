package com.chattylabs.sdk.android.voice;

import android.support.annotation.NonNull;

public interface FlowTarget {
    void to(@NonNull VoiceNode node, VoiceNode... optNodes);
}
