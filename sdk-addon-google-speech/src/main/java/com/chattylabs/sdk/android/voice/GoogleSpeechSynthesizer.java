package com.chattylabs.sdk.android.voice;

import android.app.Application;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import com.chattylabs.sdk.android.common.Tag;
import com.chattylabs.sdk.android.common.internal.ILogger;
import com.google.cloud.texttospeech.v1beta1.AudioConfig;
import com.google.cloud.texttospeech.v1beta1.AudioEncoding;
import com.google.cloud.texttospeech.v1beta1.SsmlVoiceGender;
import com.google.cloud.texttospeech.v1beta1.TextToSpeechClient;
import com.google.cloud.texttospeech.v1beta1.VoiceSelectionParams;

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

    // Constants
    private static final String CHECKING_UTTERANCE_ID = BuildConfig.APPLICATION_ID + ".checking";
    private static final String DEFAULT_UTTERANCE_ID = BuildConfig.APPLICATION_ID + ".utterance:";
    private static final String TESTING_STRING = "<TESTING_STRING>";
    private static final String MAP_UTTERANCE_ID = "utteranceId";
    private static final String MAP_SILENCE = "silence";
    private static final String MAP_MESSAGE = "message";
    private static final String MAP_PARAMS = "params";
    private static final int MAX_SPEECH_TIME = 60;

    // Data
    private final VoiceConfig configuration;
    private final Map<String, UtteranceProgressListener> listenersMap;
    private final Map<String, ConcurrentLinkedQueue<Map<String, Object>>> queue;
    private final List<TextFilter> filters;
    private final Object lock = new Object();

    // States
    private boolean isReady; // released
    private boolean isOnHold; // released
    private boolean isSpeaking; // released

    // Resources
    private String queueId = DEFAULT_QUEUE_ID; // released
    private String lastQueueId;
    private final Application application;
    private final AndroidAudioHandler audioHandler;
    private final BluetoothSco bluetoothSco;
    private TextToSpeechClient tts;
    private VoiceSelectionParams voice;
    private AudioConfig audioConfig;

    // Log stuff
    private ILogger logger;

    GoogleSpeechSynthesizer(Application application,
                            VoiceConfig configuration,
                            AndroidAudioHandler audioHandler,
                            BluetoothSco bluetoothSco, ILogger logger) {
        this.application = application;
        this.listenersMap = new LinkedHashMap<>();
        this.queue = new LinkedHashMap<>();
        this.filters = new LinkedList<>();
        this.configuration = configuration;
        this.audioHandler = audioHandler;
        this.bluetoothSco = bluetoothSco;
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

    @Override
    public void addFilter(TextFilter filter) {

    }

    @Override
    public <T extends SynthesizerListenerContract> void playText(String text, String queueId, T... listeners) {
        playText(text, queueId, generateUtteranceListener(listeners));
    }

    @Override
    public <T extends SynthesizerListenerContract> void playText(String text, T... listeners) {

    }

    @Override
    public <T extends SynthesizerListenerContract> void playSilence(long durationInMillis, String queueId, T... listeners) {

    }

    @Override
    public <T extends SynthesizerListenerContract> void playSilence(long durationInMillis, T... listeners) {

    }

    @Override
    public void releaseCurrentQueue() {

    }

    @Override
    public void holdCurrentQueue() {

    }

    @Override
    public void stop() {

    }

    @Override
    public void resume() {

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
