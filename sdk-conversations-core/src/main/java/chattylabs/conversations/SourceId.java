package chattylabs.conversations;

import androidx.annotation.NonNull;

public interface SourceId {
    TargetId from(@NonNull String id);
}
