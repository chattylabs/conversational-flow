package com.chattylabs.sdk.android.voice;

import com.chattylabs.sdk.android.voice.ConversationalFlowComponent.Consumer;

public class VoiceCapture implements VoiceAction {
    public final String id;
    public final Consumer<String> onCaptured;

    private VoiceCapture(Builder builder) {
        id = builder.id;
        onCaptured = builder.onCaptured;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static final class Builder {
        private String id;
        private Consumer<String> onCaptured;

        private Builder() {}

        public Builder setId(String id) {
            this.id = id;
            return this;
        }

        public Builder setOnCaptured(Consumer<String> onCaptured) {
            this.onCaptured = onCaptured;
            return this;
        }

        public VoiceCapture build() {
            return new VoiceCapture(this);
        }
    }
}
