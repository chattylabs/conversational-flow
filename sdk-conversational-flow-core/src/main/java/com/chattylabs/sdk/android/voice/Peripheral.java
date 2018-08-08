package com.chattylabs.sdk.android.voice;

import android.media.AudioManager;

public final class Peripheral {
    private AudioManager audioManager;

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
                return new PeripheralHeadsetDevice(audioManager);
            case BLUETOOTH:
                return new PeripheralBluetoothDevice(audioManager);
            default:
                throw new RuntimeException("Device type \"" + type + "\" does not exists");
        }
    }
}
