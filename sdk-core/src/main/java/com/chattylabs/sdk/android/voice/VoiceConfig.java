package com.chattylabs.sdk.android.voice;

import android.support.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class VoiceConfig {
    public static final int RECOGNIZER_SERVICE_ANDROID_BUILTIN = 0x5a4ed1;
    public static final int RECOGNIZER_SERVICE_GOOGLE_SPEECH = 0xcd231a;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(flag=true,
            value = {
                    RECOGNIZER_SERVICE_ANDROID_BUILTIN,
                    RECOGNIZER_SERVICE_GOOGLE_SPEECH
            })
    public @interface ServiceType {}

    private BooleanLazyReturn bluetoothScoRequired;
    private BooleanLazyReturn audioExclusiveRequiredForSynthesizer;
    private BooleanLazyReturn audioExclusiveRequiredForRecognizer;
    private ServiceTypeLazyReturn recognizerServiceType;

    private VoiceConfig(Builder builder) {
        bluetoothScoRequired = builder.bluetoothScoRequired;
        audioExclusiveRequiredForSynthesizer = builder.audioExclusiveRequiredForSynthesizer;
        audioExclusiveRequiredForRecognizer = builder.audioExclusiveRequiredForRecognizer;
        recognizerServiceType = builder.recognizerServiceType;
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

    @ServiceType
    public int getRecognizerServiceType() {
        return recognizerServiceType.get();
    }

    public static final class Builder {
        private BooleanLazyReturn bluetoothScoRequired;
        private BooleanLazyReturn audioExclusiveRequiredForSynthesizer;
        private BooleanLazyReturn audioExclusiveRequiredForRecognizer;
        private ServiceTypeLazyReturn recognizerServiceType;

        public Builder() {
        }

        public Builder(VoiceConfig copy) {
            this.bluetoothScoRequired = copy.bluetoothScoRequired;
            this.audioExclusiveRequiredForSynthesizer = copy.audioExclusiveRequiredForSynthesizer;
            this.audioExclusiveRequiredForRecognizer = copy.audioExclusiveRequiredForRecognizer;
            this.recognizerServiceType = copy.recognizerServiceType;
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

        public Builder setRecognizerServiceType(ServiceTypeLazyReturn recognizerServiceType) {
            this.recognizerServiceType = recognizerServiceType;
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

    public interface IntegerLazyReturn {
        int get();
    }

    public interface ServiceTypeLazyReturn {
        @ServiceType int get();
    }
}
