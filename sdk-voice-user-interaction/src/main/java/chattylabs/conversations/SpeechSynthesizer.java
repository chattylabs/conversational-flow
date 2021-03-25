package chattylabs.conversations;

import android.app.Activity;

import androidx.annotation.Nullable;
import androidx.core.util.Consumer;

import java.util.List;
import java.util.Set;

/**
 * Instead of inheriting from this Interface you should extend {@link BaseSpeechSynthesizer}
 */
public interface SpeechSynthesizer {

    int CHECK_TTS_REQUEST_CODE = 775;

    String EMPTY = "<EMPTY>";

    String VOICE_MALE = "#male";
    String VOICE_FEMALE = "#female";

    void setVoice(String gender);

    void setDefaultVoice();

    void checkStatus(Activity activity, SynthesizerListener.OnStatusChecked listener);

    void testStatus(SynthesizerListener.OnStatusChecked listener);

    void loadInstallation(Activity activity, SynthesizerListener.OnStatusChecked listener);

    void playAudioFile(String audioPath, String queueId, SynthesizerListener... listeners);

    void playText(String text, String queueId, SynthesizerListener... listeners);

    void playAudioFileNow(String text, SynthesizerListener... listeners);

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

    void getSpeechDuration(String text, Consumer<Integer> callback);

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
