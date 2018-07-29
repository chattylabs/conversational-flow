package com.chattylabs.sdk.android.voice;

public interface PlaybackListener {
    void onProgress(int progress);
    void onCompletion();
}
