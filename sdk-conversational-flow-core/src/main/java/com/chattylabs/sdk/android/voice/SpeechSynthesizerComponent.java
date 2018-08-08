package com.chattylabs.sdk.android.voice;

import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;

import java.util.List;
import java.util.Set;

@SuppressWarnings("unchecked")
public interface SpeechSynthesizerComponent {
    @WorkerThread
    void setup(SynthesizerListener.OnSetup onSynthesizerSetup);

    void addFilter(TextFilter filter);

    List<TextFilter> getFilters();

    void clearFilters();

    @WorkerThread
    <T extends SynthesizerListener> void playText(String text, String queueId, T... listeners);

    @WorkerThread
    <T extends SynthesizerListener> void playText(String text, T... listeners);

    @WorkerThread
    <T extends SynthesizerListener> void playSilence(long durationInMillis, String queueId, T... listeners);

    @WorkerThread
    <T extends SynthesizerListener> void playSilence(long durationInMillis, T... listeners);

    void releaseCurrentQueue();

    void holdCurrentQueue();

    void stop();

    void resume();

    void shutdown();

    void release();

    boolean isEmpty();

    String getLastQueueId();

    @Nullable
    String getNextQueueId();

    String getCurrentQueueId();

    Set<String> getQueueSet();
}
