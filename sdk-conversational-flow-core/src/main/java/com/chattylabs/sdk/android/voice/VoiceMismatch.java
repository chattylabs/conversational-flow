package com.chattylabs.sdk.android.voice;

import java.util.List;

public class VoiceMismatch implements VoiceAction {
    public int retries;
    public final String lowSoundErrorMessage;
    public final String listeningErrorMessage;
    public final String unexpectedErrorMessage;
    public final ComponentConsumer<List<String>> onNotMatched;

    private VoiceMismatch(Builder builder) {
        retries = builder.retries;
        lowSoundErrorMessage = builder.lowSoundErrorMessage;
        listeningErrorMessage = builder.listeningErrorMessage;
        unexpectedErrorMessage = builder.unexpectedErrorMessage;
        onNotMatched = builder.onNotMatched;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static final class Builder {
        private int retries;
        private String lowSoundErrorMessage;
        private String listeningErrorMessage;
        private String unexpectedErrorMessage;
        private ComponentConsumer<List<String>> onNotMatched;

        private Builder() {}

        public Builder setRetries(int retries) {
            this.retries = retries;
            return this;
        }

        public Builder setLowSoundErrorMessage(String lowSoundErrorMessage) {
            this.lowSoundErrorMessage = lowSoundErrorMessage;
            return this;
        }

        public Builder setListeningErrorMessage(String listeningErrorMessage) {
            this.listeningErrorMessage = listeningErrorMessage;
            return this;
        }

        public Builder setUnexpectedErrorMessage(String unexpectedErrorMessage) {
            this.unexpectedErrorMessage = unexpectedErrorMessage;
            return this;
        }

        public Builder setOnNotMatched(ComponentConsumer<List<String>> onNotMatched) {
            this.onNotMatched = onNotMatched;
            return this;
        }

        public VoiceMismatch build() {
            return new VoiceMismatch(this);
        }
    }
}
