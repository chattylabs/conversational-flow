package com.chattylabs.sdk.android.voice;

public interface ComponentStatus {
    boolean isAvailable();
    int getSynthesizerStatus();
    int getRecognizerStatus();
}
