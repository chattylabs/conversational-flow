package com.chattylabs.sdk.android.voice;

import com.chattylabs.sdk.android.voice.ConversationalFlowComponent.Consumer;

import java.util.List;

public class VoiceMatch implements VoiceAction {
    public final String id;
    public final Runnable onReady;
    public final boolean canMatchOnPartials;
    public final String[] expectedResults;
    public final Consumer<List<String>> onMatched;

    private VoiceMatch(Builder builder) {
        id = builder.id;
        onReady = builder.onReady;
        canMatchOnPartials = builder.canMatchOnPartials;
        expectedResults = builder.expectedResults;
        onMatched = builder.onMatched;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static final class Builder {
        private String id;
        private Runnable onReady;
        private boolean canMatchOnPartials;
        private String[] expectedResults;
        private Consumer<List<String>> onMatched;

        private Builder() {}

        public Builder setId(String id) {
            this.id = id;
            return this;
        }

        public Builder setOnReady(Runnable onReady) {
            this.onReady = onReady;
            return this;
        }

        public Builder canMatchOnPartials(boolean canMatchOnPartials) {
            this.canMatchOnPartials = canMatchOnPartials;
            return this;
        }

        public Builder setExpectedResults(String[] expectedResults) {
            this.expectedResults = expectedResults;
            return this;
        }

        public Builder setOnMatched(Consumer<List<String>> onMatched) {
            this.onMatched = onMatched;
            return this;
        }

        public VoiceMatch build() {
            return new VoiceMatch(this);
        }
    }
}
