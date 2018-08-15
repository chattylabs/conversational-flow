package com.chattylabs.sdk.android.voice;

import android.os.Bundle;
import android.support.annotation.RestrictTo;

import java.util.List;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public interface RecognizerListener {

    interface OnReady extends RecognizerListener {
        void execute(Bundle params);
    }

    interface OnResults extends RecognizerListener {
        void execute(List<String> results, float[] confidences);
    }

    interface OnPartialResults extends RecognizerListener {
        void execute(List<String> results, float[] confidences);
    }

    interface OnMostConfidentResult extends RecognizerListener {
        void execute(String result);
    }

    interface OnError extends RecognizerListener {
        void execute(int error, int originalError);
    }

    final class Status {
        private Status() {}
        public static final int AVAILABLE = 201;
        public static final int NOT_AVAILABLE = 202;
        public static final int UNKNOWN_ERROR = 203;
        public static final int EMPTY_RESULTS_ERROR = 204;
        public static final int UNAVAILABLE_ERROR = 205;
        public static final int STOPPED_TOO_EARLY_ERROR = 206;
        public static final int RETRY_ERROR = 207;
        public static final int AFTER_PARTIALS_ERROR = 208;
        public static final int NO_SOUND_ERROR = 209;
        public static final int LOW_SOUND_ERROR = 210;
    }
}
