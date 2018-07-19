package com.chattylabs.sdk.android.voice;

import android.support.annotation.NonNull;

public interface FlowSource {
    FlowTarget from(@NonNull VoiceNode node);
}
