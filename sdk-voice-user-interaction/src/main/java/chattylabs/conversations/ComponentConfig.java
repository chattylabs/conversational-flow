package chattylabs.conversations;

import androidx.annotation.NonNull;
import androidx.annotation.RawRes;

import java.util.Locale;

public class ComponentConfig {
    private LazyProvider<Locale> speechLanguage;
    private LazyProvider<Boolean> customBeepEnabled;
    private LazyProvider<Boolean> bluetoothScoRequired;
    private LazyProvider<Boolean> audioExclusiveRequiredForSynthesizer;
    private LazyProvider<Boolean> audioExclusiveRequiredForRecognizer;
    private LazyProvider<Integer> bluetoothScoAudioMode;
    private LazyProvider<Class<? extends SpeechRecognizer>> recognizerServiceType;
    private LazyProvider<Class<? extends SpeechSynthesizer>> synthesizerServiceType;
    private RawResourceLazyProvider googleCredentialsResourceFile;
    /**
     * Only for Google Speech addon to allow more time to record audio as on a dictation.
     */
    private LazyProvider<Boolean> speechDictation;
    /**
     * If enabled, it destroys the TTS every time an utterance has finished.
     * This allows the Engine to cleanly reset and detect language of next utterance.
     */
    private LazyProvider<Boolean> forceLanguageDetection;

    private ComponentConfig(Builder builder) {
        speechLanguage = builder.speechLanguage;
        customBeepEnabled = builder.customBeepEnabled;
        bluetoothScoRequired = builder.bluetoothScoRequired;
        audioExclusiveRequiredForSynthesizer = builder.audioExclusiveRequiredForSynthesizer;
        audioExclusiveRequiredForRecognizer = builder.audioExclusiveRequiredForRecognizer;
        bluetoothScoAudioMode = builder.bluetoothScoAudioMode;
        recognizerServiceType = builder.recognizerServiceType;
        synthesizerServiceType = builder.synthesizerServiceType;
        googleCredentialsResourceFile = builder.googleCredentialsResourceFile;
        speechDictation = builder.speechDictation;
        forceLanguageDetection = builder.forceLanguageDetection;
    }

    public Locale getSpeechLanguage() {
        return speechLanguage.get();
    }

    public boolean isCustomBeepEnabled() {
        return customBeepEnabled.get();
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

    public Integer getBluetoothScoAudioMode() {
        return bluetoothScoAudioMode.get();
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

    public boolean isForceLanguageDetection() {
        return forceLanguageDetection.get();
    }

    void update(ComponentConfig config) {
        speechLanguage = config.speechLanguage;
        customBeepEnabled = config.customBeepEnabled;
        bluetoothScoRequired = config.bluetoothScoRequired;
        audioExclusiveRequiredForSynthesizer = config.audioExclusiveRequiredForSynthesizer;
        audioExclusiveRequiredForRecognizer = config.audioExclusiveRequiredForRecognizer;
        bluetoothScoAudioMode = config.bluetoothScoAudioMode;
        recognizerServiceType = config.recognizerServiceType;
        synthesizerServiceType = config.synthesizerServiceType;
        googleCredentialsResourceFile = config.googleCredentialsResourceFile;
        speechDictation = config.speechDictation;
        forceLanguageDetection = config.forceLanguageDetection;
    }

    public static final class Builder {
        private LazyProvider<Locale> speechLanguage;
        private LazyProvider<Boolean> customBeepEnabled;
        private LazyProvider<Boolean> bluetoothScoRequired;
        private LazyProvider<Boolean> audioExclusiveRequiredForSynthesizer;
        private LazyProvider<Boolean> audioExclusiveRequiredForRecognizer;
        private LazyProvider<Integer> bluetoothScoAudioMode;
        private LazyProvider<Class<? extends SpeechRecognizer>> recognizerServiceType;
        private LazyProvider<Class<? extends SpeechSynthesizer>> synthesizerServiceType;
        private RawResourceLazyProvider googleCredentialsResourceFile;
        private LazyProvider<Boolean> speechDictation;
        private LazyProvider<Boolean> forceLanguageDetection;

        public Builder() {
        }

        public Builder(ComponentConfig copy) {
            speechLanguage = copy.speechLanguage;
            customBeepEnabled = copy.customBeepEnabled;
            bluetoothScoRequired = copy.bluetoothScoRequired;
            audioExclusiveRequiredForSynthesizer = copy.audioExclusiveRequiredForSynthesizer;
            audioExclusiveRequiredForRecognizer = copy.audioExclusiveRequiredForRecognizer;
            bluetoothScoAudioMode = copy.bluetoothScoAudioMode;
            recognizerServiceType = copy.recognizerServiceType;
            synthesizerServiceType = copy.synthesizerServiceType;
            googleCredentialsResourceFile = copy.googleCredentialsResourceFile;
            speechDictation = copy.speechDictation;
            forceLanguageDetection = copy.forceLanguageDetection;
        }

        public Builder setSpeechLanguage(LazyProvider<Locale> speechLanguage) {
            this.speechLanguage = speechLanguage;
            return this;
        }

        public Builder setCustomBeepEnabled(LazyProvider<Boolean> customBeepEnabled) {
            this.customBeepEnabled = customBeepEnabled;
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

        /**
         * Allowed values {{@link android.media.AudioManager#MODE_IN_CALL}}
         * or {{@link android.media.AudioManager#MODE_IN_COMMUNICATION}}
         */
        public Builder setBluetoothScoAudioMode(LazyProvider<Integer> lazyProvider) {
            this.bluetoothScoAudioMode = lazyProvider;
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

        public Builder setForceLanguageDetection(LazyProvider<Boolean> forceLanguageDetection) {
            this.forceLanguageDetection = forceLanguageDetection;
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
