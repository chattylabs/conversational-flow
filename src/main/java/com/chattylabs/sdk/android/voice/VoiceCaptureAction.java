package com.chattylabs.sdk.android.voice;

public class VoiceCaptureAction implements IAction {
    public final String id;
    public final VoiceInteractionComponent.Consumer<String> onCaptured;

    private VoiceCaptureAction(Builder builder) {
        id = builder.id;
        onCaptured = builder.onCaptured;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static final class Builder {
        private String id;
        private VoiceInteractionComponent.Consumer<String> onCaptured;

        private Builder() {}

        public Builder setId(String id) {
            this.id = id;
            return this;
        }

        public Builder setOnCaptured(VoiceInteractionComponent.Consumer<String> onCaptured) {
            this.onCaptured = onCaptured;
            return this;
        }

        public VoiceCaptureAction build() {
            return new VoiceCaptureAction(this);
        }
    }
}
