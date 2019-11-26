package chattylabs.conversations;

import androidx.annotation.Nullable;

public interface HasNotMatched<T> {
    @Nullable T getOnNotMatched();
}
