package chattylabs.conversations;

/**
 * Instead of inheriting from this Interface you should extend {@link BaseSpeechRecognizer}
 */
public interface SpeechRecognizer {

    void checkStatus(RecognizerListener.OnStatusChecked listener);

    void listen(RecognizerListener... listeners);

    void stopListening();

    void stop();

    void setRmsDebug(boolean debug);

    void setNoSoundThreshold(float maxValue);

    void setLowSoundThreshold(float maxValue);

    boolean isListening();

    interface Creator<T> {
        T create();
    }
}
