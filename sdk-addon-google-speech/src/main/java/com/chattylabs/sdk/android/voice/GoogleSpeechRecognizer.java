package com.chattylabs.sdk.android.voice;

import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.speech.SpeechRecognizer;
import android.support.annotation.RawRes;
import android.text.TextUtils;

import com.chattylabs.sdk.android.common.Tag;
import com.chattylabs.sdk.android.common.internal.ILogger;
import com.chattylabs.sdk.android.voice.ConversationalFlowComponent.RecognizerListener;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.speech.v1.RecognitionAudio;
import com.google.cloud.speech.v1.RecognitionConfig;
import com.google.cloud.speech.v1.SpeechClient;
import com.google.cloud.speech.v1.SpeechRecognitionResult;
import com.google.cloud.speech.v1.SpeechSettings;
import com.google.cloud.speech.v1.RecognizeRequest;
import com.google.cloud.speech.v1.RecognizeResponse;
import com.google.cloud.speech.v1.stub.SpeechStubSettings;
import com.google.protobuf.ByteString;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.selectMostConfidentResult;

public class GoogleSpeechRecognizer implements ConversationalFlowComponent.SpeechRecognizer {
    private static final String TAG = Tag.make("GoogleSpeechRecognizer");

    private final ReentrantLock lock = new ReentrantLock();

    // Resources
    private final Application application;
    private final ComponentConfig config;
    private final AndroidAudioHandler audioHandler;
    private final BluetoothSco bluetoothSco;
    private final ExecutorService executorService;

    // Log stuff
    private ILogger logger;

    private AudioRecorder mAudioRecorder;
    private SpeechClient speech;

    private final AudioRecorder.Callback mVoiceCallback = new AudioRecorder.Callback() {

        @Override
        public void onVoiceStart() {
            if (speech != null) {
                recognitionListener.onReadyForSpeech(null);
            }
        }

        @Override
        public void onVoice(byte[] data, int size) {
            if (speech != null) {
                RecognitionConfig.AudioEncoding encoding =
                        RecognitionConfig.AudioEncoding.FLAC;
                RecognitionConfig recognitionConfig = RecognitionConfig.newBuilder()
                        .setEncoding(encoding)
                        .setSampleRateHertz(mAudioRecorder.getSampleRate())
                        .setLanguageCode(getDefaultLanguageCode())
                        .build();
                RecognitionAudio audio = RecognitionAudio.newBuilder()
                        .setContent(ByteString.copyFrom(data, 0, size))
                        .build();
                RecognizeRequest request = RecognizeRequest.newBuilder()
                        .setConfig(recognitionConfig)
                        .setAudio(audio)
                        .build();
                RecognizeResponse response = speech.recognize(request);
                if (response.getResultsCount() > 0) {
                    SpeechRecognitionResult result = response.getResults(0);
                    if (result.getAlternativesCount() > 0) {
                        String text = result.getAlternatives(0).getTranscript();

//                        if (!TextUtils.isEmpty(text)) {
//                            Bundle bundle = new Bundle();
//                            ArrayList<String> textList = new ArrayList<>();
//                            textList.add(text);
//                            bundle.putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, textList);
//                            bundle.putFloatArray(SpeechRecognizer.CONFIDENCE_SCORES, new float[]{1});
//                            if (isFinal) {
//                                mVoiceCallback.onVoiceEnd();
//                                recognitionListener.onResults(bundle);
//                            } else {
//                                recognitionListener.onPartialResults(bundle);
//                            }
//                        }
                    }
                }
            }
        }

        @Override
        public void onVoiceError(int error) {
            if (speech != null) {
                recognitionListener.onError(error);
            }
        }

        @Override
        public void onVoiceEnd() {
            if (speech != null) {
                stopVoiceRecorder();
                recognitionListener.onEndOfSpeech();
            }
        }

    };

    // Listener
    private final GoogleSpeechRecognitionAdapter recognitionListener = new GoogleSpeechRecognitionAdapter() {
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
                    executorService.submit(() -> {
                        lock.lock();
                        try {
                            //TODO mVoiceCallback.onVoiceEnd();
                            lock.unlock();
                        } catch (Exception e) {
                            logger.logException(e);
                        } finally {
                            lock.unlock();
                        }
                    });
                }
            };
            timeout.schedule(task, RecognizerListener.MIN_VOICE_RECOGNITION_TIME_LISTENING * 3);
        }

        private void cleanup() {
            elapsedTime = System.currentTimeMillis();
            intents = 0;
            logger.v(TAG, "GOOGLE VOICE - cleanup elapsedTime & partial intents");
        }

        @Override
        public void reset() {
            releaseTimeout();
            audioHandler.abandonAudioFocus();
            cleanup();
            //super.setTryAgain(false);
            setOnError(null);
            setOnPartialResults(null);
            setOnResults(null);
            setOnMostConfidentResult(null);
            setOnReady(null);
            //setSoundLevel(UNKNOWN);
        }

        @Override
        public void onReadyForSpeech(Bundle params) {
            if (!bluetoothSco.isBluetoothScoOn()) {
                audioHandler.requestAudioFocus(config.isAudioExclusiveRequiredForRecognizer());
            }
            //beep();
            super.onReadyForSpeech(params);
        }

        @Override
        public void onError(int error) {
            //logger.e(TAG, "ANDROID VOICE - error: " + getErrorType(error));
            // We consider 2 sec as timeout for non speech
            boolean stoppedTooEarly = (System.currentTimeMillis() - elapsedTime) < RecognizerListener.MIN_VOICE_RECOGNITION_TIME_LISTENING;
            // Start checking for the error
            ConversationalFlowComponent.OnRecognizerError errorListener = getOnError();
            //int soundLevel = getSoundLevel();
            //logger.v(TAG, "ANDROID VOICE - Sound Level: " + getSoundLevelAsString(soundLevel));
            // Restart the recognizer
            cancel();
            if (errorListener != null) {
//                if (needRetry(error)) {
//                    errorListener.execute(RECOGNIZER_UNAVAILABLE_ERROR, error);
//                }
//                else
                    if (stoppedTooEarly) {
                    errorListener.execute(RecognizerListener.RECOGNIZER_STOPPED_TOO_EARLY_ERROR, error);
                }
//                else if (soundLevel == NO_SOUND) {
//                    errorListener.execute(RECOGNIZER_NO_SOUND_ERROR, error);
//                }
//                else if (soundLevel == LOW_SOUND) {
//                    errorListener.execute(RECOGNIZER_LOW_SOUND_ERROR, error);
//                }
                else if (intents > 0) {
                    errorListener.execute(RecognizerListener.RECOGNIZER_AFTER_PARTIALS_ERROR, error);
                }
//                else if (isTryAgain()) {
//                    errorListener.execute(error == SpeechRecognizer.ERROR_NO_MATCH ?
//                            RECOGNIZER_UNKNOWN_ERROR :
//                            RECOGNIZER_RETRY_ERROR, error);
//                }
                else { // Restore ANDROID VOICE
                    errorListener.execute(RecognizerListener.RECOGNIZER_UNKNOWN_ERROR, error);
                }
            }
        }

        @Override
        public void onResults(Bundle results) {
            releaseTimeout();
            ConversationalFlowComponent.OnRecognizerResults resultsListener = getOnResults();
            ConversationalFlowComponent.OnRecognizerMostConfidentResult mostConfidentResult = getOnMostConfidentResult();
            if (resultsListener == null && mostConfidentResult == null) return;
            List<String> textResults = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            float[] confidences = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES);
            if (textResults != null && (textResults.size() > 1 || (!textResults.isEmpty() && textResults.get(0).length() > 0))) {
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
                ConversationalFlowComponent.OnRecognizerError listener = getOnError();
                reset();
                if (listener != null) listener.execute(RecognizerListener.RECOGNIZER_EMPTY_RESULTS_ERROR, -1);
            }
        }

        @Override
        public void onPartialResults(Bundle partialResults) {
            releaseTimeout();
            intents++;
            ConversationalFlowComponent.OnRecognizerPartialResults listener = getOnPartialResults();
            if (listener == null) return;
            List<String> textResults = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            float[] confidences = partialResults.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES);
            if (textResults != null && (textResults.size() > 1 || (!textResults.isEmpty() && textResults.get(0).length() > 0))) {
                logger.v(TAG, "GOOGLE VOICE - partial results: " + textResults);
                listener.execute(textResults, confidences);
            }
        }
    };

    GoogleSpeechRecognizer(Application application,
                           ComponentConfig configuration,
                           AndroidAudioHandler audioHandler,
                           BluetoothSco bluetoothSco, ILogger logger) {
        this.application = application;
        this.config = configuration;
        this.audioHandler = audioHandler;
        this.bluetoothSco = bluetoothSco;
        this.logger = logger;
        this.executorService = Executors.newSingleThreadExecutor();
    }

    @Override
    public <T extends RecognizerListener> void listen(
             T... listeners) {
        logger.i(TAG, "GOOGLE VOICE - start listening");
        handleListeners(listeners);
        checkForBluetoothScoRequired(this::startListening);
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

    private void checkForBluetoothScoRequired(Runnable starter) {
        logger.i(TAG, "GOOGLE VOICE - is bluetooth Sco required: " +
                Boolean.toString(config.isBluetoothScoRequired()));
        if (config.isBluetoothScoRequired() && !bluetoothSco.isBluetoothScoOn()) {
            // Sco Listener
            BluetoothScoListener listener = new BluetoothScoListener() {
                @Override
                public void onConnected() {
                    logger.w(TAG, "GOOGLE VOICE - Sco onConnected");
                    starter.run();
                }

                @Override
                public void onDisconnected() {
                    logger.w(TAG, "GOOGLE VOICE - Sco onDisconnected");
                    if (bluetoothSco.isBluetoothScoOn()) {
                        logger.w(TAG, "GOOGLE VOICE - shutdown from Sco");
                        shutdown();
                    }
                }
            };
            // Start Bluetooth Sco
            bluetoothSco.startSco(listener);
            logger.v(TAG, "GOOGLE VOICE - waiting for bluetooth sco connection");
        }
        else {
            logger.v(TAG, "GOOGLE VOICE - bluetooth sco is: " + (bluetoothSco.isBluetoothScoOn() ? "on" : "off"));
            starter.run();
        }
    }

    private void startListening() {
        executorService.submit(() -> {
            lock.lock();
            try {
                recognitionListener.startTimeout();

//                    recognitionListener.setRmsDebug(rmsDebug);
//                    if (noSoundThreshold > 0) recognitionListener.setNoSoundThreshold(noSoundThreshold);
//                    if (lowSoundThreshold > 0) recognitionListener.setLowSoundThreshold(lowSoundThreshold);
//                    logger.i(TAG, "VOICE - start listening");
//                    speechRecognizer.setRecognitionListener(recognitionListener);

                if (this.speech == null) {
                    try (SpeechClient speechClient = generateFromRawFile(
                            application,
                            config.getGoogleCredentialsResourceFile())) {
                        this.speech = speechClient;
                    }
                }
                startVoiceRecorder();
                lock.unlock();
            } catch (Exception e) {
                logger.logException(e);
            } finally {
                lock.unlock();
            }
        });
    }

    private SpeechClient generateFromRawFile(Context context, @RawRes int rawResourceId) throws IOException {
        final InputStream stream = context.getResources().openRawResource(rawResourceId);
        final GoogleCredentials credentials = ServiceAccountCredentials.fromStream(stream)
                .createScoped(SpeechStubSettings.getDefaultServiceScopes());
        return SpeechClient.create(SpeechSettings.newBuilder()
                .setCredentialsProvider(
                        FixedCredentialsProvider.create(credentials)
                ).build());
    }

    private void handleListeners(RecognizerListener... listeners) {
        recognitionListener.reset();
        if (listeners != null && listeners.length > 0) {
            for (RecognizerListener item : listeners) {
                if (item instanceof ConversationalFlowComponent.OnRecognizerReady) {
                    recognitionListener.setOnReady((ConversationalFlowComponent.OnRecognizerReady) item);
                }
                else if (item instanceof ConversationalFlowComponent.OnRecognizerResults) {
                    recognitionListener.setOnResults((ConversationalFlowComponent.OnRecognizerResults) item);
                }
                else if (item instanceof ConversationalFlowComponent.OnRecognizerMostConfidentResult) {
                    recognitionListener.setOnMostConfidentResult((ConversationalFlowComponent.OnRecognizerMostConfidentResult) item);
                }
                else if (item instanceof ConversationalFlowComponent.OnRecognizerPartialResults) {
                    recognitionListener.setOnPartialResults((ConversationalFlowComponent.OnRecognizerPartialResults) item);
                }
                else if (item instanceof ConversationalFlowComponent.OnRecognizerError) {
                    recognitionListener.setOnError((ConversationalFlowComponent.OnRecognizerError) item);
                }
            }
        }
    }

    private void startVoiceRecorder() {
        if (mAudioRecorder != null) {
            mAudioRecorder.stop();
        }
        mAudioRecorder = new AudioRecorder(mVoiceCallback);
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
        // Stop listening to voice
        stopVoiceRecorder();
        if (speech != null) {
            try {
                speech.close();
            } catch (Exception e) {
                logger.logException(e);
            }
        }
    }

    @Override
    public void shutdown() {
        stop();
        // Stop Cloud Speech API
        if (speech != null) {
            speech.shutdown();
            try {
                speech.awaitTermination(2000, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                logger.logException(e);
            }
        }
        speech = null;
    }

    @Override
    public void release() {
    }

    @Override
    public void cancel() {

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
}
