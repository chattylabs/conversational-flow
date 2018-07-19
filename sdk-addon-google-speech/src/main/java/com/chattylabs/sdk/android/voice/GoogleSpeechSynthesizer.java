package com.chattylabs.sdk.android.voice;

import android.app.Application;
import android.media.MediaPlayer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import com.chattylabs.sdk.android.common.Tag;
import com.chattylabs.sdk.android.common.internal.ILogger;
import com.google.cloud.texttospeech.v1beta1.AudioConfig;
import com.google.cloud.texttospeech.v1beta1.AudioEncoding;
import com.google.cloud.texttospeech.v1beta1.SsmlVoiceGender;
import com.google.cloud.texttospeech.v1beta1.TextToSpeechClient;
import com.google.cloud.texttospeech.v1beta1.VoiceSelectionParams;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.*;
import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.DEFAULT_QUEUE_ID;
import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.SYNTHESIZER_AVAILABLE;
import static com.chattylabs.sdk.android.voice.ConversationalFlowComponent.SYNTHESIZER_NOT_AVAILABLE_ERROR;

public class GoogleSpeechSynthesizer implements SpeechSynthesizer {
    private static final String TAG = Tag.make("GoogleSpeechSynthesizer");

    // Resources
    private final Application application;
    private TextToSpeechClient tts;
    private VoiceSelectionParams voice;
    private AudioConfig audioConfig;
    private MediaPlayer mediaPlayer;

    // Log stuff
    private ILogger logger;

    GoogleSpeechSynthesizer(Application application,
                            VoiceConfig configuration,
                            AndroidAudioHandler audioHandler,
                            MediaPlayer mediaPlayer,
                            BluetoothSco bluetoothSco, ILogger logger) {
        this.application = application;
        this.listenersMap = new LinkedHashMap<>();
        this.queue = new LinkedHashMap<>();
        this.filters = new LinkedList<>();
        this.configuration = configuration;
        this.audioHandler = audioHandler;
        this.bluetoothSco = bluetoothSco;
        this.mediaPlayer = mediaPlayer;
        this.logger = logger;
        //this.release();
    }

    @Override
    public void setup(OnSynthesizerInitialised onSynthesizerInitialised) {
        try (TextToSpeechClient ttsClient = TextToSpeechClient.create()) {
            this.tts = ttsClient;
            this.audioConfig = AudioConfig.newBuilder().setAudioEncoding(AudioEncoding.MP3).build();
            onSynthesizerInitialised.execute(SYNTHESIZER_AVAILABLE);
        } catch (Exception e) {
            shutdown();
            onSynthesizerInitialised.execute(SYNTHESIZER_NOT_AVAILABLE_ERROR);
        }
    }

    private void initTts(TextToSpeech.OnInitListener onInitListener,
                         OnSynthesizerInitialised onCheckLanguageInit) {
        if (tts == null) {
            isReady = false;
            utteranceListener = initUtterancesListener(onCheckLanguageInit);
            // Audio !
            //tts.setOnUtteranceProgressListener(utteranceListener);
            logger.i(TAG, "GOOGLE TTS - creating new instance");
            try (TextToSpeechClient ttsClient = TextToSpeechClient.create()) {
                logger.i(TAG, "TTS - new instance created");
                isReady = true;
                this.tts = ttsClient;
                this.voice = VoiceSelectionParams.newBuilder()
                        .setLanguageCode(getDefaultLanguageCode())
                        .setSsmlGender(SsmlVoiceGender.NEUTRAL)
                        .build();
                this.audioConfig = AudioConfig.newBuilder().setAudioEncoding(AudioEncoding.MP3).build();
                onInitListener.onInit(status);
            } catch (Exception e) {
                shutdown();
                onSynthesizerInitialised.execute(SYNTHESIZER_NOT_AVAILABLE_ERROR);
            }
        }
        else if (isReady) {
            onInitListener.onInit(TextToSpeech.SUCCESS);
        }
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

    private void handleListener(@NonNull String utteranceId, @NonNull GoogleSpeechSynthesizerAdapter listener) {
        logger.v(TAG, "TTS - added utterance - " + utteranceId +
                " - listener -> size:  " + listenersMap.size());
        synchronized (lock) {
            listenersMap.put(utteranceId, listener);
        }
    }

    private void addToQueueSet(@NonNull String utteranceId, String message, long duration, @NonNull String queueId) {
        Map<String, Object> map = new HashMap<>();
        map.put(MAP_UTTERANCE_ID, utteranceId);
        if (message != null) map.put(MAP_MESSAGE, message);
        if (duration > 0) map.put(MAP_SILENCE, duration);
        if (!queue.containsKey(queueId)) {
            logger.v(TAG, "TTS - added queue: <" + queueId + "> - " + utteranceId);
            lastQueueId = queueId;
            synchronized (lock) {
                queue.put(queueId, new ConcurrentLinkedQueue<>());
            }
        }
        queue.get(queueId).add(map);
        logger.v(TAG, "TTS - added message to queue <" + queueId + ">. Number of queues: " + queue.size());
        logger.v(TAG, "TTS - messages in the queue <" + queueId + ">: " + queue.get(queueId).size());
    }

    private GoogleSpeechSynthesizerAdapter generateUtteranceListener(@NonNull SynthesizerListenerContract... listeners) {
        GoogleSpeechSynthesizerAdapter listener = new GoogleSpeechSynthesizerAdapter()
        {
            @Override
            public void onStart(String utteranceId) {
                getOnStartedListener().execute(utteranceId);
            }

            @Override
            public void onDone(String utteranceId) {
                getOnDoneListener().execute(utteranceId);
            }

            @Override
            public void onError(String utteranceId, int errorCode) {
                getOnErrorListener().execute(utteranceId, errorCode);
            }
        };
        if (listeners.length > 0) {
            for (SynthesizerListenerContract item : listeners) {
                if (item instanceof OnSynthesizerStart) {
                    listener.setOnStartedListener((OnSynthesizerStart) item);
                }
                if (item instanceof OnSynthesizerDone) {
                    listener.setOnDoneListener((OnSynthesizerDone) item);
                }
                if (item instanceof OnSynthesizerError) {
                    listener.setOnErrorListener((OnSynthesizerError) item);
                }
            }
        }
        return listener;
    }

    @Override
    public void addFilter(TextFilter filter) {

    }

    @Override
    public <T extends SynthesizerListenerContract> void playText(String text, String queueId, T... listeners) {
        playText(text, queueId, generateUtteranceListener(listeners));
    }

    private void playText(String text, String queueId, GoogleSpeechSynthesizerAdapter listener) {
        playText(text, queueId, listener, DEFAULT_UTTERANCE_ID + System.nanoTime());
    }

    private void playText(String text, String queueId, @Nullable GoogleSpeechSynthesizerAdapter listener, String utteranceId) {
        logger.i(TAG, "TTS - prepare to playText \"" + text + "\" with Queue: <" + queueId + "> - " + utteranceId);
        if (listenersMap.containsKey(utteranceId)) {
            utteranceId = utteranceId + "_" + System.currentTimeMillis();
        }
        final String uId = utteranceId;
        if (listener != null) handleListener(uId, listener);
        addToQueueSet(uId, text, -1, queueId);
        logger.i(TAG, "TTS - ready: " + Boolean.toString(isReady) +
                " | speaking: " + Boolean.toString(isSpeaking) +
                " | held: " + Boolean.toString(isOnHold) + " - " + utteranceId);
        if (tts == null) {
            initTts(status -> {
                if (status == TextToSpeech.SUCCESS) {
                    isReady = true;
                    resume();
                }
                else {
                    logger.e(TAG, "TTS - with queue status ERROR");
                    if (listenersMap.containsKey(uId)) {
                        GoogleSpeechSynthesizerAdapter progressListener;
                        synchronized (lock) {
                            progressListener = listenersMap.remove(uId);
                        }
                        progressListener.onError(uId, TextToSpeech.ERROR);
                    }
                }
            }, null);
        }
        else if (isReady && !isSpeaking && !isOnHold) {
            resume();
        }
    }

    @Override
    public <T extends SynthesizerListenerContract> void playText(String text, T... listeners) {
        String utteranceId = DEFAULT_UTTERANCE_ID + System.nanoTime();
        logger.i(TAG, "TTS - prepare to immediately playText \"" + text + "\" - " + utteranceId);
        GoogleSpeechSynthesizerAdapter listener = generateUtteranceListener(listeners);
        if (listenersMap.containsKey(utteranceId)) {
            utteranceId = utteranceId + "_" + listenersMap.size();
        }
        handleListener(utteranceId, listener);
        Map<String, Object> map = new HashMap<>();
        map.put(MAP_UTTERANCE_ID, utteranceId);
        map.put(MAP_MESSAGE, text);
        logger.i(TAG, "TTS - ready: " + Boolean.toString(isReady) +
                " | speaking: " + Boolean.toString(isSpeaking) + " - " + utteranceId);
        initTts(status -> {
            if (status == TextToSpeech.SUCCESS) {
                playTheCurrentQueue(map);
            }
            else {
                logger.e(TAG, "TTS - no queue status ERROR");
                shutdown();
            }
        }, null);
    }

    @Override
    public <T extends SynthesizerListenerContract> void playSilence(long durationInMillis, String queueId, T... listeners) {

    }

    @Override
    public <T extends SynthesizerListenerContract> void playSilence(long durationInMillis, T... listeners) {

    }

    @Override
    public void stop() {

    }

    @Override
    public void shutdown() {

    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public boolean isCurrentQueueEmpty() {
        return false;
    }

    @Override
    public String getLastQueueId() {
        return null;
    }

    @Nullable
    @Override
    public String getNextQueueId() {
        return null;
    }

    @Override
    public String getCurrentQueueId() {
        return null;
    }

    @Override
    public Set<String> getQueueSet() {
        return null;
    }
}
