package com.chattylabs.sdk.android.voice;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RestrictTo;
import android.text.TextUtils;

import com.chattylabs.sdk.android.common.RequiredPermissions;
import com.chattylabs.sdk.android.common.Tag;
import com.chattylabs.sdk.android.common.internal.ILogger;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * By design there must be only one instance of this component at a time.
 * Otherwise the last instance is used instead.
 */
@dagger.Reusable
public interface ConversationalFlowComponent extends RequiredPermissions {
    String TAG = Tag.make("ConversationalFlowComponent");

    String DEFAULT_QUEUE_ID = "default_queue";

    // Synthesizer codes
    int SYNTHESIZER_AVAILABLE = 101;
    int SYNTHESIZER_AVAILABLE_BUT_INACTIVE = 102;
    int SYNTHESIZER_UNKNOWN_ERROR = 103;
    int SYNTHESIZER_LANGUAGE_NOT_SUPPORTED_ERROR = 104;
    int SYNTHESIZER_NOT_AVAILABLE_ERROR = 105;

    // Recognizer codes
    int RECOGNIZER_AVAILABLE = 201;
    int RECOGNIZER_NOT_AVAILABLE = 202;
    int RECOGNIZER_UNKNOWN_ERROR = 203;
    int RECOGNIZER_EMPTY_RESULTS_ERROR = 204;
    int RECOGNIZER_UNAVAILABLE_ERROR = 205;
    int RECOGNIZER_STOPPED_TOO_EARLY_ERROR = 206;
    int RECOGNIZER_RETRY_ERROR = 207;
    int RECOGNIZER_AFTER_PARTIALS_ERROR = 208;
    int RECOGNIZER_NO_SOUND_ERROR = 209;
    int RECOGNIZER_LOW_SOUND_ERROR = 210;

    int MIN_VOICE_RECOGNITION_TIME_LISTENING = 2000;

    /**
     * The callbacks to interact with.
     * <p>
     * Declared as single interfaces so we can easily make use of lambda expressions and isolate each callback.
     */

    interface OnSetup {
        void execute(Status status);
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    interface SynthesizerListenerContract {}

    interface OnSynthesizerError extends SynthesizerListenerContract {
        void execute(String utteranceId, int errorCode);
    }

    interface OnSynthesizerInitialised extends SynthesizerListenerContract {
        void execute(int synthesizerStatus);
    }

    interface OnSynthesizerStart extends SynthesizerListenerContract {
        void execute(String utteranceId);
    }

    interface OnSynthesizerDone extends SynthesizerListenerContract {
        void execute(String utteranceId);
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    interface RecognizerListenerContract {}

    interface OnRecognizerReady extends RecognizerListenerContract {
        void execute(Bundle params);
    }

    interface OnRecognizerResults extends RecognizerListenerContract {
        void execute(List<String> results, float[] confidences);
    }

    interface OnRecognizerPartialResults extends RecognizerListenerContract {
        void execute(List<String> results, float[] confidences);
    }

    interface OnRecognizerMostConfidentResult extends RecognizerListenerContract {
        void execute(String result);
    }

    interface OnRecognizerError extends RecognizerListenerContract {
        void execute(int error, int originalError);
    }

    /**
     * Extra interfaces
     */

    interface SpeechRecognizerCreator {
        android.speech.SpeechRecognizer create();
    }

    interface Status {
        boolean isAvailable();
        int getSynthesizerStatus();
        int getRecognizerStatus();
    }

    @FunctionalInterface
    interface Consumer<T> {

        /**
         * Performs this operation on the given argument.
         *
         * @param t the input argument
         */
        void accept(@Nullable T t);

        /**
         * Returns a composed {@code Consumer} that performs, in sequence, this
         * operation followed by the {@code after} operation. If performing either
         * operation throws an exception, it is relayed to the caller of the
         * composed operation.  If performing this operation throws an exception,
         * the {@code after} operation will not be performed.
         *
         * @param after the operation to perform after this operation
         * @return a composed {@code Consumer} that performs in sequence this
         * operation followed by the {@code after} operation
         * @throws NullPointerException if {@code after} is null
         */
        default Consumer<T> andThen(Consumer<? super T> after) {
            Objects.requireNonNull(after);
            return (@Nullable T t) -> { accept(t); after.accept(t); };
        }
    }

    @SuppressWarnings("unchecked")
    interface SpeechRecognizer {
        <T extends RecognizerListenerContract> void listen(T... listeners);

        void stop();

        void shutdown();

        void cancel();

        void setRmsDebug(boolean debug);

        void setNoSoundThreshold(float maxValue);

        void setLowSoundThreshold(float maxValue);
    }

    @SuppressWarnings("unchecked")
    interface SpeechSynthesizer {
        void setup(OnSynthesizerInitialised onSynthesizerInitialised);

        void addFilter(TextFilter filter);

        <T extends SynthesizerListenerContract> void playText(String text, String queueId, T... listeners);

        <T extends SynthesizerListenerContract> void playText(String text, T... listeners);

        <T extends SynthesizerListenerContract> void playSilence(long durationInMillis, String queueId, T... listeners);

        <T extends SynthesizerListenerContract> void playSilence(long durationInMillis, T... listeners);

        void releaseCurrentQueue();

        void holdCurrentQueue();

        void stop();

        void resume();

        void shutdown();

        boolean isEmpty();

        boolean isCurrentQueueEmpty();

        String getLastQueueId();

        @Nullable
        String getNextQueueId();

        String getCurrentQueueId();

        Set<String> getQueueSet();
    }

    /**
     * Utils
     */

    static boolean anyMatch(@NonNull List<String> data, @NonNull List<String> expected) {
        String expectedJoined = TextUtils.join("|", expected);
        if (data.size() > 1) {
            for (String str : data) {
                if (matches(str, expectedJoined)) {
                    return true;
                }
            }
        }
        else {
            return matches(data.get(0), expectedJoined);
        }
        return false;
    }

    static boolean matches(@NonNull String str, @NonNull String patternStr) {
        Pattern pattern = Pattern.compile("\\b(" + patternStr + ")\\b", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(str);
        return matcher.find();
    }

    @Nullable
    static String selectMostConfidentResult(List<String> results, float[] confidences) {
        String message = null;
        if (results != null && !results.isEmpty() && results.get(0).length() > 0) {
            float last = 0;
            if (confidences != null && confidences.length > 0) {
                for (int a = 0; a < confidences.length; a++) {
                    if (confidences[a] >= last) {
                        last = confidences[a];
                        message = results.get(a);
                    }
                }
            }
            else {
                message = results.get(0);
            }
        }
        return message;
    }

    /**
     * Main Component Contract
     */

    void setup(Context context, OnSetup onSetup);

    void setVoiceConfig(VoiceConfig voiceConfiguration);

    void updateVoiceConfig(VoiceConfig.Update update);

    SpeechSynthesizer getSpeechSynthesizer(Context context);

    SpeechRecognizer getSpeechRecognizer(Context context);

    Conversation create(Context context);

    void stop();

    void shutdown();
}
