package chattylabs.conversations;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

public class VoiceCapture implements VoiceAction, HasId {
    public final String id;
    final ComponentConsumer<VoiceCapture, String> onCaptured;
    final ComponentConsumer<VoiceCapture, Integer> onNoCapture;

    public static final class Builder {
        private String id;
        private @StringRes int resId;
        private ComponentConsumer<VoiceCapture, String> onCaptured;
        private ComponentConsumer<VoiceCapture, Integer> onNoCapture;

        public Builder(@NonNull String id) {
            this.id = id;
        }

        public Builder(@StringRes int resId) {
            this.resId = resId;
        }

        public Builder setOnCaptured(ComponentConsumer<VoiceCapture, String> onCaptured) {
            this.onCaptured = onCaptured;
            return this;
        }

        public Builder setOnNoCapture(ComponentConsumer<VoiceCapture, Integer> onNoCapture) {
            this.onNoCapture = onNoCapture;
            return this;
        }

        public VoiceCapture build() {
            if (resId != 0 && id == null) {
                throw new IllegalArgumentException("ResourceId provided, use build(Context)");
            }
            if (id.trim().length() == 0) {
                throw new NullPointerException("Property \"id\" cannot be empty");
            }
            if (onCaptured == null && onNoCapture == null) {
                throw new NullPointerException("One property \"onCaptured\" or \"onNoCapture\" is required");
            }
            return new VoiceCapture(this);
        }

        public VoiceCapture build(Context context) {
            if (resId == 0) {
                throw new IllegalArgumentException("Property \"resId\" is required");
            }
            this.id = context.getString(this.resId);
            return build();
        }
    }

    private VoiceCapture(Builder builder) {
        id = builder.id;
        onCaptured = builder.onCaptured;
        onNoCapture = builder.onNoCapture;
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
