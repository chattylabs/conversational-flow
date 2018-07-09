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

    BluetoothSco(Application application, AudioManager audioManager, ILogger logger) {
        this.application = application;
        this.audioManager = audioManager;
        this.logger = logger;
    }

    private void registerReceiver(BluetoothScoListener bluetoothScoListener) {
        BluetoothScoListener helper = new BluetoothScoListener() {
            @Override
            public void onConnected() {
                bluetoothScoListener.onConnected();
            }

            @Override
            public void onDisconnected() {
                unregisterReceiver();
                bluetoothScoListener.onDisconnected();
            }
        };
        bluetoothScoReceiver.setListener(helper);
        if (!isScoReceiverRegistered) {
            logger.v(TAG, "register sco receiver");
            IntentFilter scoFilter = new IntentFilter();
            scoFilter.addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED);
            application.registerReceiver(bluetoothScoReceiver, scoFilter);
            isScoReceiverRegistered = true;
        }
    }

    private void unregisterReceiver() {
        if (isScoReceiverRegistered) {
            logger.v(TAG, "unregister sco receiver");
            application.unregisterReceiver(bluetoothScoReceiver);
            isScoReceiverRegistered = false;
        }
    }

    void startSco(BluetoothScoListener bluetoothScoListener) {
        registerReceiver(bluetoothScoListener);
        if (audioManager.isBluetoothScoAvailableOffCall() && !isBluetoothScoOn) {
            isBluetoothScoOn = true;
            audioManager.setBluetoothScoOn(true);
            audioManager.startBluetoothSco();
            logger.v(TAG, "start bluetooth sco");
        }
    }

    void stopSco() {
        if (audioManager.isBluetoothScoAvailableOffCall() && isBluetoothScoOn) {
            isBluetoothScoOn = false;
            audioManager.setBluetoothScoOn(false);
            audioManager.stopBluetoothSco();
            logger.v(TAG, "stop bluetooth sco");
        }
    }

    boolean isBluetoothScoOn() {
        return isBluetoothScoOn;
    }
}
