package chattylabs.conversations;

import androidx.annotation.NonNull;

public interface Source {
    Target from(@NonNull VoiceNode node);
}
