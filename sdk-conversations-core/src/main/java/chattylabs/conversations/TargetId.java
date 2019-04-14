package chattylabs.conversations;

import androidx.annotation.NonNull;

public interface TargetId {
    void to(@NonNull String id, String... ids);
}
