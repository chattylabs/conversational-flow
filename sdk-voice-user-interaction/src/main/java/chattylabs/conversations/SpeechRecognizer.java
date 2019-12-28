package chattylabs.conversations;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Instead of inheriting from this Interface you should extend {@link BaseSpeechRecognizer}
 */
public interface SpeechRecognizer {

    void checkStatus(RecognizerListener.OnStatusChecked listener);

    /**
     * It returns the string result that satisfies the higher confidence
     */
    String selectMostConfidentResult(@NonNull ArrayList<String> results, float[] confidences);

    /**
     * Helper that checks if a string on a list matches with another list of strings.
     * <br/>This is normally used internally to see if the text said by a user matches with
     * some expected strings.
     */
    boolean anyMatch(@NonNull List<String> data, @NonNull List<String> expected);

    /**
     * Check if a specific string pattern interpreted as a whole word matches with a string.
     */
    boolean matches(@NonNull String str, @NonNull String patternStr);

    void listen(RecognizerListener... listeners);

    void stopListening();

    void stop();

    void setRmsDebug(boolean debug);

    void setNoSoundThreshold(float maxValue);

    void setLowSoundThreshold(float maxValue);

    boolean isAvailable();

    boolean isListening();

    interface Creator<T> {
        T create();
    }
}
