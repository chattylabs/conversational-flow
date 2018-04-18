package com.chattylabs.sdk.android.voice;

import android.content.Context;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RestrictTo;
import android.text.TextUtils;
import android.util.Log;

import com.chattylabs.sdk.android.core.RequiredPermissions;
import com.chattylabs.sdk.android.core.Tag;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.SoftReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * By design there must be only one instance of this component at a time.
 * Otherwise the last instance is used instead.
 */
@dagger.Reusable
public interface VoiceInteractionComponent extends RequiredPermissions {

    String TAG = Tag.make(VoiceInteractionComponent.class);

    String DEFAULT_GROUP = "default_group";

    int TEXT_TO_SPEECH_AVAILABLE = 101;
    int TEXT_TO_SPEECH_AVAILABLE_BUT_INACTIVE = 102;
    int TEXT_TO_SPEECH_UNKNOWN_ERROR = 103;
    int TEXT_TO_SPEECH_LANGUAGE_NOT_SUPPORTED_ERROR = 104;
    int TEXT_TO_SPEECH_NOT_AVAILABLE_ERROR = 105;

    int VOICE_RECOGNITION_AVAILABLE = 201;
    int VOICE_RECOGNITION_NOT_AVAILABLE = 202;
    int VOICE_RECOGNITION_UNKNOWN_ERROR = 203;
    int VOICE_RECOGNITION_EMPTY_RESULTS_ERROR = 204;
    int VOICE_RECOGNITION_UNAVAILABLE_ERROR = 205;
    int VOICE_RECOGNITION_STOPPED_TOO_EARLY_ERROR = 206;
    int VOICE_RECOGNITION_RETRY_ERROR = 207;
    int VOICE_RECOGNITION_AFTER_PARTIALS_ERROR = 208;
    int VOICE_RECOGNITION_NO_SOUND_ERROR = 209;
    int VOICE_RECOGNITION_LOW_SOUND_ERROR = 210;

    int MIN_LISTENING_TIME = 2000;

    /**
     * Handles SharedPreferences keys related to this component
     */
    interface Preferences {
    }

    /**
     * The callbacks to interact with.
     * <p>
     * Declared as single interfaces so we can easily make use of lambda expressions and isolate each callback.
     */

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    interface TextToSpeechListeners {}
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    interface VoiceRecognitionListeners {}

    interface OnSetupListener extends TextToSpeechListeners {
        void execute(VoiceInteractionStatus status);
    }

    interface OnTextToSpeechErrorListener extends TextToSpeechListeners {
        void execute(String utteranceId, int errorCode);
    }

    interface OnTextToSpeechInitialisedListener extends TextToSpeechListeners {
        void execute(int status);
    }

    interface OnTextToSpeechStartedListener extends TextToSpeechListeners {
        void execute(String utteranceId);
    }

    interface OnTextToSpeechDoneListener extends TextToSpeechListeners {
        void execute(String utteranceId);
    }

    interface OnVoiceRecognitionReadyListener extends VoiceRecognitionListeners {
        void execute(Bundle params);
    }

    interface OnVoiceRecognitionResultsListener extends VoiceRecognitionListeners {
        void execute(List<String> results, float[] confidences);
    }

    interface OnVoiceRecognitionPartialResultsListener extends VoiceRecognitionListeners {
        void execute(List<String> results, float[] confidences);
    }

    interface OnVoiceRecognitionMostConfidentResultListener extends VoiceRecognitionListeners {
        void execute(String result);
    }

    interface OnVoiceRecognitionErrorListener extends VoiceRecognitionListeners {
        void execute(int error, int originalError);
    }

    /**
     * Extra interfaces
     */

    interface OnLogListener {
        void execute(String item);
    }

    interface SpeechRecognizerCreator {
        android.speech.SpeechRecognizer create();
    }

    interface VoiceInteractionStatus {
        boolean isAvailable();
        int getTextToSpeechStatus();
        int getSpeechRecognizerStatus();
    }

    interface MessageFilter {
        String apply(String message);
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
        <T extends VoiceRecognitionListeners> void listen(T... listeners);

        void setBluetoothScoRequired(boolean required);

        void shutdown();

        void cancel();

        void setRmsDebug(boolean debug);
    }

    @SuppressWarnings("unchecked")
    interface SpeechSynthesizer {
        void addFilter(MessageFilter filter);

        void setBluetoothScoRequired(boolean required);

        <T extends TextToSpeechListeners> void play(String text, String groupId, T... listeners);

        <T extends TextToSpeechListeners> void play(String text, T... listeners);

        <T extends TextToSpeechListeners> void playSilence(long durationInMillis, String groupId, T... listeners);

        <T extends TextToSpeechListeners> void playSilence(long durationInMillis, T... listeners);

        void pause();

        void unPause();

        void resume();

        void shutdown();

        boolean isEmpty();

        boolean isGroupQueueEmpty();

        boolean isPaused();

        String lastGroup();

        @Nullable
        String nextGroup();

        String group();

        Set<String> groupQueue();
    }

    interface Conversation extends Flow.Edge {

        int FLAG_ENABLE_ON_LOW_SOUND_ERROR_MESSAGE = 0x8F4E00;

        @IntDef({FLAG_ENABLE_ON_LOW_SOUND_ERROR_MESSAGE})
        @Retention(RetentionPolicy.SOURCE)
        @interface Flag {}

        SpeechSynthesizer getSpeechSynthesizer();

        SpeechRecognizer getSpeechRecognizer();

        void addFlag(@Flag int flag);

        void removeFlag(@Flag int flag);

        boolean hasFlag(@Flag int flag);

        void addNode(@NonNull Node node);

        EdgeSource prepare();

        @Override
        void addEdge(@NonNull Node node, @NonNull Node incomingEdge);

        void start(Node root);

        void next();
    }

    /**
     * TODO: documentation...
     */
    class Instance {
        transient static SoftReference<VoiceInteractionComponent> instanceOf;
        static VoiceInteractionComponent getInstanceOf() {
            synchronized (Instance.class) {
                if ((instanceOf == null) || (instanceOf.get() == null))
                {
                    Log.w(TAG, "New instance of SoftReference<VoiceInteractionComponent>");
                    return new SoftReference<>(new VoiceInteractionComponentImpl()).get();
                }
                return instanceOf.get();
            }
        }
        private Instance(){}
    }

    /**
     * Utilities related to this component
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
        Pattern pattern = Pattern.compile("^.*?(" + patternStr + ").*$", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(str);
        return matcher.find();
    }

    static String logTime(String message) {
        SimpleDateFormat formatter = new SimpleDateFormat("hh:mm:ss.SSS", Locale.getDefault());
        String time = formatter.format(Calendar.getInstance().getTime());
        return time + " " + message;
    }

    static String getTextToSpeechErrorType(int error) {
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

    static String getVoiceRecognitionErrorType(int error) {
        switch (error) {
            case android.speech.SpeechRecognizer.ERROR_AUDIO:
                return "ERROR_AUDIO";
            case android.speech.SpeechRecognizer.ERROR_CLIENT:
                return "ERROR_CLIENT";
            case android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                return "ERROR_INSUFFICIENT_PERMISSIONS";
            case android.speech.SpeechRecognizer.ERROR_NETWORK:
                return "ERROR_NETWORK";
            case android.speech.SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                return "ERROR_NETWORK_TIMEOUT";
            case android.speech.SpeechRecognizer.ERROR_NO_MATCH:
                return "ERROR_NO_MATCH";
            case android.speech.SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                return "ERROR_RECOGNIZER_BUSY";
            case android.speech.SpeechRecognizer.ERROR_SERVER:
                return "ERROR_SERVER";
            case android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                return "ERROR_SPEECH_TIMEOUT";
            default:
                return "ERROR_UNKNOWN";
        }
    }

    @Nullable
    static String selectMostConfidentResult(List<String> results, float[] confidences) {
        String message = null;
        if (results != null && results.size() > 0 && results.get(0).length() > 0) {
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
     * Contract
     */

    void setup(Context context, OnSetupListener onSetupListener);

    void setBluetoothScoRequired(Context context, boolean required);

    SpeechSynthesizer getSpeechSynthesizer(Context context);

    SpeechRecognizer getSpeechRecognizer(Context context);

    Conversation createConversation(Context context);

    void stop();

    void shutdown();
}
