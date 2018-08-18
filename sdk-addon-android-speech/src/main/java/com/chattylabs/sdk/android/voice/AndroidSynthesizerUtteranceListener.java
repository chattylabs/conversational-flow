package com.chattylabs.sdk.android.voice;

import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.support.annotation.NonNull;

class AndroidSynthesizerUtteranceListener extends BaseSynthesizerUtteranceListener {

    private static final String LOG_LABEL = "ANDROID TTS";

    private final AndroidSynthesizerUtteranceSupplier supplier;
    private final UtteranceProgressListener utteranceProgressListener;

    AndroidSynthesizerUtteranceListener(@NonNull BaseSpeechSynthesizer speechSynthesizer,
                                        @NonNull AndroidSynthesizerUtteranceSupplier supplier,
                                        @Mode int mode) {
        super(speechSynthesizer, mode);
        this.supplier = supplier;
        utteranceProgressListener = new UtteranceProgressListener() {
            @Override
            public void onStart(String s) {
                AndroidSynthesizerUtteranceListener.this.onStart(s);
            }

            @Override
            public void onDone(String s) {
                AndroidSynthesizerUtteranceListener.this.onDone(s);
            }

            @Override
            public void onError(String s) {
                // No Impl
            }

            @Override
            public void onError(String utteranceId, int errorCode) {
                AndroidSynthesizerUtteranceListener.this.onError(utteranceId, errorCode);
            }
        };
    }

    AndroidSynthesizerUtteranceListener(@NonNull BaseSpeechSynthesizer speechSynthesizer,
                                        @NonNull AndroidSynthesizerUtteranceSupplier supplier) {
        this(speechSynthesizer, supplier, Mode.INTIALIZE);
    }

    @Override
    public void onDone(String utteranceId) {
        if (mode == Mode.INTIALIZE) {
            super.onDone(utteranceId);
            return;
        }

        if (utteranceId.equals(supplier.getCheckingUtteranceId())) {
            clearTimeout(utteranceId);
            logger.v(getTag(), "ANDROID TTS[%s] - on done <%s> -> go to setup language", utteranceId, getCurrentQueueId());
            supplier.checkLanguage(true);
        } else {
            super.onDone(utteranceId);
        }
    }

    @Override
    public void onError(String utteranceId, int errorCode) {
        if (mode == Mode.INTIALIZE) {
            super.onError(utteranceId, errorCode);
            return;
        }

        logger.e(getTag(), "ANDROID TTS[%s] - on error <%s> -> stop timeout", utteranceId, getCurrentQueueId());
        logger.e(getTag(), "ANDROID TTS[%s] - error code: %s", utteranceId, getErrorType(errorCode));
        if (utteranceId.equals(supplier.getCheckingUtteranceId())) {
            clearTimeout(utteranceId);
            shutdown();
            if (errorCode == TextToSpeech.ERROR_NOT_INSTALLED_YET) {
                supplier.getOnSetupSynthesizer().execute(SynthesizerListener.Status.NOT_AVAILABLE_ERROR);
            } else {
                supplier.getOnSetupSynthesizer().execute(SynthesizerListener.Status.UNKNOWN_ERROR);
            }
        } else {
            super.onError(utteranceId, errorCode);
        }
    }

    public UtteranceProgressListener getUtteranceProgressListener() {
        return this.utteranceProgressListener;
    }

    @Override
    protected String getErrorType(int error) {
        switch (error) {
            case TextToSpeech.ERROR:
                return "ERROR";
            case TextToSpeech.ERROR_INVALID_REQUEST:
                return "ERROR_INVALID_REQUEST";
            case TextToSpeech.ERROR_NETWORK:
                return "ERROR_NETWORK";
            case TextToSpeech.ERROR_NETWORK_TIMEOUT:
                return "ERROR_NETWORK_TIMEOUT";
            case TextToSpeech.ERROR_NOT_INSTALLED_YET:
                return "ERROR_NOT_INSTALLED_YET";
            case TextToSpeech.ERROR_OUTPUT:
                return "ERROR_OUTPUT";
            case TextToSpeech.ERROR_SERVICE:
                return "ERROR_SERVICE";
            case TextToSpeech.ERROR_SYNTHESIS:
                return "ERROR_SYNTHESIS";
            default:
                return "ERROR_UNKNOWN";
        }
    }

    @Override
    String getTtsLogLabel() {
        return LOG_LABEL;
    }

    interface AndroidSynthesizerUtteranceSupplier {
        @NonNull
        SynthesizerListener.OnSetup getOnSetupSynthesizer();

        void checkLanguage(boolean fromUtterance);

        String getCheckingUtteranceId();
    }
}
