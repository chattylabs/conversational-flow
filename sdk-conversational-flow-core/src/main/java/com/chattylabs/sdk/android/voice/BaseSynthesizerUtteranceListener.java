package com.chattylabs.sdk.android.voice;

import android.support.annotation.IntDef;
import android.support.annotation.NonNull;

import com.chattylabs.sdk.android.common.internal.ILogger;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

abstract class BaseSynthesizerUtteranceListener implements SynthesizerUtteranceListener {

    private static final int MAX_SPEECH_TIME_SEC = 60;

    protected final ILogger logger;
    @Mode
    final int mode;
    private final BaseSpeechSynthesizer speechSynthesizer;

    private SynthesizerListener.OnStart onStartedListener;
    private SynthesizerListener.OnDone onDoneListener;
    private SynthesizerListener.OnError onErrorListener;
    private long timestamp;
    private TimerTask task;
    private Timer timer;

    BaseSynthesizerUtteranceListener(@NonNull final BaseSpeechSynthesizer speechSynthesizer,
                                     @Mode int mode) {
        this.speechSynthesizer = speechSynthesizer;
        this.logger = speechSynthesizer.logger;
        this.mode = mode;
    }

    BaseSynthesizerUtteranceListener(@NonNull final BaseSpeechSynthesizer speechSynthesizer) {
        this(speechSynthesizer, Mode.INTIALIZE);
    }

    abstract String getTtsLogLabel();

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
        if (this.mode == Mode.INTIALIZE) {
            _getOnStartedListener().execute(utteranceId);
            return;
        }

        logger.v(getTag(), "%s[%s] - on start", getTtsLogLabel(), utteranceId);

        startTimeout(utteranceId);
        timestamp = System.currentTimeMillis();

        if (getListenersMap().size() > 0) {
            SynthesizerUtteranceListener listener = getListenersMap().get(utteranceId);
            if (listener != null) {
                listener.onStart(utteranceId);
            }
        }

    }

    @Override
    public void onDone(String utteranceId) {
        if (this.mode == Mode.INTIALIZE) {
            _getOnDoneListener().execute(utteranceId);
            return;
        }

        clearTimeout(utteranceId);
        logger.v(getTag(), "%s[%s] - on done <%s> - check for Empty Queue", getTtsLogLabel(), utteranceId, getCurrentQueueId());
        moveToNextQueueIfNeeded();
        if (isEmpty()) {
            stop();
            logger.i(getTag(), "%s[%s] - on done <%s> - Stream Finished", getTtsLogLabel(), utteranceId, getCurrentQueueId());
        }
        setSpeaking(false);
        if (getListenersMap().size() > 0) {
            SynthesizerUtteranceListener listener = removeListener(utteranceId);
            logger.v(getTag(), "%s[%s] - on done <%s> - execute listener.onDone", getTtsLogLabel(), utteranceId, getCurrentQueueId());
            if (listener != null) {
                listener.onDone(utteranceId);
            }
        }
    }

    @Override
    public void onError(String utteranceId, int errorCode) {
        if (this.mode == Mode.INTIALIZE) {
            _getOnErrorListener().execute(utteranceId, errorCode);
            return;
        }

        clearTimeout(utteranceId);
        logger.e(getTag(), "%s[%s] - on error <%s> -> stop timeout", getTtsLogLabel(), utteranceId, getCurrentQueueId());
        logger.e(getTag(), "%s[%s] - error code: %s", getTtsLogLabel(), utteranceId, getErrorType(errorCode));
        moveToNextQueueIfNeeded();
        if (isEmpty()) {
            stop();
            logger.i(getTag(), "%s[%s] - ERROR <%s> - Stream Finished", getTtsLogLabel(), utteranceId, getCurrentQueueId());
        }
        setSpeaking(false);
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
        logger.v(getTag(), "%s[%s] - utterance timeout cleared", getTtsLogLabel(), utteranceId);
        if (task != null) task.cancel();
        if (timer != null) timer.cancel();
    }

    @Override
    public void startTimeout(String utteranceId) {
        logger.i(getTag(), "%s[%s] - started timeout", getTtsLogLabel(), utteranceId);
        timer = new Timer();
        task = new TimerTask() {
            @Override
            public void run() {
                if (!isTtsSpeaking()) {
                    logger.e(getTag(), "%s[%s] - is null or not speaking && reached timeout", getTtsLogLabel(), utteranceId);
                    speechSynthesizer.stop();
                    onError(utteranceId, SynthesizerListener.Status.TIMEOUT);
                } else {
                    if ((System.currentTimeMillis() - timestamp) > TimeUnit.SECONDS.toMillis(MAX_SPEECH_TIME_SEC)) {
                        logger.e(getTag(), "%s[%s] - exceeded %s seconds", getTtsLogLabel(), utteranceId, MAX_SPEECH_TIME_SEC);
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

    protected String getTag() {
        return speechSynthesizer.getTag();
    }

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

    private void setSpeaking(boolean speaking) {
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

    @IntDef({Mode.INTIALIZE, Mode.DELEGATE})
    @Retention(RetentionPolicy.SOURCE)
    @interface Mode {
        int INTIALIZE = 0;
        int DELEGATE = 1;
    }
}
