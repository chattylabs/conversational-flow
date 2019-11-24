package chattylabs.conversations;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

public class VoiceMessage implements VoiceNode, HasId {
    public final String id;
    public final @StringRes int resId;
    public String text;
    public final OnReadyCallback onReady;
    public final Runnable onSuccess;
    public final Runnable onError;

    public interface OnReadyCallback {
        void run(VoiceMessage node);
    }

    public static final class Builder {
        private String id;
        private @StringRes int resId;
        private String text;
        private OnReadyCallback onReady;
        private Runnable onSuccess;
        private Runnable onError;

        public Builder(@NonNull String id) {
            this.id = id;
        }

        public Builder(@NonNull @StringRes int resId) {
            this.resId = resId;
        }

        public Builder setText(String text) {
            this.text = text;
            return this;
        }

        public Builder setOnReady(OnReadyCallback onReady) {
            this.onReady = onReady;
            return this;
        }

        public Builder setOnSuccess(Runnable onSuccess) {
            this.onSuccess = onSuccess;
            return this;
        }

        public Builder setOnError(Runnable onError) {
            this.onError = onError;
            return this;
        }

        public VoiceMessage build() {
            if (resId != 0 && id == null) {
                throw new IllegalArgumentException("ResourceId provided, use build(Context)");
            }
            if (id.trim().length() == 0) {
                throw new NullPointerException("Property \"id\" cannot be empty");
            }
            if (text == null || text.length() == 0) {
                throw new NullPointerException("Property \"text\" is required");
            }
            return new VoiceMessage(this);
        }

        public VoiceMessage build(Context context) {
            if (resId == 0) {
                throw new IllegalArgumentException("Property \"resId\" is required");
            }
            this.id = context.getString(this.resId);
            return build();
        }
    }

    private VoiceMessage(Builder builder) {
        id = builder.id;
        resId = builder.resId;
        text = builder.text;
        onReady = builder.onReady;
        onSuccess = builder.onSuccess;
        onError = builder.onError;
    }

    public static Builder newBuilder(@NonNull String id) {
        return new Builder(id);
    }

    public static Builder newBuilder(@NonNull @StringRes int resId) {
        return new Builder(resId);
    }

    @NonNull @Override
    public String getId() {
        return id;
    }
}
