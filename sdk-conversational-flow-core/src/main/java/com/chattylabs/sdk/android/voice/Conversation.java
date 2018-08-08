package com.chattylabs.sdk.android.voice;

import android.support.annotation.NonNull;

public interface Conversation {
    int FLAG_ENABLE_ERROR_MESSAGE_ON_LOW_SOUND = 1;

    void addFlag(@ConversationFlag int flag);

    void removeFlag(@ConversationFlag int flag);

    boolean hasFlag(@ConversationFlag int flag);

    void addNode(@NonNull VoiceNode node);

    ConversationFlow prepare();

    void start(VoiceNode root);

    void next();
}