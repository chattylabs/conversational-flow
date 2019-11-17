package chattylabs.conversations;

import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.speech.SpeechRecognizer;
import android.text.TextUtils;

import androidx.annotation.Keep;
import androidx.annotation.RawRes;

import com.chattylabs.android.commons.Tag;
import com.chattylabs.android.commons.internal.ILogger;
import com.google.api.gax.core.FixedExecutorProvider;
import com.google.api.gax.rpc.ClientStream;
import com.google.api.gax.rpc.ResponseObserver;
import com.google.api.gax.rpc.StreamController;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.speech.v1.RecognitionConfig;
import com.google.cloud.speech.v1.SpeechClient;
import com.google.cloud.speech.v1.SpeechRecognitionAlternative;
import com.google.cloud.speech.v1.SpeechSettings;
import com.google.cloud.speech.v1.StreamingRecognitionConfig;
import com.google.cloud.speech.v1.StreamingRecognitionResult;
import com.google.cloud.speech.v1.StreamingRecognizeRequest;
import com.google.cloud.speech.v1.StreamingRecognizeResponse;
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
import java.util.concurrent.atomic.AtomicBoolean;

import chattylabs.conversations.RecognizerListener.Status;
import kotlin.Unit;

import static chattylabs.conversations.ConversationalFlow.selectMostConfidentResult;
import static chattylabs.conversations.RecognizerListener.OnError;
import static chattylabs.conversations.RecognizerListener.OnMostConfidentResult;
import static chattylabs.conversations.RecognizerListener.OnPartialResults;
import static chattylabs.conversations.RecognizerListener.OnPrepared;
import static chattylabs.conversations.RecognizerListener.OnResults;
import static chattylabs.conversations.RecognizerListener.OnStatusChecked;

public final class GoogleSpeechRecognizer extends BaseSpeechRecognizer {
    private static final String TAG = Tag.make("GoogleSpeechRecognizer");

    // States
    private boolean rmsDebug; // released
    private float noSoundThreshold; // released
    private float lowSoundThreshold; // released

    // Resources
    private final Application application;
    private final AudioEmitter audioEmitter;
    private GoogleSpeechRecognitionAdapter listener;
    private SpeechClient stt;
    private RecognitionConfig config;
    private ClientStream<StreamingRecognizeRequest> requestClientStream;
    private boolean hasResults;
    private Timer timer;
    private long currentTime;

    @Keep
    public GoogleSpeechRecognizer(Application application,
                                  ComponentConfig configuration,
                                  AndroidAudioManager audioManager,
                                  AndroidBluetooth bluetooth,
                                  ILogger logger) {
        super(configuration, audioManager, bluetooth, logger, TAG);
        this.application = application;
        this.audioEmitter = new AudioEmitter();
    }

    private boolean isSttNull() {
        return stt == null;
    }

    private void prepare(OnPrepared onPrepared) {
        if (isSttNull() || stt.isShutdown() || stt.isTerminated()) {
            RecognitionConfig.AudioEncoding encoding =
                    RecognitionConfig.AudioEncoding.LINEAR16;
            config = RecognitionConfig.newBuilder()
                    //.setMaxAlternatives(1)
                    //.setProfanityFilter(true)
                    .setEnableAutomaticPunctuation(true)
                    .setEncoding(encoding)
                    .setSampleRateHertz(audioEmitter.getSampleRate())
                    .setLanguageCode(getDefaultLanguageCode())
                    .build();
            try {
                this.stt = generateFromRawFile(
                        application, getConfiguration().getGoogleCredentialsResourceFile());
                onPrepared.execute(Status.AVAILABLE);
            } catch (Exception e) {
                logger.logException(e);
                shutdown();
                onPrepared.execute(Status.UNAVAILABLE_ERROR);
            }
        } else if (!stt.isShutdown()) {
            onPrepared.execute(Status.AVAILABLE);
        }
    }

    @Override
    public void checkStatus(OnStatusChecked onStatusChecked) {
        onStatusChecked.execute(Status.AVAILABLE);
    }

    private final ResponseObserver<StreamingRecognizeResponse> observer =
            new ResponseObserver<StreamingRecognizeResponse>() {

                @Override
                public void onStart(StreamController controller) {
                }

                @Override
                public void onResponse(StreamingRecognizeResponse response) {
                    logger.d(TAG, "response [%d]", response.getResultsCount());
                    if (response.getResultsCount() > 0 && requestClientStream != null) {
                        currentTime = System.currentTimeMillis();
                        hasResults = true;
                        StreamingRecognitionResult result = response.getResults(0);
                        SpeechRecognitionAlternative alternative = result.getAlternatives(0);
                        Bundle bundle = new Bundle();
                        ArrayList<String> textList = new ArrayList<>();
                        textList.add(alternative.getTranscript());
                        bundle.putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, textList);
                        bundle.putFloatArray(SpeechRecognizer.CONFIDENCE_SCORES, new float[]{
                                alternative.getConfidence()
                        });
                        if (result.getIsFinal()) {
                            getRecognitionListener().onResults(bundle);
                        } else {
                            getRecognitionListener().onPartialResults(bundle);
                        }
                    }
                }

                @Override
                public void onError(Throwable t) {
                    logger.logException(t);
                    getRecognitionListener().onError(Status.UNKNOWN_ERROR);
                }

                @Override
                public void onComplete() {
                }
            };

    @Override
    GoogleSpeechRecognitionAdapter getRecognitionListener() {
        if (listener == null) {
            listener = new GoogleSpeechRecognitionAdapter() {
                private int intents;
                private long elapsedTime;

                private void cleanup() {
                    logger.v(TAG, "reset elapsedTime and intents");
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
                }

                @Override
                public void onReadyForSpeech(Bundle params) {
                    super.onReadyForSpeech(params);
                    getAudioManager().startBeep(application);
                }

                @Override
                public void onError(int error) {
                    logger.e(TAG, "error: %s", GoogleSpeechRecognizer.getErrorType(error));
                    onEndOfSpeech();
                    getAudioManager().errorBeep(application);
                    OnError errorListener = _getOnError();
                    // We consider 2 sec as the minimum to record audio
                    // If it last less than that, there was an audio issue
                    // So potentially we retry listening again
                    boolean stoppedTooEarly = (System.currentTimeMillis() - elapsedTime)
                            < MIN_VOICE_RECOGNITION_TIME_LISTENING;
                    cancel();
                    if (errorListener != null) {
                        if (needRetry(error)) {
                            errorListener.execute(Status.UNAVAILABLE_ERROR, error);
                        } else if (stoppedTooEarly) {
                            errorListener.execute(Status.STOPPED_TOO_EARLY_ERROR, error);
                        } else if (intents > 0) {
                            errorListener.execute(Status.AFTER_PARTIALS_ERROR, error);
                        } else if (tryAgainRequired()) {
                            errorListener.execute(error == SpeechRecognizer.ERROR_NO_MATCH ?
                                    Status.UNKNOWN_ERROR :
                                    Status.RETRY_ERROR, error);
                        } else { // Restore ANDROID VOICE
                            errorListener.execute(Status.UNKNOWN_ERROR, error);
                        }
                    }
                }

                @Override
                public void onResults(Bundle results) {
                    logger.v(TAG, "onResults");
                    onEndOfSpeech();
                    getAudioManager().successBeep(application);
                    List<String> list;
                    OnResults onResults = _getOnResults();
                    OnMostConfidentResult onMostConfidentResult = _getOnMostConfidentResult();
                    // if none of both are setup, we can't continue
                    if (onResults == null && onMostConfidentResult == null) return;
                    list = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    float[] confidences = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES);
                    if (list != null && !list.isEmpty()) {
                        stop();
                        if (onResults != null) {
                            logger.v(TAG, "results: %s", list);
                            onResults.execute(list, confidences);
                        }
                        if (onMostConfidentResult != null) {
                            String result = selectMostConfidentResult(list, confidences);
                            logger.v(TAG, "confident result: %s", result);
                            onMostConfidentResult.execute(result);
                        }
                    } else {
                        logger.e(TAG, "No results");
                        OnError onError = _getOnError();
                        stop();
                        if (onError != null) {
                            onError.execute(Status.EMPTY_RESULTS_ERROR, -1);
                        }
                    }
                }

                @Override
                public void onPartialResults(Bundle results) {
                    logger.v(TAG, "onPartialResults");
                    List<String> list;
                    intents++;
                    OnPartialResults onPartialResults = _getOnPartialResults();
                    // if this is not setup, we can't continue
                    if (onPartialResults == null) return;
                    list = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    float[] confidences = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES);
                    if (list != null && !list.isEmpty()) {
                        logger.v(TAG, "partial results: %s", list);
                        onPartialResults.execute(list, confidences);
                    }
                }

                private boolean needRetry(int error) {
                    switch (error) {
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
        logger.i(TAG, "started listening");

        AtomicBoolean isFirstRequest = new AtomicBoolean(true);

        prepare(recognizerStatus -> {
            if (recognizerStatus == Status.AVAILABLE) {

                getRecognitionListener().onReadyForSpeech(null);
                startElapsedTime();

                audioEmitter.start((buffer, size) -> {
                    StreamingRecognizeRequest.Builder builder = StreamingRecognizeRequest.newBuilder()
                            .setAudioContent(ByteString.copyFrom(buffer, 0, size));

                    if (requestClientStream == null)
                        requestClientStream = stt.streamingRecognizeCallable().splitCall(observer);

                    // if first time, include the config
                    if (isFirstRequest.getAndSet(false)) {
                        StreamingRecognitionConfig configBuilder = StreamingRecognitionConfig.newBuilder()
                                .setConfig(config)
                                .setInterimResults(true)
                                .setSingleUtterance(false)
                                .build();
                        builder.setStreamingConfig(configBuilder);
                    }

                    requestClientStream.send(builder.build());

                    return Unit.INSTANCE;
                });

            } else {
                logger.e(TAG, "internal ERROR");
                getRecognitionListener().onError(recognizerStatus);
            }
        });
    }

    private void startElapsedTime() {
        currentTime = System.currentTimeMillis();
        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override public void run() {
                if (System.currentTimeMillis() - currentTime >
                        TimeUnit.SECONDS.toMillis(hasResults ?
                                (getConfiguration().isSpeechDictation() ? 5 : 2) : 3)) {
                    if (hasResults) {
                        getRecognitionListener().onEndOfSpeech();
                        stop();
                    } else
                        getRecognitionListener().onError(Status.EMPTY_RESULTS_ERROR);
                }
            }
        }, 0, TimeUnit.SECONDS.toMillis(1));
    }

    private void stopElapsedTime() {
        if (timer != null) timer.cancel();
    }

    private SpeechClient generateFromRawFile(Context context,
                                             @RawRes int rawResourceId) throws IOException {
        final InputStream stream = context.getResources().openRawResource(rawResourceId);
        return SpeechClient.create(SpeechSettings.newBuilder()
                .setExecutorProvider(FixedExecutorProvider.create(Executors.newScheduledThreadPool(
                        Math.max(2, Math.min(Runtime.getRuntime().availableProcessors() - 1, 4))
                )))
                .setCredentialsProvider(() -> GoogleCredentials.fromStream(stream)).build());
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

    @Override
    public void stop() {
        logger.w(TAG, "stop");
        // Cancel timeout
        stopElapsedTime();
        // Stop recording
        if (audioEmitter != null) audioEmitter.stop();
        // Close Streaming
        if (requestClientStream != null && requestClientStream.isSendReady()) {
            requestClientStream.closeSend();
        }
        // Stop Cloud Speech API
        if (stt != null && !stt.isShutdown()) {
            stt.close();
            stt.shutdown();
        }
        hasResults = false;
        stt = null;
        requestClientStream = null;
        super.stop();
    }

    public void cancel() {
        logger.w(TAG, "cancel");
        stop();
    }

    public void shutdown() {
        logger.w(TAG, "shutdown");
        cancel();
    }

    private String getDefaultLanguageCode() {
        Locale speechLanguage = getConfiguration().getSpeechLanguage();
        final Locale locale = speechLanguage != null ? speechLanguage : Locale.getDefault();
        final StringBuilder language = new StringBuilder(locale.getLanguage());
        final String country = locale.getCountry();
        if (!TextUtils.isEmpty(country)) {
            language.append("-");
            language.append(country);
        }
        return language.toString();
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
