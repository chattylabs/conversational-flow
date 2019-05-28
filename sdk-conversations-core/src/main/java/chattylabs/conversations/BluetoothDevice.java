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
    @SuppressWarnings("deprecation")
    @Override
    public boolean isConnected() {
        try {
            return audioManager.isBluetoothA2dpOn();
        } catch (Exception ignored) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                AudioDeviceInfo[] audioDevices = audioManager.getDevices(AudioManager.GET_DEVICES_ALL);
                for (AudioDeviceInfo deviceInfo : audioDevices) {
                    if (deviceInfo.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                            || deviceInfo.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                        return true;
                    }
                }
            }
            return false;
        }
    }
}
