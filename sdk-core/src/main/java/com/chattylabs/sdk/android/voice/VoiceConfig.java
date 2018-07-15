package com.chattylabs.sdk.android.voice;

public class VoiceConfig {
    private BooleanLazyReturn bluetoothScoRequired;
    private BooleanLazyReturn audioExclusiveRequiredForSynthesizer;
    private BooleanLazyReturn audioExclusiveRequiredForRecognizer;

    private VoiceConfig(Builder builder) {
        bluetoothScoRequired = builder.bluetoothScoRequired;
        audioExclusiveRequiredForSynthesizer = builder.audioExclusiveRequiredForSynthesizer;
        audioExclusiveRequiredForRecognizer = builder.audioExclusiveRequiredForRecognizer;
    }

    public boolean isBluetoothScoRequired() {
        return bluetoothScoRequired.isTrue();
    }

    public boolean isAudioExclusiveRequiredForSynthesizer() {
        return audioExclusiveRequiredForSynthesizer.isTrue();
    }

    public boolean isAudioExclusiveRequiredForRecognizer() {
        return audioExclusiveRequiredForRecognizer.isTrue();
    }

    public static final class Builder {
        private BooleanLazyReturn bluetoothScoRequired;
        private BooleanLazyReturn audioExclusiveRequiredForSynthesizer;
        private BooleanLazyReturn audioExclusiveRequiredForRecognizer;

        public Builder() {
        }

        public Builder(VoiceConfig copy) {
            this.bluetoothScoRequired = copy.bluetoothScoRequired;
            this.audioExclusiveRequiredForSynthesizer = copy.audioExclusiveRequiredForSynthesizer;
            this.audioExclusiveRequiredForRecognizer = copy.audioExclusiveRequiredForRecognizer;
        }

        public Builder setBluetoothScoRequired(BooleanLazyReturn lazyReturn) {
            this.bluetoothScoRequired = lazyReturn;
            return this;
        }

        public Builder setAudioExclusiveRequiredForSynthesizer(BooleanLazyReturn lazyReturn) {
            this.audioExclusiveRequiredForSynthesizer = lazyReturn;
            return this;
        }

        public Builder setAudioExclusiveRequiredForRecognizer(BooleanLazyReturn lazyReturn) {
            this.audioExclusiveRequiredForRecognizer = lazyReturn;
            return this;
        }

        public VoiceConfig build() {
            return new VoiceConfig(this);
        }
    }

    public interface Update {
        VoiceConfig run(VoiceConfig.Builder builder);
    }

    public interface BooleanLazyReturn {
        boolean isTrue();
    }
}
