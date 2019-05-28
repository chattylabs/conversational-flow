package chattylabs.conversations;

import androidx.annotation.NonNull;

public class VoiceCapture implements VoiceAction {
    public final String id;
    public final ComponentConsumer<VoiceCapture, String> onCaptured;

    private VoiceCapture(Builder builder) {
        id = builder.id;
        onCaptured = builder.onCaptured;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static final class Builder {
        private String id;
        private ComponentConsumer<VoiceCapture, String> onCaptured;

        private Builder() {}

        public Builder setId(String id) {
            this.id = id;
            return this;
        }

        public Builder setOnCaptured(ComponentConsumer<VoiceCapture, String> onCaptured) {
            this.onCaptured = onCaptured;
            return this;
        }

        public VoiceCapture build() {
            return new VoiceCapture(this);
        }
    }

    @NonNull @Override
    public String getId() {
        return id;
    }
}
