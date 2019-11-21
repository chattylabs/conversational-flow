package chattylabs.conversations;

import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import androidx.annotation.Keep;

import java.util.List;

import chattylabs.android.commons.Tag;
import chattylabs.android.commons.internal.ILogger;
import chattylabs.android.commons.internal.os.AndroidHandler;
import chattylabs.android.commons.internal.os.AndroidHandlerImpl;

import static chattylabs.conversations.ConversationalFlow.selectMostConfidentResult;
import static chattylabs.conversations.RecognizerListener.OnError;
import static chattylabs.conversations.RecognizerListener.OnMostConfidentResult;
import static chattylabs.conversations.RecognizerListener.OnPartialResults;
import static chattylabs.conversations.RecognizerListener.OnResults;
import static chattylabs.conversations.RecognizerListener.OnStatusChecked;
import static chattylabs.conversations.RecognizerListener.Status;

public final class AndroidSpeechRecognizer extends BaseSpeechRecognizer {
    private static final String TAG = Tag.make("AndroidSpeechRecognizer");

    // States
    private boolean rmsDebug; // released
    private float noSoundThreshold; // released
    private float lowSoundThreshold; // released

    // Resources
    private final Application application;
    private final AndroidHandler mainHandler;
    private final Intent speechRecognizerIntent;
    private final Creator<SpeechRecognizer> recognizerCreator;
    private AndroidSpeechRecognitionAdapter listener;
    private SpeechRecognizer speechRecognizer;

    @Keep
    public AndroidSpeechRecognizer(Application application,
                            ComponentConfig configuration,
                            AndroidAudioManager audioManager,
                            AndroidBluetooth bluetooth,
                            ILogger logger) {
        super(configuration, audioManager, bluetooth, logger, TAG);
        release();
        this.application = application;
        this.mainHandler = new AndroidHandlerImpl(Looper.getMainLooper());
        this.speechRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        this.speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        this.speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE,
                application.getPackageName());
        this.speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        this.recognizerCreator = () -> SpeechRecognizer.createSpeechRecognizer(application);
    }

    @Override
    public void checkStatus(OnStatusChecked listener) {
        int androidRecognizerStatus =
                android.speech.SpeechRecognizer.isRecognitionAvailable(application) ?
                Status.AVAILABLE : Status.NOT_AVAILABLE;
        listener.execute(androidRecognizerStatus);
    }

    @Override
    AndroidSpeechRecognitionAdapter getRecognitionListener() {
        if (listener == null) {
            listener = new AndroidSpeechRecognitionAdapter() {
                private int intents;
                private long elapsedTime;

                private void cleanup() {
                    logger.v(TAG, "ANDROID SPEECH - reset elapsedTime and intents");
                    elapsedTime = System.currentTimeMillis();
                    intents = 0;
                }

                @Override
                public void reset() {
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
                    super.onReadyForSpeech(params);
                    if (getConfiguration().isCustomBeepEnabled())
                        getAudioManager().startBeep(application);
                }

                @Override
                public void onError(int error) {
                    onEndOfSpeech();
                    if (getConfiguration().isCustomBeepEnabled())
                        getAudioManager().errorBeep(application);
                    logger.e(TAG, "ANDROID SPEECH - error: %s", AndroidSpeechRecognizer.getErrorType(error));
                    OnError errorListener = _getOnError();
                    int soundLevel = getSoundLevel();
                    logger.v(TAG, "ANDROID SPEECH - sound level: %s", getSoundLevelAsString(soundLevel));
                    // We consider 2 sec as the minimum to record audio
                    // If it last less than that, there was an audio issue
                    // So potentially we retry listening again
                    boolean stoppedTooEarly = (System.currentTimeMillis() - elapsedTime) < MIN_VOICE_RECOGNITION_TIME_LISTENING;
                    getRecognitionListener().reset();
                    mainHandler.post(() -> {
                        release();
                        if (errorListener != null) {
                            if (needRetry(error)) {
                                errorListener.execute(Status.UNAVAILABLE_ERROR, error);
                            }
                            else if (stoppedTooEarly) {
                                errorListener.execute(Status.STOPPED_TOO_EARLY_ERROR, error);
                            }
                            else if (soundLevel == NO_SOUND) {
                                errorListener.execute(Status.NO_SOUND_ERROR, error);
                            }
                            else if (soundLevel == LOW_SOUND) {
                                errorListener.execute(Status.LOW_SOUND_ERROR, error);
                            }
                            else if (intents > 0) {
                                errorListener.execute(Status.AFTER_PARTIALS_ERROR, error);
                            }
                            else if (this.tryAgainRequired()) {
                                errorListener.execute(error == SpeechRecognizer.ERROR_NO_MATCH ?
                                        Status.UNKNOWN_ERROR :
                                        Status.RETRY_ERROR, error);
                            }
                            else { // Restore ANDROID SPEECH
                                errorListener.execute(Status.UNKNOWN_ERROR, error);
                            }
                        }
                    });
                }

                @Override
                public void onResults(Bundle results) {
                    onEndOfSpeech();
                    if (getConfiguration().isCustomBeepEnabled())
                        getAudioManager().successBeep(application);
                    List<String> list;
                    OnError onError = _getOnError();
                    OnResults onResults = _getOnResults();
                    OnMostConfidentResult onMostConfidentResult = _getOnMostConfidentResult();
                    // if none of both are setup, we can't continue
                    if (onResults == null && onMostConfidentResult == null) return;
                    list = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    float[] confidences = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES);
                    getRecognitionListener().reset();
                    mainHandler.post(() -> {
                        if (list != null && !list.isEmpty()) {
                            if (onResults != null) {
                                logger.v(TAG, "ANDROID SPEECH - results: %s", list);
                                onResults.execute(list, confidences);
                            }
                            if (onMostConfidentResult != null) {
                                String result = selectMostConfidentResult(list, confidences);
                                logger.v(TAG, "ANDROID SPEECH - confident result: %s", result);
                                onMostConfidentResult.execute(result);
                            }
                        }
                        else {
                            logger.e(TAG, "ANDROID SPEECH - No results > onError");
                            if (onError != null) {
                                onError.execute(Status.EMPTY_RESULTS_ERROR, -1);
                            }
                        }
                    });
                }

                @Override
                public void onPartialResults(Bundle partialResults) {
                    logger.v(TAG, "ANDROID SPEECH - onPartialResults");
                    List<String> list;
                    intents++;
                    OnPartialResults onPartialResults = _getOnPartialResults();
                    // if this is not setup, we can't continue
                    if (onPartialResults == null) return;
                    list = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    float[] confidences = partialResults.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES);
                    if (list != null && !list.isEmpty()) {
                        logger.v(TAG, "ANDROID SPEECH - partial results: %s", list);
                        onPartialResults.execute(list, confidences);
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
        }
        return listener;
    }

    @Override
    void startListening() {
        logger.i(TAG, "ANDROID SPEECH - started listening");
        if (mainHandler != null)  {
            mainHandler.removeCallbacksAndMessages(null);
            mainHandler.post(() -> {
                speechRecognizer = recognizerCreator.create();
                getRecognitionListener().setRmsDebug(rmsDebug);
                if (noSoundThreshold > 0)
                    getRecognitionListener().setNoSoundThreshold(noSoundThreshold);
                if (lowSoundThreshold > 0)
                    getRecognitionListener().setLowSoundThreshold(lowSoundThreshold);
                speechRecognizer.setRecognitionListener(getRecognitionListener());
                //adjustVolumeForBeep();
                speechRecognizer.startListening(speechRecognizerIntent);
            });
        }
    }

    @Override
    public void setRmsDebug(boolean rmsDebug) {
        this.rmsDebug = rmsDebug;
    }

    @Override
    public void setNoSoundThreshold(float maxValue) {
        this.noSoundThreshold = maxValue;
    }

    @Override
    public void setLowSoundThreshold(float maxValue) {
        this.lowSoundThreshold = maxValue;
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

    private void release() {
        speechRecognizer = null;
        setRmsDebug(false);
        setNoSoundThreshold(0);
        setLowSoundThreshold(0);
        logger.i(TAG, "ANDROID SPEECH - released");
    }

    @Override
    public void stop() {
        logger.w(TAG, "ANDROID SPEECH - stop");
        if (mainHandler != null) mainHandler.removeCallbacksAndMessages(null);
        getRecognitionListener().reset();
        super.stop();
        if (speechRecognizer != null) {
            speechRecognizer.setRecognitionListener(null);
            if (mainHandler != null) mainHandler.post(() -> {
                try {
                    speechRecognizer.stopListening();
                    speechRecognizer.destroy();
                    logger.v(TAG, "ANDROID SPEECH - speechRecognizer stopped");
                } catch (Exception ignored) {}
                release();
            });
        } else {
            release();
        }
    }
}
