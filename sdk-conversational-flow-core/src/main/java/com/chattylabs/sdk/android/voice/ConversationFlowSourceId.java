package com.chattylabs.sdk.android.voice;

import android.support.annotation.NonNull;

public interface ConversationFlowSourceId {
    ConversationFlowTargetId from(@NonNull String id);
}
