package chattylabs.conversations;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import java.util.List;

public class VoiceMatch implements VoiceAction, HasId {
    public final String id;
    final OnReadyCallback onReady;
    final boolean canMatchOnPartials;
    final String[] expectedResults;
    final ComponentConsumer<VoiceMatch, List<String>> onMatched;
    final ComponentConsumer2<VoiceMatch, List<String>, Integer> onNotMatched;

    public interface OnReadyCallback {
        void run(VoiceMatch node);
    }

    public static final class Builder {
        private String id;
        private @StringRes int resId;
        private OnReadyCallback onReady;
        private boolean canMatchOnPartials;
        private String[] expectedResults;
        private ComponentConsumer<VoiceMatch, List<String>> onMatched;
        private ComponentConsumer2<VoiceMatch, List<String>, Integer> onNotMatched;

        public Builder(@NonNull String id) {
            this.id = id;
        }

        public Builder(@StringRes int resId) {
            this.resId = resId;
        }

        public Builder setOnReady(OnReadyCallback onReady) {
            this.onReady = onReady;
            return this;
        }

        public Builder canMatchOnPartials(boolean canMatchOnPartials) {
            this.canMatchOnPartials = canMatchOnPartials;
            return this;
        }

        public Builder setExpected(String[] expectedResults) {
            this.expectedResults = expectedResults;
            return this;
        }

        public Builder setOnMatched(ComponentConsumer<VoiceMatch, List<String>> onMatched) {
            this.onMatched = onMatched;
            return this;
        }

        public Builder setOnNotMatched(ComponentConsumer2<VoiceMatch, List<String>, Integer> onNotMatched) {
            this.onNotMatched = onNotMatched;
            return this;
        }

        public VoiceMatch build() {
            if (resId != 0 && id == null) {
                throw new IllegalArgumentException("ResourceId provided, use build(Context)");
            }
            if (id.trim().length() == 0) {
                throw new NullPointerException("Property \"id\" cannot be empty");
            }
            if (expectedResults == null || expectedResults.length == 0) {
                throw new NullPointerException("Property \"expectedResults\" is required");
            }
            if (onMatched == null && onNotMatched == null) {
                throw new NullPointerException("One property \"onMatched\" or \"onNotMatched\" is required");
            }
            return new VoiceMatch(this);
        }

        public VoiceMatch build(Context context) {
            if (resId == 0) {
                throw new IllegalArgumentException("Property \"resId\" is required");
            }
            this.id = context.getString(this.resId);
            return build();
        }
    }

    private VoiceMatch(Builder builder) {
        id = builder.id;
        onReady = builder.onReady;
        canMatchOnPartials = builder.canMatchOnPartials;
        expectedResults = builder.expectedResults;
        onMatched = builder.onMatched;
        onNotMatched = builder.onNotMatched;
    }

    public static Builder newBuilder(@NonNull String id) {
        return new Builder(id);
    }

    public static Builder newBuilder(@StringRes int resId) {
        return new Builder(resId);
    }

    @NonNull @Override
    public String getId() {
        return id;
    }
}
