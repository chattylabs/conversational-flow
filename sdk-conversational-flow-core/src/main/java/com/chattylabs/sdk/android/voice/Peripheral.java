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
        Device device = () -> false;
        if (type.equals(Type.WIRED_HEADSET)) device = new PeripheralHeadsetDevice(audioManager);
        if (type.equals(Type.BLUETOOTH)) device = new PeripheralBluetoothDevice(audioManager);
        return device;
    }
}
