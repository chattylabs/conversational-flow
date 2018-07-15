package com.chattylabs.sdk.android.voice.addon.google;

import android.app.Application;

import com.chattylabs.sdk.android.common.Tag;
import com.chattylabs.sdk.android.common.internal.ILogger;
import com.chattylabs.sdk.android.voice.AndroidAudioHandler;
import com.chattylabs.sdk.android.voice.BluetoothSco;
import com.chattylabs.sdk.android.voice.BluetoothScoListener;
import com.chattylabs.sdk.android.voice.VoiceConfig;
import com.chattylabs.sdk.android.voice.VoiceInteractionComponent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GoogleSpeechRecognizer implements VoiceInteractionComponent.SpeechRecognizer {
    private static final String TAG = Tag.make("GoogleSpeechRecognizer");

    // Resources
    private final Application application;
    private final VoiceConfig config;
    private final AndroidAudioHandler audioHandler;
    private final BluetoothSco bluetoothSco;
    private final ExecutorService executorService;

    // Log stuff
    private ILogger logger;

    // Listener
    private final GoogleSpeechRecognitionAdapter recognitionListener = new GoogleSpeechRecognitionAdapter() {

    };

    public GoogleSpeechRecognizer(Application application,
                                  VoiceConfig config,
                                  AndroidAudioHandler audioHandler,
                                  BluetoothSco bluetoothSco, ILogger logger) {
        this.application = application;
        this.config = config;
        this.audioHandler = audioHandler;
        this.bluetoothSco = bluetoothSco;
        this.logger = logger;
        this.executorService = Executors.newSingleThreadExecutor();
    }

    @Override
    public <T extends VoiceInteractionComponent.RecognizerListenerContract> void listen(
             T... listeners) {
        logger.i(TAG, "GOOGLE VOICE - start conversation");
        handleListeners(listeners);
        // Check whether Sco is connected or required
        logger.i(TAG, "GOOGLE VOICE - is bluetooth Sco required: " +
                Boolean.toString(config.isBluetoothScoRequired()));
        if (config.isBluetoothScoRequired() && !bluetoothSco.isBluetoothScoOn()) {
            // Sco Listener
            BluetoothScoListener listener = new BluetoothScoListener() {
                @Override
                public void onConnected() {
                    logger.w(TAG, "GOOGLE VOICE - Sco onConnected");
                    startListening();
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
            startListening();
        }
    }

    private void startListening() {
        executorService.submit(() -> {
            lock.lock();
            try {
                mainHandler.post(() -> {
                    if (speechRecognizer == null) {
                        speechRecognizer = recognizerCreator.create();
                        logger.v(TAG, "VOICE - created");
                    }
                    recognitionListener.startTimeout();
                    recognitionListener.setRmsDebug(rmsDebug);
                    if (noSoundThreshold > 0) recognitionListener.setNoSoundThreshold(noSoundThreshold);
                    if (lowSoundThreshold > 0) recognitionListener.setLowSoundThreshold(lowSoundThreshold);
                    logger.i(TAG, "VOICE - start listening");
                    speechRecognizer.setRecognitionListener(recognitionListener);
                    //adjustVolumeForBeep();
                    speechRecognizer.startListening(speechRecognizerIntent);
                    executorService.submit(lock::unlock);
                });
            } catch (Exception e) {
                lock.unlock();
            }
        });
    }

    private void handleListeners(VoiceInteractionComponent.RecognizerListenerContract... listeners) {
        recognitionListener.reset();
        if (listeners != null && listeners.length > 0) {
            for (VoiceInteractionComponent.RecognizerListenerContract item : listeners) {
                if (item instanceof VoiceInteractionComponent.OnRecognizerReady) {
                    recognitionListener.setOnReady((VoiceInteractionComponent.OnRecognizerReady) item);
                }
                else if (item instanceof VoiceInteractionComponent.OnRecognizerResults) {
                    recognitionListener.setOnResults((VoiceInteractionComponent.OnRecognizerResults) item);
                }
                else if (item instanceof VoiceInteractionComponent.OnRecognizerMostConfidentResult) {
                    recognitionListener.setOnMostConfidentResult((VoiceInteractionComponent.OnRecognizerMostConfidentResult) item);
                }
                else if (item instanceof VoiceInteractionComponent.OnRecognizerPartialResults) {
                    recognitionListener.setOnPartialResults((VoiceInteractionComponent.OnRecognizerPartialResults) item);
                }
                else if (item instanceof VoiceInteractionComponent.OnRecognizerError) {
                    recognitionListener.setOnError((VoiceInteractionComponent.OnRecognizerError) item);
                }
            }
        }
    }

    @Override
    public void stop() {

    }

    @Override
    public void shutdown() {

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
