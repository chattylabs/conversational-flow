package chattylabs.conversations;

import android.annotation.SuppressLint;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;

public final class BluetoothDevice implements Peripheral.Device {

    private AudioManager audioManager;

    BluetoothDevice(AudioManager audioManager) {
        this.audioManager = audioManager;
    }

    @SuppressLint("NewApi")
    @Override
    public boolean isConnected() {
        try {
            return audioManager.isBluetoothA2dpOn();
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
                if (deviceInfo.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                    || deviceInfo.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                    return delegate.get(deviceInfo);
                }
            }
        }
        return defaultValue;
    }
}
