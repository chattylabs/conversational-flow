package com.chattylabs.sdk.android.voice;

import android.support.annotation.NonNull;

public interface EdgeSource {
    EdgeTarget from(@NonNull VoiceNode node);
}
