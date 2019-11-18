package chattylabs.conversations;

import androidx.annotation.Nullable;

import java.util.List;
import java.util.Set;

/**
 * Instead of inheriting from this Interface you should extend {@link BaseSpeechSynthesizer}
 */
public interface SpeechSynthesizer {

    void checkStatus(SynthesizerListener.OnStatusChecked listener);

    void addFilter(TextFilter filter);

    List<TextFilter> getFilters();

    void clearFilters();

    void playText(String text, String queueId, SynthesizerListener... listeners);

    void playText(String text, SynthesizerListener... listeners);

    void playSilence(long durationInMillis, String queueId, SynthesizerListener... listeners);

    void playSilence(long durationInMillis, SynthesizerListener... listeners);

    void freeCurrentQueue();

    void holdCurrentQueue();

    void stop();

    void resume();

    void shutdown();

    boolean isEmpty();

    String getLastQueueId();

    @Nullable
    String getNextQueueId();

    String getCurrentQueueId();

    Set<String> getQueueSet();
}
