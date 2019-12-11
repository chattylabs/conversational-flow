package chattylabs.conversations;

import android.content.Context;
import android.speech.tts.UtteranceProgressListener;

import androidx.annotation.NonNull;

import chattylabs.android.commons.Tag;
import chattylabs.android.commons.internal.ILogger;

class BaseSynthesizerUtteranceListener implements SynthesizerUtteranceListener {
    private static final String TAG = Tag.make("BaseSynthesizerUtteranceListener");

    protected final ILogger logger;

    private final BaseSpeechSynthesizer speechSynthesizer;
    private final UtteranceProgressListener utteranceProgressListener;

    BaseSynthesizerUtteranceListener(@NonNull Context context, @NonNull final BaseSpeechSynthesizer speechSynthesizer) {
        this.speechSynthesizer = speechSynthesizer;
        this.logger = speechSynthesizer.logger;
        this.utteranceProgressListener = new UtteranceProgressListener() {
            private String onStarted;

            @Override
            public void onStart(String utteranceId) {
                // https://android.googlesource.com/platform/cts/+/master/tests/tests/speech/src/android/speech/tts/cts/TextToSpeechWrapper.java#232
                //
                // Due to a bug in the framework onStart() is called twice for
                // synthesizeToFile requests.
                if (onStarted == null || !onStarted.equals(utteranceId)) {
                    onStarted = utteranceId;
                    BaseSynthesizerUtteranceListener.this.onStart(utteranceId);
                }
            }

            @Override
            public void onDone(String utteranceId) {
                if (speechSynthesizer.getConfiguration().isForceLanguageDetection())
                    speechSynthesizer.forceDestroyTTS();
                speechSynthesizer.handleSynthesizedFile(context, BaseSynthesizerUtteranceListener.this, utteranceId);
            }

            @Override
            public void onError(String utteranceId) {
                // No Impl
            }

            @Override
            public void onError(String utteranceId, int errorCode) {
                if (speechSynthesizer.getConfiguration().isForceLanguageDetection())
                    speechSynthesizer.forceDestroyTTS();
                BaseSynthesizerUtteranceListener.this.onError(utteranceId, errorCode);
            }
        };
    }

    public UtteranceProgressListener getUtteranceProgressListener() {
        return utteranceProgressListener;
    }

    @Override
    public void onStart(String utteranceId) {
        logger.v(TAG, "[%s] - on start", utteranceId);
        speechSynthesizer.getAudioManager().requestAudioFocus(null,
                speechSynthesizer.getConfiguration().isAudioExclusiveRequiredForSynthesizer());
        speechSynthesizer.executeListener(utteranceId, BaseSpeechSynthesizer.ON_START, 0);
    }

    @Override
    public void onDone(String utteranceId) {
        speechSynthesizer.setSpeaking(false);
        if (speechSynthesizer.isOnQueue()) {
            logger.v(TAG, "[%s] - on done <%s> - check for Empty Queue", utteranceId, speechSynthesizer.getCurrentQueueId());
            speechSynthesizer.poolQueueFromLast();
            speechSynthesizer.moveToNextQueueIfNeeded();
        }
        logger.v(TAG, "[%s] - on done <%s> - execute listener.onDone", utteranceId, speechSynthesizer.getCurrentQueueId());
        speechSynthesizer.removeAndExecuteListener(utteranceId, BaseSpeechSynthesizer.ON_DONE, 0);
        if (speechSynthesizer.isOnQueue() && speechSynthesizer.isQueueEmpty()) speechSynthesizer.shutdown();
        else if (speechSynthesizer.isOnQueue()) speechSynthesizer.resume();
    }

    @Override
    public void onError(String utteranceId, int errorCode) {
        speechSynthesizer.setSpeaking(false);
        logger.e(TAG, "[%s] - error <%s> - code: %s", utteranceId, speechSynthesizer.getCurrentQueueId(), getErrorType(errorCode));
        speechSynthesizer.poolQueueFromLast();
        speechSynthesizer.moveToNextQueueIfNeeded();
        speechSynthesizer.prune();
        speechSynthesizer.removeAndExecuteListener(utteranceId, BaseSpeechSynthesizer.ON_DONE, 0);
    }

    protected String getErrorType(int error) {
        return "Error Code: " + error;
    }
}
