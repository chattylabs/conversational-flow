package com.chattylabs.sdk.android.voice;

import android.support.annotation.IntDef;
import android.support.annotation.RawRes;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class ComponentConfig {
    public static final int SYNTHESIZER_SERVICE_ANDROID_BUILTIN = 0x6e44ad;
    public static final int RECOGNIZER_SERVICE_ANDROID_BUILTIN = 0x5a4ed1;
    public static final int SYNTHESIZER_SERVICE_GOOGLE_BUILTIN = 0xaa88d6;
    public static final int RECOGNIZER_SERVICE_GOOGLE_SPEECH = 0xcd231a;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(flag=true,
            value = {
                    SYNTHESIZER_SERVICE_ANDROID_BUILTIN,
                    RECOGNIZER_SERVICE_ANDROID_BUILTIN,
                    SYNTHESIZER_SERVICE_GOOGLE_BUILTIN,
                    RECOGNIZER_SERVICE_GOOGLE_SPEECH
            })
    public @interface ServiceType {}

    private LazyProvider<Boolean> bluetoothScoRequired;
    private LazyProvider<Boolean> audioExclusiveRequiredForSynthesizer;
    private LazyProvider<Boolean> audioExclusiveRequiredForRecognizer;
    private ServiceTypeLazyProvider recognizerServiceType;
    private ServiceTypeLazyProvider synthesizerServiceType;
    private RawResourceLazyProvider googleCredentialsResourceFile;

    private ComponentConfig(Builder builder) {
        bluetoothScoRequired = builder.bluetoothScoRequired;
        audioExclusiveRequiredForSynthesizer = builder.audioExclusiveRequiredForSynthesizer;
        audioExclusiveRequiredForRecognizer = builder.audioExclusiveRequiredForRecognizer;
        recognizerServiceType = builder.recognizerServiceType;
        synthesizerServiceType = builder.synthesizerServiceType;
        googleCredentialsResourceFile = builder.googleCredentialsResourceFile;
    }

    public boolean isBluetoothScoRequired() {
        return bluetoothScoRequired.get();
    }

    public boolean isAudioExclusiveRequiredForSynthesizer() {
        return audioExclusiveRequiredForSynthesizer.get();
    }

    public boolean isAudioExclusiveRequiredForRecognizer() {
        return audioExclusiveRequiredForRecognizer.get();
    }

    @ServiceType
    public int getRecognizerServiceType() {
        return recognizerServiceType.get();
    }

    @ServiceType
    public int getSynthesizerServiceType() {
        return synthesizerServiceType.get();
    }

    @RawRes
    public int getGoogleCredentialsResourceFile() {
        return googleCredentialsResourceFile.get();
    }

    public static final class Builder {
        private LazyProvider<Boolean> bluetoothScoRequired;
        private LazyProvider<Boolean> audioExclusiveRequiredForSynthesizer;
        private LazyProvider<Boolean> audioExclusiveRequiredForRecognizer;
        private ServiceTypeLazyProvider recognizerServiceType;
        private ServiceTypeLazyProvider synthesizerServiceType;
        private RawResourceLazyProvider googleCredentialsResourceFile;

        public Builder() {
        }

        public Builder(ComponentConfig copy) {
            bluetoothScoRequired = copy.bluetoothScoRequired;
            audioExclusiveRequiredForSynthesizer = copy.audioExclusiveRequiredForSynthesizer;
            audioExclusiveRequiredForRecognizer = copy.audioExclusiveRequiredForRecognizer;
            recognizerServiceType = copy.recognizerServiceType;
            synthesizerServiceType = copy.synthesizerServiceType;
            googleCredentialsResourceFile = copy.googleCredentialsResourceFile;
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

        public Builder setSynthesizerServiceType(ServiceTypeLazyProvider lazyRecognizerServiceType) {
            this.synthesizerServiceType = lazyRecognizerServiceType;
            return this;
        }

        public Builder setGoogleCredentialsResourceFile(RawResourceLazyProvider lazyProvider) {
            this.googleCredentialsResourceFile = lazyProvider;
            return this;
        }

        public ComponentConfig build() {
            return new ComponentConfig(this);
        }
    }

    public interface Update {
        ComponentConfig run(ComponentConfig.Builder builder);
    }

    public interface LazyProvider<T> {
        T get();
    }

    public interface ServiceTypeLazyProvider {
        @ServiceType int get();
    }

    public interface RawResourceLazyProvider {
        @RawRes int get();
    }
}
