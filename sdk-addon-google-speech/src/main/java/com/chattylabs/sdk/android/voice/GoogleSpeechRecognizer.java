package com.chattylabs.sdk.android.voice;

import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.os.ConditionVariable;
import android.speech.SpeechRecognizer;
import android.support.annotation.RawRes;
import android.text.TextUtils;

import com.chattylabs.sdk.android.common.Tag;
import com.chattylabs.sdk.android.common.ThreadUtils;
import com.chattylabs.sdk.android.common.internal.ILogger;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.core.FixedExecutorProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.speech.v1p1beta1.RecognitionAudio;
import com.google.cloud.speech.v1p1beta1.RecognitionConfig;
import com.google.cloud.speech.v1p1beta1.RecognizeResponse;
import com.google.cloud.speech.v1p1beta1.SpeechClient;
import com.google.cloud.speech.v1p1beta1.SpeechRecognitionResult;
import com.google.cloud.speech.v1p1beta1.SpeechSettings;
import com.google.cloud.speech.v1p1beta1.stub.SpeechStubSettings;
import com.google.protobuf.ByteString;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.selectMostConfidentResult;

public final class GoogleSpeechRecognizer extends BaseSpeechRecognizer {
    private static final String TAG = Tag.make("GoogleSpeechRecognizer");

    private static final int TERMINATION_TIMEOUT = 2000;

    private final ReentrantLock lock = new ReentrantLock();

    private final ConditionVariable mCondVar = new ConditionVariable();
    // States

    // Resources
    private final Application application;
    private ThreadUtils.SerialThread serialThread;
    private AndroidAudioRecorder mAudioRecorder;
    private SpeechClient speech;
    private Bundle lastResult;

    GoogleSpeechRecognizer(Application application,
                           ComponentConfig configuration,
                           AndroidAudioManager audioManager,
                           BluetoothSco bluetoothSco,
                           ILogger logger) {
        super(configuration, audioManager, bluetoothSco, logger);
        this.application = application;
        this.release();
        this.serialThread = ThreadUtils.newSerialThread();
    }

    private final AndroidAudioRecorder.Callback mVoiceCallback = new AndroidAudioRecorder.Callback() {

        @Override
        public void onVoiceStart() {
            getRecognitionListener().onReadyForSpeech(null);
        }

        @Override
        public void onVoice(byte[] data, int size) {

            

            RecognitionConfig.AudioEncoding encoding =
                    RecognitionConfig.AudioEncoding.LINEAR16;
            RecognitionConfig config = RecognitionConfig.newBuilder()
                    //.setMaxAlternatives(1)
                    //.setProfanityFilter(true)
                    .setEncoding(encoding)
                    .setSampleRateHertz(mAudioRecorder.getSampleRate())
                    .setLanguageCode("en-US")
                    .build();

            RecognitionAudio audio = RecognitionAudio.newBuilder()
                    .setContent(ByteString.copyFrom(data, 0, size))
                    .build();

            try (SpeechClient speech = generateFromRawFile(
                    application, getConfiguration().getGoogleCredentialsResourceFile())) {
                RecognizeResponse response = speech.recognize(config, audio);
                if (response.getResultsCount() > 0 || response.getResultsList().size() > 0) {
                    SpeechRecognitionResult result = response.getResults(0);
                    if (result.getAlternativesCount() > 0) {
                        String text = result.getAlternatives(0).getTranscript();
                        if (!TextUtils.isEmpty(text)) {
                            Bundle bundle = new Bundle();
                            ArrayList<String> textList = new ArrayList<>();
                            textList.add(text);
                            bundle.putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, textList);
                            bundle.putFloatArray(SpeechRecognizer.CONFIDENCE_SCORES, new float[]{1});
                            getRecognitionListener().onPartialResults(bundle);
                        }
                    }
                }
            } catch (Exception e) {}
        }

        @Override
        public void onVoiceError(int error) {
            getRecognitionListener().onError(error);
        }

        @Override
        public void onVoiceEnd() {
            stop();
            if (lastResult != null) getRecognitionListener().onResults(lastResult);
            getRecognitionListener().onEndOfSpeech();
        }

    };

    private final GoogleSpeechRecognitionAdapter listener = new GoogleSpeechRecognitionAdapter() {
        private int intents;
        private long elapsedTime;
        private Timer timeout;
        private TimerTask task;

        private void releaseTimeout() {
            if (timeout != null) {
                logger.w(TAG, "GOOGLE VOICE - releasing previous timeout");
                task.cancel();
                timeout.cancel();
                timeout = null;
                task = null;
            }
        }

        @Override
        public void startTimeout() {
            releaseTimeout();
            logger.w(TAG, "GOOGLE VOICE - started timeout");
            timeout = new Timer();
            task = new TimerTask() {
                @Override
                public void run() {
                    logger.w(TAG, "GOOGLE VOICE - reached timeout");
                    serialThread.addTask(() -> {
                        printLock();
                        lock.lock();
                        try {
                            //mVoiceCallback.onVoiceEnd();
                        } catch (Exception e) {
                            logger.logException(e);
                        } finally {
                            lock.unlock();
                        }
                    });
                }
            };
            timeout.schedule(task, MIN_VOICE_RECOGNITION_TIME_LISTENING * 3);
        }

        private void cleanup() {
            elapsedTime = System.currentTimeMillis();
            intents = 0;
            logger.v(TAG, "GOOGLE VOICE - cleanup elapsedTime & partial intents");
        }

        @Override
        public void reset() {
            releaseTimeout();
            abandonAudioFocus();
            cleanup();
            _setOnError(null);
            _setOnPartialResults(null);
            _setOnResults(null);
            _setOnMostConfidentResult(null);
            _setOnReady(null);
        }

        @Override
        public void onReadyForSpeech(Bundle params) {
            requestAudioFocus();
            startBeep();
            super.onReadyForSpeech(params);
        }

        @Override
        public void onError(int error) {
            logger.e(TAG, "ANDROID VOICE - error: " + GoogleSpeechRecognizer.getErrorType(error));
            // We consider 2 sec as the minimum to record audio
            // If it last less than that, there was an audio issue
            // So potentially we retry listening again
            boolean stoppedTooEarly = (System.currentTimeMillis() - elapsedTime) < MIN_VOICE_RECOGNITION_TIME_LISTENING;
            RecognizerListener.OnError errorListener = _getOnError();
            cancel();
            if (errorListener != null) {
                if (needRetry(error)) {
                    errorListener.execute(RecognizerListener.Status.RECOGNIZER_UNAVAILABLE_ERROR, error);
                }
                else if (stoppedTooEarly) {
                    errorListener.execute(RecognizerListener.Status.RECOGNIZER_STOPPED_TOO_EARLY_ERROR, error);
                }
//                else if (soundLevel == NO_SOUND) {
//                    errorListener.execute(RECOGNIZER_NO_SOUND_ERROR, error);
//                }
//                else if (soundLevel == LOW_SOUND) {
//                    errorListener.execute(RECOGNIZER_LOW_SOUND_ERROR, error);
//                }
                else if (intents > 0) {
                    errorListener.execute(RecognizerListener.Status.RECOGNIZER_AFTER_PARTIALS_ERROR, error);
                }
                else if (isTryAgain()) {
                    errorListener.execute(error == SpeechRecognizer.ERROR_NO_MATCH ?
                            RecognizerListener.Status.RECOGNIZER_UNKNOWN_ERROR :
                            RecognizerListener.Status.RECOGNIZER_RETRY_ERROR, error);
                }
                else { // Restore ANDROID VOICE
                    errorListener.execute(RecognizerListener.Status.RECOGNIZER_UNKNOWN_ERROR, error);
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
                    logger.v(TAG, "GOOGLE VOICE - results: " + textResults);
                    resultsListener.execute(textResults, confidences);
                }
                if (mostConfidentResult != null) {
                    String result = selectMostConfidentResult(textResults, confidences);
                    logger.v(TAG, "GOOGLE VOICE - confident result: " + result);
                    mostConfidentResult.execute(result);
                }
            }
            else {
                logger.e(TAG, "GOOGLE VOICE - NO results");
                RecognizerListener.OnError listener = _getOnError();
                reset();
                if (listener != null) listener.execute(RecognizerListener.Status.RECOGNIZER_EMPTY_RESULTS_ERROR, -1);
            }
        }

        @Override
        public void onPartialResults(Bundle partialResults) {
            logger.v(TAG, "GOOGLE VOICE - onPartialResults");
            releaseTimeout();
            intents++;
            startTimeout(); lastResult = partialResults;
            RecognizerListener.OnPartialResults listener = _getOnPartialResults();
            if (listener == null) return;
            List<String> textResults = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            float[] confidences = partialResults.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES);
            if (textResults != null && (textResults.size() > 1 ||
                    (!textResults.isEmpty() && textResults.get(0).length() > 0))) {
                logger.v(TAG, "GOOGLE VOICE - partial results: " + textResults);
                listener.execute(textResults, confidences);
            }
        }

        private boolean needRetry(int error) {
            switch (error) {
                default:
                    return false;
            }
        }
    };

    @Override
    GoogleSpeechRecognitionAdapter getRecognitionListener() {
        return listener;
    }

    @Override
    String getTag() {
        return TAG;
    }

    private String getDefaultLanguageCode() {
        final Locale locale = Locale.getDefault();
        final StringBuilder language = new StringBuilder(locale.getLanguage());
        final String country = locale.getCountry();
        if (!TextUtils.isEmpty(country)) {
            language.append("-");
            language.append(country);
        }
        return language.toString();
    }

    private void printLock() {
        logger.d(TAG, "isHeldByCurrentThread: %s, isLocked: %s, getHoldCount: %s",
                lock.isHeldByCurrentThread(), lock.isLocked(), lock.getHoldCount());
    }

    @Override
    void startListening() {
        logger.i(TAG, "GOOGLE VOICE - started listening");
        if (serialThread == null) {
            serialThread = ThreadUtils.newSerialThread();
        }
        serialThread.addTask(() -> {
            printLock();
            lock.lock();
            getRecognitionListener().startTimeout();
            try {
//                if (this.speech == null) {
//                    this.speech = generateFromRawFile(
//                            application, getConfiguration().getGoogleCredentialsResourceFile());
//                }
                startVoiceRecorder();
            } catch (Exception e) {
                logger.logException(e);
                listener.onError(-1);
            } finally {
                lock.unlock();
            }
        });
    }

    private void startBeep() {

    }

    private SpeechClient generateFromRawFile(Context context, @RawRes int rawResourceId) throws IOException {
        final InputStream stream = context.getResources().openRawResource(rawResourceId);
        final GoogleCredentials credentials = ServiceAccountCredentials.fromStream(stream)
                .createScoped(SpeechStubSettings.getDefaultServiceScopes());
        return SpeechClient.create(SpeechSettings.newBuilder()
                .setExecutorProvider(
                        FixedExecutorProvider.create(
                                Executors.newScheduledThreadPool(
                                        Math.max(2, Math.min(Runtime.getRuntime().availableProcessors() - 1, 4))
                                )
                        )
                )
                .setCredentialsProvider(
                        FixedCredentialsProvider.create(credentials)
                ).build());
    }

    private void startVoiceRecorder() {
        stopVoiceRecorder();
        mAudioRecorder = new AndroidAudioRecorder(mVoiceCallback);
        mAudioRecorder.start();
    }

    private void stopVoiceRecorder() {
        if (mAudioRecorder != null) {
            mAudioRecorder.stop();
            mAudioRecorder = null;
        }
    }

    @Override
    public void stop() {
        super.stop();
        // Stop recording
        stopVoiceRecorder();
        serialThread.addTask(lock::unlock);
    }

    @Override
    public void cancel() {
        super.cancel();
        // Stop recording
        stopVoiceRecorder();
        // Stop Cloud Speech API
        if (speech != null) {
            speech.shutdownNow();
            try {
                speech.awaitTermination(TERMINATION_TIMEOUT, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                logger.logException(e);
            }
        }
        release();
    }

    @Override
    public void shutdown() {
        logger.w(TAG, "GOOGLE VOICE - shutting down");
        super.shutdown();
        // Stop recording
        stopVoiceRecorder();
        // Stop Cloud Speech API
        if (speech != null) {
            speech.shutdown();
            try {
                speech.awaitTermination(TERMINATION_TIMEOUT, TimeUnit.MILLISECONDS);
                logger.v(TAG, "GOOGLE VOICE - speechRecognizer destroyed");
            } catch (InterruptedException e) {
                logger.logException(e);
            }
        }
        release();
    }

    @Override
    public void release() {
        super.release();
        if (serialThread != null) {
            serialThread.addTask(lock::unlock);
        }
        if (speech != null) {
            speech.close();
            speech = null;
        }
    }

    @Override
    public void setRmsDebug(boolean debug) {

    }

    @Override
    public void setNoSoundThreshold(float maxValue) {

    }

    @Override
    public void setLowSoundThreshold(float maxValue) {

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
