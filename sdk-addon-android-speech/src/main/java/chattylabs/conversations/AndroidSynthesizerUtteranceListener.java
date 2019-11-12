package chattylabs.conversations;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import androidx.annotation.NonNull;

import com.chattylabs.android.commons.Tag;

import static chattylabs.conversations.SynthesizerListener.Status.*;

class AndroidSynthesizerUtteranceListener extends BaseSynthesizerUtteranceListener {
    private static final String TAG = Tag.make("AndroidSynthesizerUtteranceListener");

    private final AndroidSynthesizerUtteranceSupplier supplier;
    private final UtteranceProgressListener utteranceProgressListener;

    AndroidSynthesizerUtteranceListener(@NonNull Context context,
                                        @NonNull BaseSpeechSynthesizer speechSynthesizer,
                                        @NonNull AndroidSynthesizerUtteranceSupplier supplier,
                                        @Mode int mode) {
        super(speechSynthesizer, mode, TAG);
        this.supplier = supplier;
        utteranceProgressListener = new UtteranceProgressListener() {
            private String onStarted;

            @Override
            public void onStart(String s) {
                // https://android.googlesource.com/platform/cts/+/master/tests/tests/speech/src/android/speech/tts/cts/TextToSpeechWrapper.java#232
                //
                // Due to a bug in the framework onStart() is called twice for
                // synthesizeToFile requests.
                if (onStarted == null || !onStarted.equals(s)) {
                    onStarted = s;
                    AndroidSynthesizerUtteranceListener.this.onStart(s);
                }
            }

            @Override
            public void onDone(String s) {
                speechSynthesizer.handleSynthesizedFile(context,
                        AndroidSynthesizerUtteranceListener.this, s);
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

    AndroidSynthesizerUtteranceListener(@NonNull Context context,
                                        @NonNull BaseSpeechSynthesizer speechSynthesizer,
                                        @NonNull AndroidSynthesizerUtteranceSupplier supplier) {
        this(context, speechSynthesizer, supplier, Mode.CHECKING);
    }

    @Override
    public void onDone(String utteranceId) {
        if (mode == Mode.CHECKING && utteranceId.equals(supplier.getCheckingUtteranceId())) {
            //clearTimeout(utteranceId);
            logger.v(TAG, "[%s] - on done <%s> -> go to checkStatus language", utteranceId, getCurrentQueueId());
            setSpeaking(false);
            supplier.setChecking(false);
            supplier.checkLanguage(true);
        } else {
            super.onDone(utteranceId);
        }
    }

    @Override
    public void onError(String utteranceId, int errorCode) {
        logger.e(TAG, "[%s] - on error <%s> -> stop timeout", utteranceId, getCurrentQueueId());
        logger.e(TAG, "[%s] - error code: %s", utteranceId, getErrorType(errorCode));
        if (mode == Mode.CHECKING && utteranceId.equals(supplier.getCheckingUtteranceId())) {
            //clearTimeout(utteranceId);
            supplier.setChecking(false);
            shutdown();
            if (errorCode == TextToSpeech.ERROR_NOT_INSTALLED_YET) {
                supplier.getOnStatusCheckedListener().execute(NOT_AVAILABLE_ERROR);
            } else {
                supplier.getOnStatusCheckedListener().execute(UNKNOWN_ERROR);
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

    interface AndroidSynthesizerUtteranceSupplier {
        @NonNull
        SynthesizerListener.OnStatusChecked getOnStatusCheckedListener();

        void checkLanguage(boolean fromUtterance);

        String getCheckingUtteranceId();

        void setChecking(boolean checking);
    }
}
