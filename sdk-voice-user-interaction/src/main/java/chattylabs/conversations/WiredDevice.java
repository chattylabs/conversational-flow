package chattylabs.conversations;

import android.annotation.SuppressLint;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;

import kotlin.collections.ArraysKt;

public final class WiredDevice implements Peripheral.Device {

    private AudioManager audioManager;

    WiredDevice(AudioManager audioManager) {
        this.audioManager = audioManager;
    }

    @SuppressLint("NewApi")
    @SuppressWarnings("deprecation")
    @Override
    public boolean isConnected() {
        try {
            return audioManager.isWiredHeadsetOn();
        } catch (Exception ignored) {
            return get(deviceInfo -> true, false);
        }
    }

    @SuppressLint("NewApi")
    @Override public int getId() {
        return get(AudioDeviceInfo::getId, -1);
    }

    @SuppressLint("NewApi")
    @Override public String getName(String defaultName) {
        return (String) get(AudioDeviceInfo::getProductName, defaultName);
    }

    private <T> T get(Peripheral.ReturnType<T> delegate, T defaultValue) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AudioDeviceInfo[] audioDevices = audioManager.getDevices(AudioManager.GET_DEVICES_ALL);
            for (AudioDeviceInfo deviceInfo : audioDevices) {
                if (ArraysKt.contains(new int[] {
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                    AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    //AudioDeviceInfo.TYPE_USB_ACCESSORY,
                    //AudioDeviceInfo.TYPE_USB_DEVICE,
                    AudioDeviceInfo.TYPE_USB_HEADSET,
                    //AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
                    //AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
                    AudioDeviceInfo.TYPE_HEARING_AID
                }, deviceInfo.getType())) {
                    return delegate.get(deviceInfo);
                }
            }
        }
        return defaultValue;
    }
}
