package chattylabs.conversations;

import android.Manifest;
import android.app.Activity;
import android.content.Context;

import chattylabs.android.commons.RequiredPermissions;
import chattylabs.android.commons.Tag;
import chattylabs.android.commons.internal.ILogger;

public interface ConversationalFlow extends RequiredPermissions {
    String TAG = Tag.make("ConversationalFlow");

    void resetConfiguration(Context context);

    /**
     * Checks whether the Synthesizer is available and returns a {@link SynthesizerListener.Status}
     * object within the {@link SynthesizerListener.OnStatusChecked} interface.
     * <br/><pre>{@code
     * component.checkSpeechSynthesizerStatus(context, status -> {
     *      if (status == SynthesizerListener.Status.AVAILABLE) {
     *          // the functionality is available
     *      }
     * });
     * }</pre>
     * This operation should be handled on a working thread.
     *
     * @see SynthesizerListener.OnStatusChecked
     * @see SynthesizerListener.Status
     */
    void checkSpeechSynthesizerStatus(Context context, SynthesizerListener.OnStatusChecked listener);

    /**
     * Checks whether the Recognizer is available and returns a {@link RecognizerListener.Status}
     * object within the {@link RecognizerListener.OnStatusChecked} interface.
     * <br/><pre>{@code
     * component.checkSpeechRecognizerStatus(context, status -> {
     *      if (status == RecognizerListener.Status.AVAILABLE) {
     *          // the functionality is available
     *      }
     * });
     * }</pre>
     * This operation should be handled on a working thread.
     *
     * @see RecognizerListener.OnStatusChecked
     * @see RecognizerListener.Status
     */
    void checkSpeechRecognizerStatus(Context context, RecognizerListener.OnStatusChecked listener);

    /**
     * Reuses the already set {@link ComponentConfig} allowing you to update only specific parts
     * of it through the {@link ComponentConfig.OnUpdate} interface.
     * <br/><pre>{@code
     * updateConfiguration(builder ->
     *          builder.setRecognizerServiceType(() -> AndroidSpeechRecognizer.class)
     *              .setSynthesizerServiceType(() -> AndroidSpeechSynthesizer.class)
     *              .setBluetoothScoRequired(() -> false)
     *              .setAudioExclusiveRequiredForSynthesizer(() -> false)
     *              .setAudioExclusiveRequiredForRecognizer(() -> true)
     *      ).build());
     * }</pre>
     *
     * @see ComponentConfig
     * @see ComponentConfig.OnUpdate
     */
    void updateConfiguration(ComponentConfig.OnUpdate listener);

    /**
     * Returns the current {@link SpeechSynthesizer} configured.
     * <br/>You can use this component alone.
     * @see SpeechSynthesizer
     */
    SpeechSynthesizer getSpeechSynthesizer(Context context);

    /**
     * Returns the current {@link SpeechRecognizer} configured.
     * <br/>You can use this component alone.
     * <br/>Requires {@link Manifest.permission#RECORD_AUDIO}
     */
    SpeechRecognizer getSpeechRecognizer(Context context);

    void loadSynthesizerInstallation(Activity activity, SynthesizerListener.OnStatusChecked listener);

    AndroidAudioManager getAudioManager(Context context);

    /**
     * Returns a new instance of {@link Conversation}
     */
    Conversation create(Context context);

    /**
     * Shuts all internal components down and resets resources.
     */
    void shutdown();

    /**
     * Convenient provider method to return an instance of {@link ConversationalFlow}
     */
    static ConversationalFlow provide(ILogger logger) {
        logger.i(TAG, "------ Init %s", TAG);
        logger.setBuildDebug(BuildConfig.DEBUG);
        ConversationalFlow component = ConversationalFlowImpl.Instance.get();
        ((ConversationalFlowImpl)component).setLogger(logger);
        return component;
    }

    static boolean isStatus(int code, int status) {
        return (code > 0) && (code & status) == status;
    }

    void shutdown(Runnable onBluetoothScoDisconnected);

    void showVolumeControls();
}
