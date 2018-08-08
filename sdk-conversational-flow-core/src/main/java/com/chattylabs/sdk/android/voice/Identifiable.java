package com.chattylabs.sdk.android.voice;

import android.support.annotation.NonNull;

public interface Identifiable {

    /**
     * It can return an empty string, but it should never return null.
     */
    @NonNull
    String getId();
}
