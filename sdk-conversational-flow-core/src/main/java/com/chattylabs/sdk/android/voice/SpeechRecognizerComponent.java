package com.chattylabs.sdk.android.voice;

/**
 * Instead of inheriting from this Interface you should extend {@link BaseSpeechRecognizer}
 */
public interface SpeechRecognizerComponent {

    @SuppressWarnings("unchecked")
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
