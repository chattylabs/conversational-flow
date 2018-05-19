package com.chattylabs.sdk.android.voice;

import android.media.AudioManager;

public class Peripheral {
    private AudioManager audioManager;
    private HeadsetDevice device;

    public enum Type {
        HEADSET
    }

    public Peripheral(AudioManager audioManager) {
        this.audioManager = audioManager;
    }

    public Peripheral get(Type type) {
        switch (type) {
            case HEADSET:
                device = new HeadsetDevice(audioManager);
        }
        return this;
    }

    public boolean isConnected() {
        return device != null && device.isConnected();
    }
}
