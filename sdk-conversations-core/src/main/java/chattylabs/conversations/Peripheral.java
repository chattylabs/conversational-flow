package chattylabs.conversations;

import android.media.AudioManager;

public final class Peripheral {
    private AudioManager audioManager;

    public enum Type {
        WIRED, BLUETOOTH
    }

    public interface Device {
        boolean isConnected();
    }

    public Peripheral(AudioManager audioManager) {
        this.audioManager = audioManager;
    }

    public Device get(Type type) {
        switch (type) {
            case WIRED:
                return new WiredDevice(audioManager);
            case BLUETOOTH:
                return new BluetoothDevice(audioManager);
            default:
                throw new RuntimeException("Device type \"" + type + "\" not supported");
        }
    }
}
