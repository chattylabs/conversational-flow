package chattylabs.conversations;

import android.app.Activity;

import androidx.annotation.Nullable;

import java.util.List;
import java.util.Set;

/**
 * Instead of inheriting from this Interface you should extend {@link BaseSpeechSynthesizer}
 */
public interface SpeechSynthesizer {

    void checkStatus(SynthesizerListener.OnStatusChecked listener);

    void loadInstallation(Activity activity, SynthesizerListener.OnStatusChecked listener);

    void playText(String text, String queueId, SynthesizerListener... listeners);

    void playTextNow(String text, SynthesizerListener... listeners);

    void playSilence(long durationInMillis, String queueId, SynthesizerListener... listeners);

    void playSilenceNow(long durationInMillis, SynthesizerListener... listeners);

    void addFilter(Filter filter);

    void clearFilters();

    List<Filter> getFilters();

    String getLastQueueId();

    @Nullable
    String getNextQueueId();

    String getCurrentQueueId();

    Set<String> getQueueSet();

    boolean isQueueEmpty();

    boolean isOnQueue();

    void resume();

    boolean isSpeaking();

    void lock();

    void unlock();

    /**
     * Clear and release resources without affecting current listeners and queue of messages
     */
    void prune();

    boolean isLocked();

    void stop();

    void shutdown();
}
