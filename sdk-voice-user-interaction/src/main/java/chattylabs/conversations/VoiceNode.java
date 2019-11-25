package chattylabs.conversations;

public interface VoiceNode {
    interface Provider<T> {
        T get();
    }
}
