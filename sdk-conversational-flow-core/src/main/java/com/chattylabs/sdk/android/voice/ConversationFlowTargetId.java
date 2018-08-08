package com.chattylabs.sdk.android.voice;

import android.support.annotation.NonNull;

public interface ConversationFlowTargetId {
    void to(@NonNull String id, String... ids);
}
