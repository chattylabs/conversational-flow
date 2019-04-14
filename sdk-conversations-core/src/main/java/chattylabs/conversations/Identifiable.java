package chattylabs.conversations;

import androidx.annotation.NonNull;

public interface Identifiable {

    /**
     * It can return an empty string, but it should never return null.
     */
    @NonNull
    String getId();
}
