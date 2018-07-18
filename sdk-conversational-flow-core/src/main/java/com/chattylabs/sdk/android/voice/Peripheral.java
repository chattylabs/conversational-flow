package com.chattylabs.sdk.android.voice;

import android.media.AudioManager;

public final class Peripheral {
    private AudioManager audioManager;
    private Device device;

    public enum Type {
        WIRED_HEADSET, BLUETOOTH
    }

    public interface Device {
        boolean isConnected();
    }

    public Peripheral(AudioManager audioManager) {
        this.audioManager = audioManager;
    }

    public Device get(Type type) {
        switch (type) {
            case WIRED_HEADSET:
                device = new PeripheralHeadsetDevice(audioManager);
            case BLUETOOTH:
                device = new PeripheralBluetoothDevice(audioManager);
        }
        return device;
    }
}
