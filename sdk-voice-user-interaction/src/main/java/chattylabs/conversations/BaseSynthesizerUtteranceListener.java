package chattylabs.conversations;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import chattylabs.android.commons.internal.ILogger;

class BaseSynthesizerUtteranceListener implements SynthesizerUtteranceListener {
    private final String TAG;

    private static final int MAX_SPEECH_TIME_SEC = 60;

    protected final ILogger logger;

    @Mode final int mode;

    private final BaseSpeechSynthesizer speechSynthesizer;

    private SynthesizerListener.OnStart onStartedListener;
    private SynthesizerListener.OnDone onDoneListener;
    private SynthesizerListener.OnError onErrorListener;
    private long timestamp;
    private TimerTask task;
    private Timer timer;

    BaseSynthesizerUtteranceListener(@NonNull final BaseSpeechSynthesizer speechSynthesizer,
                                     @Mode int mode, @NonNull String tag) {
        this.speechSynthesizer = speechSynthesizer;
        this.logger = speechSynthesizer.logger;
        this.mode = mode;
        this.TAG = tag;
    }

    BaseSynthesizerUtteranceListener(@NonNull final BaseSpeechSynthesizer speechSynthesizer,
                                     @NonNull String logLabel) {
        this(speechSynthesizer, Mode.CHECKING, logLabel);
    }

    @Override
    public SynthesizerListener.OnStart _getOnStartedListener() {
        return onStartedListener != null ? onStartedListener : item -> {
        };
    }

    @Override
    public SynthesizerUtteranceListener _setOnStartedListener(SynthesizerListener.OnStart onStartedListener) {
        this.onStartedListener = onStartedListener;
        return this;
    }

    @Override
    public SynthesizerListener.OnDone _getOnDoneListener() {
        return onDoneListener != null ? onDoneListener : item -> {
        };
    }

    @Override
    public SynthesizerUtteranceListener _setOnDoneListener(SynthesizerListener.OnDone onDoneListener) {
        this.onDoneListener = onDoneListener;
        return this;
    }

    @Override
    public SynthesizerListener.OnError _getOnErrorListener() {
        return onErrorListener != null ? onErrorListener : (item1, item2) -> {
        };
    }

    @Override
    public SynthesizerUtteranceListener _setOnErrorListener(SynthesizerListener.OnError onErrorListener) {
        this.onErrorListener = onErrorListener;
        return this;
    }

    @Override
    public void onStart(String utteranceId) {
        if (this.mode == Mode.DELEGATE) {
            _getOnStartedListener().execute(utteranceId);
            return;
        }

        logger.v(TAG, "[%s] - on start", utteranceId);

        timestamp = System.currentTimeMillis();
        //startTimeout(utteranceId);

        // On Mode CHECKING there shouldn't be any onStart event
        if (getListenersMap().size() > 0) {
            SynthesizerUtteranceListener listener = getListenersMap().get(utteranceId);
            if (listener != null) {
                listener.onStart(utteranceId);
            }
        }
    }

    @Override
    public void onDone(String utteranceId) {
        if (this.mode == Mode.DELEGATE) {
            setSpeaking(false);
            _getOnDoneListener().execute(utteranceId);
            return;
        }

        logger.v(TAG, "[%s] - on done <%s> - check for Empty Queue", utteranceId, getCurrentQueueId());
        moveToNextQueueIfNeeded();
//        if (isEmpty()) {
//            stop();
//            logger.i(TAG, "%s[%s] - on done <%s> - Stream Finished", utteranceId, getCurrentQueueId());
//        }
        if (getListenersMap().size() > 0) {
            SynthesizerUtteranceListener listener = removeListener(utteranceId);
            logger.v(TAG, "[%s] - on done <%s> - execute listener.onDone", utteranceId, getCurrentQueueId());
            if (listener != null) {
                listener.onDone(utteranceId);
            }
        }
    }

    @Override
    public void onError(String utteranceId, int errorCode) {
        setSpeaking(false);
        if (this.mode == Mode.DELEGATE) {
            _getOnErrorListener().execute(utteranceId, errorCode);
            return;
        }

        logger.e(TAG, "[%s] - on error <%s> -> stop timeout", utteranceId, getCurrentQueueId());
        logger.e(TAG, "[%s] - error code: %s", utteranceId, getErrorType(errorCode));
        moveToNextQueueIfNeeded();
//        if (isEmpty()) {
//            stop();
//            logger.i(TAG, "[%s] - ERROR <%s> - Stream Finished", utteranceId, getCurrentQueueId());
//        }
        if (getListenersMap().size() > 0 && getListenersMap().containsKey(utteranceId)) {
            SynthesizerUtteranceListener listener = removeListener(utteranceId);
            shutdown();
            if (listener != null) {
                listener.onError(utteranceId, errorCode);
            }
        } else {
            shutdown();
        }
    }

    @Override
    public void clearTimeout(String utteranceId) {
        logger.v(TAG, "[%s] - utterance timeout cleared", utteranceId);
        if (task != null) task.cancel();
        if (timer != null) timer.cancel();
    }

    @Override
    public void startTimeout(String utteranceId) {
        logger.i(TAG, "[%s] - started timeout", utteranceId);
        timer = new Timer();
        task = new TimerTask() {
            @Override
            public void run() {
                if (!isTtsSpeaking()) {
                    logger.e(TAG, "[%s] - is null or not speaking && reached timeout", utteranceId);
                    speechSynthesizer.stop();
                    onError(utteranceId, SynthesizerListener.Status.TIMEOUT);
                } else {
                    if ((System.currentTimeMillis() - timestamp) > TimeUnit.SECONDS.toMillis(MAX_SPEECH_TIME_SEC)) {
                        logger.e(TAG, "[%s] - exceeded %s seconds", utteranceId, MAX_SPEECH_TIME_SEC);
                        speechSynthesizer.stop();
                        onError(utteranceId, SynthesizerListener.Status.TIMEOUT);
                    } else {
                        clearTimeout(utteranceId);
                        startTimeout(utteranceId);
                    }
                }
            }
        };
        timer.schedule(task, TimeUnit.SECONDS.toMillis(10));
    }

    protected String getErrorType(int error) {
        return "Error Code: " + error;
    }

    /**
     * Util Methods For Speech Synthesizer
     **/

    private boolean isTtsSpeaking() {
        return speechSynthesizer.isTtsSpeaking();
    }

    private Map<String, SynthesizerUtteranceListener> getListenersMap() {
        return speechSynthesizer.getListenersMap();
    }

    String getCurrentQueueId() {
        return speechSynthesizer.getCurrentQueueId();
    }

    protected boolean isEmpty() {
        return speechSynthesizer.isEmpty();
    }

    protected void setSpeaking(boolean speaking) {
        speechSynthesizer.setSpeaking(speaking);
    }

    private SynthesizerUtteranceListener removeListener(String utteranceId) {
        return speechSynthesizer.removeListener(utteranceId);
    }

    private void moveToNextQueueIfNeeded() {
        speechSynthesizer.moveToNextQueueIfNeeded();
    }

    protected void stop() {
        speechSynthesizer.stop();
    }

    protected void shutdown() {
        speechSynthesizer.shutdown();
    }

    @IntDef({Mode.CHECKING, Mode.DELEGATE})
    @Retention(RetentionPolicy.SOURCE)
    @interface Mode {
        int CHECKING = 0;
        int DELEGATE = 1;
    }
}
