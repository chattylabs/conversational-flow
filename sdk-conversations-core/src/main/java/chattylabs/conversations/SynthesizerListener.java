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
    }
}