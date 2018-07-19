package com.chattylabs.sdk.android.voice;

public class VoiceCaptureAction implements VoiceActionContract {
    public final String id;
    public final ConversationalFlowComponent.Consumer<String> onCaptured;

    private VoiceCaptureAction(Builder builder) {
        id = builder.id;
        onCaptured = builder.onCaptured;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static final class Builder {
        private String id;
        private ConversationalFlowComponent.Consumer<String> onCaptured;

        private Builder() {}

        public Builder setId(String id) {
            this.id = id;
            return this;
        }

        public Builder setOnCaptured(ConversationalFlowComponent.Consumer<String> onCaptured) {
            this.onCaptured = onCaptured;
            return this;
        }

        public VoiceCaptureAction build() {
            return new VoiceCaptureAction(this);
        }
    }
}
