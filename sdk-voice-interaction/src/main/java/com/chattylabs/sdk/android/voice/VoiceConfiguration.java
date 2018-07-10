package com.chattylabs.sdk.android.voice;

public class VoiceConfiguration {
    private BooleanLazyReturn bluetoothScoRequired;
    private BooleanLazyReturn synthesizerAudioExclusive;
    private BooleanLazyReturn recognizerAudioExclusive;

    private VoiceConfiguration(Builder builder) {
        bluetoothScoRequired = builder.bluetoothScoRequired;
        synthesizerAudioExclusive = builder.synthesizerAudioExclusive;
        recognizerAudioExclusive = builder.recognizerAudioExclusive;
    }

    public boolean isBluetoothScoRequired() {
        return bluetoothScoRequired.isTrue();
    }

    public boolean isSynthesizerAudioExclusive() {
        return synthesizerAudioExclusive.isTrue();
    }

    public boolean isRecognizerAudioExclusive() {
        return recognizerAudioExclusive.isTrue();
    }

    public static final class Builder {
        private BooleanLazyReturn bluetoothScoRequired;
        private BooleanLazyReturn synthesizerAudioExclusive;
        private BooleanLazyReturn recognizerAudioExclusive;

        public Builder() {
        }

        public Builder(VoiceConfiguration copy) {
            this.bluetoothScoRequired = copy.bluetoothScoRequired;
        }

        public Builder setBluetoothScoRequired(BooleanLazyReturn lazyReturn) {
            this.bluetoothScoRequired = lazyReturn;
            return this;
        }

        public Builder setSynthesizerAudioExclusive(BooleanLazyReturn lazyReturn) {
            this.synthesizerAudioExclusive = lazyReturn;
            return this;
        }

        public Builder setRecognizerAudioExclusive(BooleanLazyReturn lazyReturn) {
            this.recognizerAudioExclusive = lazyReturn;
            return this;
        }

        public VoiceConfiguration build() {
            return new VoiceConfiguration(this);
        }
    }

    public interface Update {
        VoiceConfiguration run(VoiceConfiguration.Builder builder);
    }

    public interface BooleanLazyReturn {
        boolean isTrue();
    }
}
