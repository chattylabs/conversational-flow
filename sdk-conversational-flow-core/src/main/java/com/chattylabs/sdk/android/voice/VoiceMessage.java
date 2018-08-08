package com.chattylabs.sdk.android.voice;

public class VoiceMessage implements VoiceNode {
    public final String id;
    public final String text;
    public final Runnable onReady;
    public final Runnable onSuccess;
    public final Runnable onError;

    private VoiceMessage(Builder builder) {
        id = builder.id;
        text = builder.text;
        onReady = builder.onReady;
        onSuccess = builder.onSuccess;
        onError = builder.onError;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static final class Builder {
        private String id;
        private String text;
        private Runnable onReady;
        private Runnable onSuccess;
        private Runnable onError;

        private Builder() {}

        public Builder setId(String id) {
            this.id = id;
            return this;
        }

        public Builder setText(String text) {
            this.text = text;
            return this;
        }

        public Builder setOnReady(Runnable onReady) {
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
            return new VoiceMessage(this);
        }
    }

    @Override
    public String getId() {
        return id;
    }
}
