package com.chattylabs.sdk.android.voice;

public class VoiceCapture implements VoiceAction {
    public final String id;
    public final ComponentConsumer<String> onCaptured;

    private VoiceCapture(Builder builder) {
        id = builder.id;
        onCaptured = builder.onCaptured;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static final class Builder {
        private String id;
        private ComponentConsumer<String> onCaptured;

        private Builder() {}

        public Builder setId(String id) {
            this.id = id;
            return this;
        }

        public Builder setOnCaptured(ComponentConsumer<String> onCaptured) {
            this.onCaptured = onCaptured;
            return this;
        }

        public VoiceCapture build() {
            return new VoiceCapture(this);
        }
    }

    @Override
    public String getId() {
        return id;
    }
}
