package chattylabs.conversations;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

@FunctionalInterface
public interface ComponentConsumer2<N extends VoiceNode, T, S> {

    /**
     * Performs this operation on the given argument.
     *
     * @param t the input argument
     */
    void accept(@NonNull N node, @Nullable T t, @Nullable S s);

    /**
     * Returns a composed {@code Consumer} that performs, in sequence, this
     * operation followed by the {@code after} operation. If performing either
     * operation throws an exception, it is relayed to the caller of the
     * composed operation.  If performing this operation throws an exception,
     * the {@code after} operation will not be performed.
     *
     * @param after the operation to perform after this operation
     * @return a composed {@code Consumer} that performs in sequence this
     * operation followed by the {@code after} operation
     * @throws NullPointerException if {@code after} is null
     */
    default ComponentConsumer2<N, T, S> andThen(ComponentConsumer2<N, ? super T, ? super S> after) {
        Objects.requireNonNull(after);
        return (@NonNull N node, @Nullable T t, @Nullable S s) -> {
            accept(node, t, s); after.accept(node, t, s); };
    }
}
