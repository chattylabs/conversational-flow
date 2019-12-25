package chattylabs.conversations;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

public interface Conversation {
    int FLAG_ENABLE_ERROR_MESSAGE_ON_LOW_SOUND = 1;
    int MINIMUM_WORDS_TO_DISCARD_RECOGNITION = 3;

    void addFlag(@Flag int flag);

    void removeFlag(@Flag int flag);

    boolean hasFlag(@Flag int flag);

    void addNode(@NonNull VoiceNode node);

    VoiceNode getNode(@NonNull String id);

    VoiceNode getNode(@StringRes int id);

    Flow prepare(@NonNull Runnable onCompleteListener);

    Flow prepare();

    void next();

    void next(VoiceNode node);
}