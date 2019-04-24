package chattylabs.conversations;

import androidx.annotation.NonNull;
import androidx.annotation.RawRes;

import java.util.Locale;

public class ComponentConfig {
    private LazyProvider<Locale> speechLanguage;
    private LazyProvider<Boolean> bluetoothScoRequired;
    private LazyProvider<Boolean> audioExclusiveRequiredForSynthesizer;
    private LazyProvider<Boolean> audioExclusiveRequiredForRecognizer;
    private LazyProvider<Class<? extends SpeechRecognizer>> recognizerServiceType;
    private LazyProvider<Class<? extends SpeechSynthesizer>> synthesizerServiceType;
    private RawResourceLazyProvider googleCredentialsResourceFile;
    private LazyProvider<Boolean> speechDictation;

    private ComponentConfig(Builder builder) {
        speechLanguage = builder.speechLanguage;
        bluetoothScoRequired = builder.bluetoothScoRequired;
        audioExclusiveRequiredForSynthesizer = builder.audioExclusiveRequiredForSynthesizer;
        audioExclusiveRequiredForRecognizer = builder.audioExclusiveRequiredForRecognizer;
        recognizerServiceType = builder.recognizerServiceType;
        synthesizerServiceType = builder.synthesizerServiceType;
        googleCredentialsResourceFile = builder.googleCredentialsResourceFile;
        speechDictation = builder.speechDictation;
    }

    public Locale getSpeechLanguage() {
        return speechLanguage.get();
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

    public Class<? extends SpeechRecognizer> getRecognizerServiceType() {
        return recognizerServiceType.get();
    }

    public Class<? extends SpeechSynthesizer> getSynthesizerServiceType() {
        return synthesizerServiceType.get();
    }

    @RawRes
    public int getGoogleCredentialsResourceFile() {
        return googleCredentialsResourceFile.get();
    }

    public boolean isSpeechDictation() {
        return speechDictation.get();
    }

    public static final class Builder {
        private LazyProvider<Locale> speechLanguage;
        private LazyProvider<Boolean> bluetoothScoRequired;
        private LazyProvider<Boolean> audioExclusiveRequiredForSynthesizer;
        private LazyProvider<Boolean> audioExclusiveRequiredForRecognizer;
        private LazyProvider<Class<? extends SpeechRecognizer>> recognizerServiceType;
        private LazyProvider<Class<? extends SpeechSynthesizer>> synthesizerServiceType;
        private RawResourceLazyProvider googleCredentialsResourceFile;
        private LazyProvider<Boolean> speechDictation;

        public Builder() {
        }

        public Builder(ComponentConfig copy) {
            speechLanguage = copy.speechLanguage;
            bluetoothScoRequired = copy.bluetoothScoRequired;
            audioExclusiveRequiredForSynthesizer = copy.audioExclusiveRequiredForSynthesizer;
            audioExclusiveRequiredForRecognizer = copy.audioExclusiveRequiredForRecognizer;
            recognizerServiceType = copy.recognizerServiceType;
            synthesizerServiceType = copy.synthesizerServiceType;
            googleCredentialsResourceFile = copy.googleCredentialsResourceFile;
            speechDictation = copy.speechDictation;
        }

        public Builder setSpeechLanguage(LazyProvider<Locale> speechLanguage) {
            this.speechLanguage = speechLanguage;
            return this;
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

        public Builder setRecognizerServiceType(
                LazyProvider<Class<? extends SpeechRecognizer>> lazyRecognizerServiceType) {
            this.recognizerServiceType = lazyRecognizerServiceType;
            return this;
        }

        public Builder setSynthesizerServiceType(
                LazyProvider<Class<? extends SpeechSynthesizer>> lazyRecognizerServiceType) {
            this.synthesizerServiceType = lazyRecognizerServiceType;
            return this;
        }

        public Builder setGoogleCredentialsResourceFile(RawResourceLazyProvider lazyProvider) {
            this.googleCredentialsResourceFile = lazyProvider;
            return this;
        }

        public Builder setSpeechDictation(LazyProvider<Boolean> lazyProvider) {
            this.speechDictation = lazyProvider;
            return this;
        }

        public ComponentConfig build() {
            return new ComponentConfig(this);
        }
    }

    public interface OnUpdate {
        ComponentConfig run(ComponentConfig.Builder builder);
    }

    public interface LazyProvider<T> {
        @NonNull T get();
    }

    public interface RawResourceLazyProvider {
        @RawRes int get();
    }
}
