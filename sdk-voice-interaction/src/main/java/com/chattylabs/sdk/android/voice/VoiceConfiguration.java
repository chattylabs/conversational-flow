package com.chattylabs.sdk.android.voice;

public class VoiceConfiguration {
    private BluetoothScoLazyReturn bluetoothScoLazyReturn;

    private VoiceConfiguration(Builder builder) {
        bluetoothScoLazyReturn = builder.bluetoothScoLazyReturn;
    }

    public boolean isBluetoothScoRequired() {
        return bluetoothScoLazyReturn.isRequired();
    }

    public static final class Builder {
        private BluetoothScoLazyReturn bluetoothScoLazyReturn;

        public Builder() {
        }

        public Builder(VoiceConfiguration copy) {
            this.bluetoothScoLazyReturn = copy.bluetoothScoLazyReturn;
        }

        public Builder setBluetoothScoRequired(BluetoothScoLazyReturn lazyReturn) {
            this.bluetoothScoLazyReturn = lazyReturn;
            return this;
        }

        public VoiceConfiguration build() {
            return new VoiceConfiguration(this);
        }
    }

    public interface Update {
        VoiceConfiguration run(VoiceConfiguration.Builder builder);
    }

    public interface BluetoothScoLazyReturn {
        boolean isRequired();
    }
}
