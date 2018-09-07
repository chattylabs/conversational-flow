package com.chattylabs.sdk.android.voice;

import android.Manifest;
import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresPermission;
import android.support.annotation.WorkerThread;
import android.text.TextUtils;

import com.chattylabs.sdk.android.common.RequiredPermissions;
import com.chattylabs.sdk.android.common.Tag;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public interface ConversationalFlowComponent extends RequiredPermissions {
    String TAG = Tag.make("ConversationalFlowComponent");

    /**
     * It checks if the Synthesizer and Recognizer are available and returns a {@link ComponentStatus}
     * object as part of the {@link OnComponentSetup} interface.
     * <br/>Based on the returned {@link ComponentStatus} you can decide either to use only one or
     * all the functionality.
     * <br/><pre>{@code
     * component.setup(context, status -> {
     *      if (status.isAvailable()) {
     *      // start using the functionality
     *      }
     * });
     * }</pre>
     * @see OnComponentSetup
     */
    @WorkerThread
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    void setup(Context context, OnComponentSetup onComponentSetup);

    /**
     * It reuses the already set {@link ComponentConfig} allowing you to update only specific parts
     * of it through the {@link ComponentConfig.Update} interface.
     * <br/><pre>{@code
     * updateConfiguration(builder ->
     *          builder.setRecognizerServiceType(() -> AndroidSpeechRecognizer.class)
     *              .setSynthesizerServiceType(() -> AndroidSpeechSynthesizer.class)
     *              .setBluetoothScoRequired(() -> false)
     *              .setAudioExclusiveRequiredForSynthesizer(() -> false)
     *              .setAudioExclusiveRequiredForRecognizer(() -> true)
     *      ).build()
     * );
     * }</pre>
     * @see ComponentConfig.Update
     */
    void updateConfiguration(ComponentConfig.Update onUpdate);

    /**
     * Returns the current {@link SpeechSynthesizerComponent} configured.
     * <br/>You can use this component alone.
     * <br/>code...
     */
    SpeechSynthesizerComponent getSpeechSynthesizer(Context context);

    /**
     * Returns the current {@link SpeechRecognizerComponent} configured.
     * <br/>You can use this component alone.
     * <br/>Requires {@link Manifest.permission#RECORD_AUDIO}
     * <br/>code...
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    SpeechRecognizerComponent getSpeechRecognizer(Context context);

    /**
     * Returns a new instance of {@link Conversation}
     * <br/>code...
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    Conversation create(Context context);

    /**
     * Stops the current utterance playing or recording.
     * <br/>code...
     */
    void stop();

    /**
     * Shuts all internal components down and resets resources.
     * <br/>code...
     */
    void shutdown();

    /**
     * Helper that checks if a string on a list matches with another list of strings.
     * <br/>This is normally used internally to see if the text said by a user matches with
     * some expected strings.
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

    /**
     * Check if a specific string pattern interpreted as a whole word matches with a string.
     */
    static boolean matches(@NonNull String str, @NonNull String patternStr) {
        Pattern pattern = Pattern.compile("\\b(" + patternStr + ")\\b", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(str);
        return matcher.find();
    }

    /**
     * It returns the string result that satisfies the higher confidence, otherwise null.
     */
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

    static boolean isStatus(int code, int status) {
        return (code > 0) && (code & status) == status;
    }
}
