package chattylabs.conversations;

public interface PlaybackListener {
    void onProgress(int progress);
    void onCompletion();
}
