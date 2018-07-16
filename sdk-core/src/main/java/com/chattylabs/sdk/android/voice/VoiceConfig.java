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

    private LazyProvider<Boolean> bluetoothScoRequired;
    private LazyProvider<Boolean> audioExclusiveRequiredForSynthesizer;
    private LazyProvider<Boolean> audioExclusiveRequiredForRecognizer;
    private ServiceTypeLazyProvider recognizerServiceType;
    private LazyProvider<HelperAccessToken> googleAccessToken;

    private VoiceConfig(Builder builder) {
        bluetoothScoRequired = builder.bluetoothScoRequired;
        audioExclusiveRequiredForSynthesizer = builder.audioExclusiveRequiredForSynthesizer;
        audioExclusiveRequiredForRecognizer = builder.audioExclusiveRequiredForRecognizer;
        recognizerServiceType = builder.recognizerServiceType;
        googleAccessToken = builder.googleAccessToken;
    }

    public boolean isBluetoothScoRequired() {
        return bluetoothScoRequired.provide();
    }

    public boolean isAudioExclusiveRequiredForSynthesizer() {
        return audioExclusiveRequiredForSynthesizer.provide();
    }

    public boolean isAudioExclusiveRequiredForRecognizer() {
        return audioExclusiveRequiredForRecognizer.provide();
    }

    @ServiceType
    public int getRecognizerServiceType() {
        return recognizerServiceType.get();
    }

    public HelperAccessToken getGoogleAccessToken() {
        return googleAccessToken.provide();
    }

    public static final class Builder {
        private LazyProvider<Boolean> bluetoothScoRequired;
        private LazyProvider<Boolean> audioExclusiveRequiredForSynthesizer;
        private LazyProvider<Boolean> audioExclusiveRequiredForRecognizer;
        private ServiceTypeLazyProvider recognizerServiceType;
        private LazyProvider<HelperAccessToken> googleAccessToken;

        public Builder() {
        }

        public Builder(VoiceConfig copy) {
            this.bluetoothScoRequired = copy.bluetoothScoRequired;
            this.audioExclusiveRequiredForSynthesizer = copy.audioExclusiveRequiredForSynthesizer;
            this.audioExclusiveRequiredForRecognizer = copy.audioExclusiveRequiredForRecognizer;
            this.recognizerServiceType = copy.recognizerServiceType;
            this.googleAccessToken = copy.googleAccessToken;
        }

        public Builder setBluetoothScoRequired(LazyProvider<Boolean> lazyProvider) {
            this.bluetoothScoRequired = lazyProvider;
            return this;
        }

        public Builder setAudioExclusiveRequiredForSynthesizer(LazyProvider<Boolean> lazyProvider) {
            this.audioExclusiveRequiredForSynthesizer = lazyProvider;
            return this;
        }

        public Builder setAudioExclusiveRequiredForRecognizer(LazyProvider<Boolean> lazyProvider) {
            this.audioExclusiveRequiredForRecognizer = lazyProvider;
            return this;
        }

        public Builder setRecognizerServiceType(ServiceTypeLazyProvider lazyRecognizerServiceType) {
            this.recognizerServiceType = lazyRecognizerServiceType;
            return this;
        }

        public Builder setGoogleAccessToken(LazyProvider<HelperAccessToken> lazyProvider) {
            this.googleAccessToken = lazyProvider;
            return this;
        }

        public VoiceConfig build() {
            return new VoiceConfig(this);
        }
    }

    public interface Update {
        VoiceConfig run(VoiceConfig.Builder builder);
    }

    public interface LazyProvider<T> {
        T provide();
    }

    public interface ServiceTypeLazyProvider {
        @ServiceType int get();
    }
}
