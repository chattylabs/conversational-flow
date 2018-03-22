package com.chattylabs.module.voice;

import android.content.Context;
import android.os.Bundle;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RestrictTo;
import android.text.TextUtils;
import android.util.Log;

import com.chattylabs.module.core.RequiredPermissions;
import com.chattylabs.module.core.Tag;

import java.lang.ref.SoftReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * By design there must be only one instance of this component at a time.
 * Otherwise the last instance is used instead.
 */
@dagger.Reusable
public interface VoiceInteractionComponent extends RequiredPermissions {

    String TAG = Tag.make(VoiceInteractionComponent.class);

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
    int VOICE_RECOGNITION_UNEXPECTED_SHORT_TIME_ERROR = 206;
    int VOICE_RECOGNITION_RETRY_ERROR = 207;
    int VOICE_RECOGNITION_AFTER_PARTIALS_ERROR = 208;
    int VOICE_RECOGNITION_NO_MATCH_ERROR = 209;
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
        SpeechRecognizer create();
    }

    interface VoiceInteractionStatus {
        boolean isAvailable();
        int getTextToSpeechStatus();
        int getSpeechRecognizerStatus();
    }

    interface MessageFilter {
        String apply(String message);
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
            case SpeechRecognizer.ERROR_AUDIO:
                return "ERROR_AUDIO";
            case SpeechRecognizer.ERROR_CLIENT:
                return "ERROR_CLIENT";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                return "ERROR_INSUFFICIENT_PERMISSIONS";
            case SpeechRecognizer.ERROR_NETWORK:
                return "ERROR_NETWORK";
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                return "ERROR_NETWORK_TIMEOUT";
            case SpeechRecognizer.ERROR_NO_MATCH:
                return "ERROR_NO_MATCH";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                return "ERROR_RECOGNIZER_BUSY";
            case SpeechRecognizer.ERROR_SERVER:
                return "ERROR_SERVER";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
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

    void addSpeechFilter(Context context, MessageFilter filter);

    void setBluetoothScoRequired(Context context, boolean required);

    <T extends TextToSpeechListeners> void play(Context context, String text, String groupId, T... listeners);

    <T extends TextToSpeechListeners> void play(Context context, String text, T... listeners);

    <T extends VoiceRecognitionListeners> void listen(Context context, T... listeners);

    boolean hasNextSpeech();

    boolean isSpeechPaused();

    String speechGroup();

    void pauseSpeech();

    void resumeSpeech();

    void stop();

    void shutdown();

    void shutdownTextToSpeech();

    void shutdownVoiceRecognition();

    void cancelVoiceRecognition();
}
