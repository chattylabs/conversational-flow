package chattylabs.conversations;

import androidx.annotation.NonNull;

public interface Target {
    void to(@NonNull VoiceNode node, VoiceNode... optNodes);
}
