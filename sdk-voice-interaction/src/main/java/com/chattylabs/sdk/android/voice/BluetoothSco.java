package com.chattylabs.sdk.android.voice;

import android.app.Application;
import android.content.IntentFilter;
import android.media.AudioManager;

import com.chattylabs.sdk.android.common.Tag;
import com.chattylabs.sdk.android.common.internal.ILogger;

class BluetoothSco {
    private static final String TAG = Tag.make("BluetoothSco");

    // States
    private boolean isScoReceiverRegistered; // released
    private boolean isBluetoothScoOn;

    // Resources
    private Application application;
    private AudioManager audioManager;
    private BluetoothScoReceiver bluetoothScoReceiver = new BluetoothScoReceiver();

    // Log stuff
    private ILogger logger;

    public BluetoothSco(Application application, AudioManager audioManager, ILogger logger) {
        this.application = application;
        this.audioManager = audioManager;
        this.logger = logger;
    }

    private void registerReceiver(BluetoothScoListener bluetoothScoListener) {
        bluetoothScoReceiver.setListener(bluetoothScoListener);
        if (!isScoReceiverRegistered) {
            logger.v(TAG, "register sco receiver");
            IntentFilter scoFilter = new IntentFilter();
            scoFilter.addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED);
            application.registerReceiver(bluetoothScoReceiver, scoFilter);
            isScoReceiverRegistered = true;
        }
    }

    private void unregisterReceiver() {
        // Bluetooth Sco
        if (isScoReceiverRegistered) {
            logger.v(TAG, "unregister sco receiver");
            application.unregisterReceiver(bluetoothScoReceiver);
            isScoReceiverRegistered = false;
        }
    }

    void startSco(BluetoothScoListener bluetoothScoListener) {
        registerReceiver(bluetoothScoListener);
        if (audioManager.isBluetoothScoAvailableOffCall() && !isBluetoothScoOn) {
            audioManager.setBluetoothScoOn(true);
            isBluetoothScoOn = true;
            audioManager.startBluetoothSco();
            logger.v(TAG, "start bluetooth sco");
        }
    }

    void stopSco() {
        unregisterReceiver();
        if (audioManager.isBluetoothScoAvailableOffCall() && isBluetoothScoOn) {
            audioManager.setBluetoothScoOn(false);
            isBluetoothScoOn = false;
            audioManager.stopBluetoothSco();
            logger.v(TAG, "stop bluetooth sco");
        }
    }

    public boolean isBluetoothScoOn() {
        return isBluetoothScoOn;
    }
}
