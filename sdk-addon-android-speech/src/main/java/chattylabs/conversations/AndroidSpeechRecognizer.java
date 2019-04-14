package chattylabs.conversations;

import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import androidx.annotation.Nullable;

import com.chattylabs.android.commons.Tag;
import com.chattylabs.android.commons.ThreadUtils;
import com.chattylabs.android.commons.internal.ILogger;
import com.chattylabs.android.commons.internal.os.AndroidHandler;
import com.chattylabs.android.commons.internal.os.AndroidHandlerImpl;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.ReentrantLock;

import static chattylabs.conversations.ConversationalFlow.selectMostConfidentResult;

public final class AndroidSpeechRecognizer extends BaseSpeechRecognizer {
    private static final String TAG = Tag.make("AndroidSpeechRecognizer");

    private final ReentrantLock lock = new ReentrantLock();

    // States
    private boolean rmsDebug; // released
    private float noSoundThreshold; // released
    private float lowSoundThreshold; // released

    // Resources
    private final Application application;
    private final AndroidHandler mainHandler;
    private final Intent speechRecognizerIntent;
    private final Creator<SpeechRecognizer> recognizerCreator;
    private @Nullable ThreadUtils.SerialThread serialThread;
    private SpeechRecognizer speechRecognizer;

    public AndroidSpeechRecognizer(Application application,
                            ComponentConfig configuration,
                            AndroidAudioManager audioManager,
                            BluetoothSco bluetoothSco,
                            ILogger logger) {
        super(configuration, audioManager, bluetoothSco, logger);
        this.application = application;
        this.release();
        this.mainHandler = new AndroidHandlerImpl(Looper.getMainLooper());
        this.speechRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        this.speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        this.speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, application.getPackageName());
        this.speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        this.speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L);
        this.recognizerCreator = () -> SpeechRecognizer.createSpeechRecognizer(application);
    }

    @Override
    public void checkStatus(RecognizerListener.OnStatusChecked listener) {
        int androidRecognizerStatus = android.speech.SpeechRecognizer.isRecognitionAvailable(application) ?
                RecognizerListener.Status.AVAILABLE : RecognizerListener.Status.NOT_AVAILABLE;
        listener.execute(androidRecognizerStatus);
    }

    private final AndroidSpeechRecognitionAdapter listener = new AndroidSpeechRecognitionAdapter() {
        private int intents;
        private long elapsedTime;
        private Timer timeout;
        private TimerTask task;

        private void releaseTimeout() {
            if (timeout != null) {
                logger.w(TAG, "ANDROID VOICE - releasing previous timeout");
                task.cancel();
                timeout.cancel();
                timeout = null;
                task = null;
            }
        }

        @Override
        public void startTimeout() {
            releaseTimeout();
            logger.w(TAG, "ANDROID VOICE - started timeout");
            timeout = new Timer();
            task = new TimerTask() {
                @Override
                public void run() {
                    logger.w(TAG, "ANDROID VOICE - reached timeout");
                    if (serialThread != null)
                        serialThread.addTask(() -> {
                            printLock();
                            lock.lock();
                            mainHandler.post(() -> {
                                speechRecognizer.stopListening();
                                if (serialThread != null)
                                    serialThread.addTask(lock::unlock);
                                else if (lock.isLocked()) lock.unlock();
                            });
                        });
                    else if (lock.isLocked()) lock.unlock();
                }
            };
            timeout.schedule(task, MIN_VOICE_RECOGNITION_TIME_LISTENING * 3);
        }

        private void cleanup() {
            elapsedTime = System.currentTimeMillis();
            intents = 0;
            logger.v(TAG, "ANDROID VOICE - cleanup elapsedTime & partial intents");
        }

        @Override
        public void reset() {
            releaseTimeout();
            abandonAudioFocus();
            cleanup();
            super.setTryAgain(false);
            _setOnError(null);
            _setOnPartialResults(null);
            _setOnResults(null);
            _setOnMostConfidentResult(null);
            _setOnReady(null);
            setSoundLevel(UNKNOWN);
        }

        @Override
        public void onReadyForSpeech(Bundle params) {
            requestAudioFocus();
            //resetVolumeForBeep();
            super.onReadyForSpeech(params);
        }

        @Override
        public void onError(int error) {
            logger.e(TAG, "ANDROID VOICE - error: %s", AndroidSpeechRecognizer.getErrorType(error));
            // We consider 2 sec as the minimum to record audio
            // If it last less than that, there was an audio issue
            // So potentially we retry listening again
            boolean stoppedTooEarly = (System.currentTimeMillis() - elapsedTime) <
                    MIN_VOICE_RECOGNITION_TIME_LISTENING;
            RecognizerListener.OnError errorListener = _getOnError();
            int soundLevel = getSoundLevel();
            logger.v(TAG, "ANDROID VOICE - Sound Level: %s", getSoundLevelAsString(soundLevel));
            // Restart the recognizer
            cancel();
            if (errorListener != null) {
                if (needRetry(error)) {
                    errorListener.execute(RecognizerListener.Status.UNAVAILABLE_ERROR, error);
                }
                else if (stoppedTooEarly) {
                    errorListener.execute(RecognizerListener.Status.STOPPED_TOO_EARLY_ERROR, error);
                }
                else if (soundLevel == NO_SOUND) {
                    errorListener.execute(RecognizerListener.Status.NO_SOUND_ERROR, error);
                }
                else if (soundLevel == LOW_SOUND) {
                    errorListener.execute(RecognizerListener.Status.LOW_SOUND_ERROR, error);
                }
                else if (intents > 0) {
                    errorListener.execute(RecognizerListener.Status.AFTER_PARTIALS_ERROR, error);
                }
                else if (this.isTryAgain()) {
                    errorListener.execute(error == SpeechRecognizer.ERROR_NO_MATCH ?
                            RecognizerListener.Status.UNKNOWN_ERROR :
                            RecognizerListener.Status.RETRY_ERROR, error);
                }
                else { // Restore ANDROID VOICE
                    errorListener.execute(RecognizerListener.Status.UNKNOWN_ERROR, error);
                }
            }
        }

        @Override
        public void onResults(Bundle results) {
            releaseTimeout();
            RecognizerListener.OnResults resultsListener = _getOnResults();
            RecognizerListener.OnMostConfidentResult mostConfidentResult = _getOnMostConfidentResult();
            if (resultsListener == null && mostConfidentResult == null) return;
            List<String> textResults = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            float[] confidences = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES);
            if (textResults != null && (textResults.size() > 1 ||
                    (!textResults.isEmpty() && textResults.get(0).length() > 0))) {
                reset();
                if (resultsListener != null) {
                    logger.v(TAG, "ANDROID VOICE - results: " + textResults);
                    resultsListener.execute(textResults, confidences);
                }
                if (mostConfidentResult != null) {
                    String result = selectMostConfidentResult(textResults, confidences);
                    logger.v(TAG, "ANDROID VOICE - confident result: " + result);
                    mostConfidentResult.execute(result);
                }
            }
            else {
                logger.e(TAG, "ANDROID VOICE - NO results");
                RecognizerListener.OnError listener = _getOnError();
                reset();
                if (listener != null) listener.execute(RecognizerListener.Status.EMPTY_RESULTS_ERROR, -1);
            }
        }

        @Override
        public void onPartialResults(Bundle partialResults) {
            logger.v(TAG, "ANDROID VOICE - onPartialResults");
            releaseTimeout();
            intents++;
            startTimeout();
            RecognizerListener.OnPartialResults listener = _getOnPartialResults();
            if (listener == null) return;
            List<String> textResults = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            float[] confidences = partialResults.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES);
            if (textResults != null && (textResults.size() > 1 ||
                    (!textResults.isEmpty() && textResults.get(0).length() > 0))) {
                logger.v(TAG, "ANDROID VOICE - partial results: %s", textResults);
                listener.execute(textResults, confidences);
            }
        }

        private boolean needRetry(int error) {
            switch (error) {
                case SpeechRecognizer.ERROR_AUDIO:
                case SpeechRecognizer.ERROR_NETWORK:
                case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                case SpeechRecognizer.ERROR_SERVER:
                    return true;
                default:
                    return false;
            }
        }
    };

    @Override
    AndroidSpeechRecognitionAdapter getRecognitionListener() {
        return listener;
    }

    @Override
    String getTag() {
        return TAG;
    }

    private void printLock() {
        logger.d(TAG, "isHeldByCurrentThread: %s, isLocked: %s, getHoldCount: %s",
                lock.isHeldByCurrentThread(), lock.isLocked(), lock.getHoldCount());
    }

    @Override
    void startListening() {
        logger.i(TAG, "ANDROID VOICE - started listening");
        if (serialThread == null) {
            serialThread = ThreadUtils.newSerialThread();
        }
        serialThread.addTask(() -> {
            printLock();
            lock.lock();
            mainHandler.post(() -> {
                speechRecognizer = recognizerCreator.create();
                getRecognitionListener().startTimeout();
                getRecognitionListener().setRmsDebug(rmsDebug);
                if (noSoundThreshold > 0) getRecognitionListener().setNoSoundThreshold(noSoundThreshold);
                if (lowSoundThreshold > 0) getRecognitionListener().setLowSoundThreshold(lowSoundThreshold);
                speechRecognizer.setRecognitionListener(getRecognitionListener());
                //adjustVolumeForBeep();
                speechRecognizer.startListening(speechRecognizerIntent);
                if (serialThread != null)
                    serialThread.addTask(lock::unlock);
                else if (lock.isLocked()) lock.unlock();
            });
        });
    }

    public void setRmsDebug(boolean rmsDebug) {
        this.rmsDebug = rmsDebug;
    }

    public void setNoSoundThreshold(float maxValue) {
        this.noSoundThreshold = maxValue;
    }

    public void setLowSoundThreshold(float maxValue) {
        this.lowSoundThreshold = maxValue;
    }

    @Override
    public void stop() {
        logger.w(TAG, "ANDROID VOICE - do stop");
        super.stop();
        if (serialThread != null)
            serialThread.addTask(() -> {
                printLock();
                lock.lock();
                mainHandler.post(() -> {
                    if (speechRecognizer != null) {
                        speechRecognizer.stopListening();
                        logger.v(TAG, "ANDROID VOICE - speechRecognizer stopped");
                    }
                    if (serialThread != null)
                        serialThread.addTask(lock::unlock);
                });
            });
        else if (lock.isLocked()) lock.unlock();
    }

    @Override
    public void cancel() {
        logger.w(TAG, "ANDROID VOICE - do cancel");
        super.cancel();
        if (speechRecognizer != null) {
            speechRecognizer.setRecognitionListener(null);
            if (serialThread != null)
                serialThread.addTask(() -> {
                    printLock();
                    lock.lock();
                    mainHandler.post(() -> {
                        try {
                            if (speechRecognizer != null) {
                                speechRecognizer.cancel();
                                logger.v(TAG, "ANDROID VOICE - speechRecognizer canceled");
                            }
                        }
                        catch (Exception ignore) {}
                        if (serialThread != null)
                            serialThread.addTask(lock::unlock);
                        else if (lock.isLocked()) lock.unlock();
                    });
                });
            else if (lock.isLocked()) lock.unlock();
        }
    }

    @Override
    public void shutdown() {
        logger.w(TAG, "ANDROID VOICE - shutting down");
        super.shutdown();
        if (speechRecognizer != null) {
            mainHandler.post(() -> {
                speechRecognizer.setRecognitionListener(null);
                try {
                    if (speechRecognizer != null) {
                        speechRecognizer.destroy();
                        speechRecognizer = null;
                        logger.v(TAG, "ANDROID VOICE - speechRecognizer destroyed");
                    }
                } catch (Exception ignore) {
                } finally {
                    // Release and reset all resources & lock
                    release();
                }
            });
        } else {
            // Release and reset all resources & lock
            release();
        }
    }

    @Override
    public void release() {
        super.release();
        if (mainHandler != null)
            mainHandler.removeCallbacksAndMessages(null);
        if (serialThread != null) {
            serialThread.addTask(lock::unlock);
            serialThread.shutdown();
            serialThread = null;
        } else if (lock.isLocked()) lock.unlock();
        setRmsDebug(false);
        setNoSoundThreshold(0);
        setLowSoundThreshold(0);
        logger.i(TAG, "ANDROID VOICE - released");
    }

    public static String getErrorType(int error) {
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
}
