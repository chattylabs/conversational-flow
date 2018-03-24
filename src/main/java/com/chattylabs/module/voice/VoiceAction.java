package com.chattylabs.module.voice;

import java.util.List;

public class VoiceAction implements IAction {
    public final String id;
    public final boolean canMatchOnPartials;
    public final String[] expectedResults;
    public final VoiceInteractionComponent.Consumer<List<String>> onMatched;

    private VoiceAction(Builder builder) {
        id = builder.id;
        canMatchOnPartials = builder.canMatchOnPartials;
        expectedResults = builder.expectedResults;
        onMatched = builder.onMatched;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static final class Builder {
        private String id;
        private boolean canMatchOnPartials;
        private String[] expectedResults;
        private VoiceInteractionComponent.Consumer<List<String>> onMatched;

        private Builder() {}

        public Builder setId(String id) {
            this.id = id;
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

        public Builder setOnMatched(VoiceInteractionComponent.Consumer<List<String>> onMatched) {
            this.onMatched = onMatched;
            return this;
        }

        public VoiceAction build() {
            return new VoiceAction(this);
        }
    }
}
