package com.chattylabs.sdk.android.voice;

@SuppressWarnings("unchecked")
public interface SpeechRecognizerComponent {
    <T extends RecognizerListener> void listen(T... listeners);

    void stop();

    void cancel();

    void shutdown();

    void release();

    void setRmsDebug(boolean debug);

    void setNoSoundThreshold(float maxValue);

    void setLowSoundThreshold(float maxValue);

    interface Creator<T> {
        T create();
    }
}
