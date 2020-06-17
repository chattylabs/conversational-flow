package chattylabs.conversations;

import androidx.annotation.RestrictTo;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public interface SynthesizerListener {

    interface OnStatusChecked extends SynthesizerListener {
        void execute(int synthesizerStatus);
    }

    interface OnPrepared extends SynthesizerListener {
        void execute(int synthesizerStatus);
    }

    interface OnStart extends SynthesizerListener {
        void execute(String utteranceId);
    }

    interface OnDone extends SynthesizerListener {
        void execute(String utteranceId);
    }

    interface OnError extends SynthesizerListener {
        void execute(String utteranceId, int errorCode);
    }

    abstract class Status {
        public static final int AVAILABLE = 101;
        public static final int AVAILABLE_BUT_INACTIVE = 102;
        public static final int UNKNOWN_ERROR = 103;
        public static final int LANGUAGE_NOT_SUPPORTED_ERROR = 104;
        public static final int NOT_AVAILABLE_ERROR = 105;
        public static final int SUCCESS = 106;
        public static final int ERROR = 107;
        public static final int TIMEOUT = 108;

        public static String getString(int status) {
            switch (status) {
                case AVAILABLE_BUT_INACTIVE:
                    return "AVAILABLE_BUT_INACTIVE";
                case UNKNOWN_ERROR:
                    return "UNKNOWN_ERROR";
                case LANGUAGE_NOT_SUPPORTED_ERROR:
                    return "LANGUAGE_NOT_SUPPORTED_ERROR";
                case NOT_AVAILABLE_ERROR:
                    return "NOT_AVAILABLE_ERROR";
                case SUCCESS:
                    return "SUCCESS";
                case ERROR:
                    return "ERROR";
                case TIMEOUT:
                    return "TIMEOUT";
                default:
                    return "AVAILABLE";
            }
        }
    }
}