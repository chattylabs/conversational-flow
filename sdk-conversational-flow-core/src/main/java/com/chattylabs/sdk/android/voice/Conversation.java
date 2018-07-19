package com.chattylabs.sdk.android.voice;

import android.support.annotation.IntDef;
import android.support.annotation.NonNull;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public interface Conversation {

    int FLAG_ENABLE_ERROR_MESSAGE_ON_LOW_SOUND = 1;

    @IntDef({FLAG_ENABLE_ERROR_MESSAGE_ON_LOW_SOUND})
    @Retention(RetentionPolicy.SOURCE)
    @interface Flag {}

    void addFlag(@Flag int flag);

    void removeFlag(@Flag int flag);

    boolean hasFlag(@Flag int flag);

    void addNode(@NonNull VoiceNode node);

    Flow prepare();

    void start(VoiceNode root);

    void next();
}