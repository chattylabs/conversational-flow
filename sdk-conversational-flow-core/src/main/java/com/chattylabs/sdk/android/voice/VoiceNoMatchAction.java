package com.chattylabs.sdk.android.voice;

import java.util.List;

public class VoiceNoMatchAction implements VoiceActionContract {
    public int retry;
    public final String lowSoundErrorMessage;
    public final String listeningErrorMessage;
    public final String unexpectedErrorMessage;
    public final ConversationalFlowComponent.Consumer<List<String>> onNotMatched;

    private VoiceNoMatchAction(Builder builder) {
        retry = builder.retry;
        lowSoundErrorMessage = builder.lowSoundErrorMessage;
        listeningErrorMessage = builder.listeningErrorMessage;
        unexpectedErrorMessage = builder.unexpectedErrorMessage;
        onNotMatched = builder.onNotMatched;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static final class Builder {
        private int retry;
        private String lowSoundErrorMessage;
        private String listeningErrorMessage;
        private String unexpectedErrorMessage;
        private ConversationalFlowComponent.Consumer<List<String>> onNotMatched;

        private Builder() {}

        public Builder setRetry(int retry) {
            this.retry = retry;
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

        public Builder setOnNotMatched(ConversationalFlowComponent.Consumer<List<String>> onNotMatched) {
            this.onNotMatched = onNotMatched;
            return this;
        }

        public VoiceNoMatchAction build() {
            return new VoiceNoMatchAction(this);
        }
    }
}
