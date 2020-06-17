package chattylabs.conversations;

import android.os.Bundle;

import androidx.annotation.RestrictTo;

import java.util.ArrayList;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public interface RecognizerListener {

    interface OnStatusChecked extends SynthesizerListener {
        void execute(int recognizerStatus);
    }

    interface OnPrepared extends RecognizerListener {
        void execute(int recognizerStatus);
    }

    interface OnReady extends RecognizerListener {
        void execute(Bundle params);
    }

    interface OnResults extends RecognizerListener {
        void execute(ArrayList<String> results, float[] confidences);
    }

    interface OnPartialResults extends RecognizerListener {
        void execute(ArrayList<String> results, float[] confidences);
    }

    interface OnMostConfidentResult extends RecognizerListener {
        void execute(String result);
    }

    interface OnError extends RecognizerListener {
        void execute(int error, int originalError);
    }

    abstract class Status {
        public static final int AVAILABLE = 201;
        public static final int NOT_AVAILABLE = 202;
        public static final int UNKNOWN_ERROR = 203;
        public static final int EMPTY_RESULTS_ERROR = 204;
        public static final int UNAVAILABLE_ERROR = 205;
        public static final int STOPPED_TOO_EARLY_ERROR = 206;
        public static final int RETRY_ERROR = 207;
        public static final int AFTER_PARTIAL_RESULTS_ERROR = 208;
        public static final int NO_SOUND_ERROR = 209;
        public static final int LOW_SOUND_ERROR = 210;

        public static String getString(int status) {
            switch (status) {
                case NOT_AVAILABLE:
                    return "NOT_AVAILABLE";
                case UNKNOWN_ERROR:
                    return "UNKNOWN_ERROR";
                case EMPTY_RESULTS_ERROR:
                    return "EMPTY_RESULTS_ERROR";
                case UNAVAILABLE_ERROR:
                    return "UNAVAILABLE_ERROR";
                case STOPPED_TOO_EARLY_ERROR:
                    return "STOPPED_TOO_EARLY_ERROR";
                case RETRY_ERROR:
                    return "RETRY_ERROR";
                case AFTER_PARTIAL_RESULTS_ERROR:
                    return "AFTER_PARTIAL_RESULTS_ERROR";
                case NO_SOUND_ERROR:
                    return "NO_SOUND_ERROR";
                case LOW_SOUND_ERROR:
                    return "LOW_SOUND_ERROR";
                default:
                    return "AVAILABLE";
            }
        }
    }
}
