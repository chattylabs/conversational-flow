package chattylabs.conversations;

import androidx.annotation.NonNull;

public interface Conversation {
    int FLAG_ENABLE_ERROR_MESSAGE_ON_LOW_SOUND = 1;

    void addFlag(@Flag int flag);

    void removeFlag(@Flag int flag);

    boolean hasFlag(@Flag int flag);

    void addNode(@NonNull VoiceNode node);

    Flow prepare();

    void next();

    void next(VoiceNode node);
}