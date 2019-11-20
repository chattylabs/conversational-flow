package chattylabs.conversations;

import androidx.annotation.NonNull;

import java.util.List;

public class VoiceMismatch implements VoiceAction {
    public final String id;
    public int retries;
    public final String lowSoundErrorMessage;
    public final String listeningErrorMessage;
    public final String unexpectedErrorMessage;
    public final ComponentConsumer<VoiceMismatch, List<String>> onNotMatched;

    private VoiceMismatch(Builder builder) {
        id = builder.id;
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
        private String id;
        private int retries;
        private String lowSoundErrorMessage;
        private String listeningErrorMessage;
        private String unexpectedErrorMessage;
        private ComponentConsumer<VoiceMismatch, List<String>> onNotMatched;

        private Builder() {}

        public Builder setId(String id) {
            this.id = id;
            return this;
        }

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

        public Builder setOnNotMatched(ComponentConsumer<VoiceMismatch, List<String>> onNotMatched) {
            this.onNotMatched = onNotMatched;
            return this;
        }

        public VoiceMismatch build() {
            return new VoiceMismatch(this);
        }
    }

    @NonNull @Override
    public String getId() {
        return id;
    }
}
