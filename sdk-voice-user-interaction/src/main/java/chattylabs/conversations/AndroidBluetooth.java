package chattylabs.conversations;

import android.app.Application;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.TimeUnit;

import chattylabs.android.commons.Tag;
import chattylabs.android.commons.ThreadUtils;
import chattylabs.android.commons.internal.ILogger;

public class AndroidBluetooth {
    private static final String TAG = Tag.make("AndroidBluetooth");

    // States
    private boolean isScoReceiverRegistered;
    private boolean isScoOn;

    // Resources
    private Application application;
    private ComponentConfig config;
    private BluetoothAdapter adapter;
    private AndroidAudioManager audioManager;
    private BluetoothScoReceiver bluetoothScoReceiver;
    private ThreadUtils.SerialThread serialThread;

    // Log stuff
    private ILogger logger;
    private Runnable onScoDisconnected;
    private Handler mainHandler;

    public AndroidBluetooth(Application application, AndroidAudioManager audioManager, ComponentConfig config, ILogger logger) {
        this.application = application;
        this.config = config;
        this.adapter = BluetoothAdapter.getDefaultAdapter();
        this.audioManager = audioManager;
        this.logger = logger;
        this.serialThread = ThreadUtils.newSerialThread();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public boolean isEnabled() {
        return adapter != null && adapter.isEnabled();
    }

    public boolean isDeviceConnected() {
        return isEnabled() && (isBluetoothHeadset() || isBluetoothA2DP());
    }

    public boolean isBluetoothHeadset() {
        return adapter.getProfileConnectionState(BluetoothProfile.HEADSET)
                == BluetoothProfile.STATE_CONNECTED;
    }

    public boolean isBluetoothA2DP() {
        return adapter.getProfileConnectionState(BluetoothProfile.A2DP)
                == BluetoothProfile.STATE_CONNECTED;
    }

    public void closeProfileProxy(int profile, BluetoothProfile proxy) {
        adapter.closeProfileProxy(profile, proxy);
    }

    public boolean getProfileProxy(Context context, BluetoothProfile.ServiceListener listener, int profile) {
        return adapter.getProfileProxy(context, listener, profile);
    }

    private void registerScoReceiver(Runnable onScoConnected) {
        BluetoothScoListenerAdapter helper = new BluetoothScoListenerAdapter() {
            @Override
            public void onConnected() {
                logger.i(TAG, "Sco connected");

                mainHandler.postDelayed(() -> {
                    if (onScoConnected != null) onScoConnected.run();
                    serialThread.release();
                }, 2500);
            }

            @Override
            public void onDisconnected() {
                logger.i(TAG, "Sco disconnected");

                if (mainHandler != null) {
                    mainHandler.removeCallbacksAndMessages(null);
                }
                audioManager.unsetAudioMode();
                unregisterScoReceiver();
                audioManager.setBluetoothScoOn(false);
                isScoOn = false;
                if (onScoDisconnected != null) onScoDisconnected.run();
                serialThread.release();
            }
        };
        bluetoothScoReceiver = new BluetoothScoReceiver(logger);
        bluetoothScoReceiver.setListener(helper);
        if (!isScoReceiverRegistered) {
            logger.v(TAG, "register Sco receiver");
            IntentFilter scoFilter = new IntentFilter();
            scoFilter.addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED);
            application.registerReceiver(bluetoothScoReceiver, scoFilter);
            isScoReceiverRegistered = true;
        }
    }

    private void unregisterScoReceiver() {
        if (isScoReceiverRegistered) {
            logger.v(TAG, "unregister sco receiver");
            try {
                application.unregisterReceiver(bluetoothScoReceiver);
            } catch (Exception ignore){}
            isScoReceiverRegistered = false;
        }
    }

    private void registerBluetoothProxy(Runnable runnable) {
        getProfileProxy(application, new BluetoothProfileListenerAdapter() {
            @Override public void onServiceConnected(int profile, BluetoothProfile proxy) {

                runnable.run();

                if (isBluetoothHeadset()) {
                    ((BluetoothHeadset) proxy).startVoiceRecognition(proxy.getConnectedDevices().get(0));
                }

                closeProfileProxy(profile, proxy);
            }
        }, isBluetoothHeadset() ? BluetoothProfile.HEADSET : BluetoothProfile.A2DP);
    }

    private void unRegisterBluetoothProxy(Runnable runnable) {
        getProfileProxy(application, new BluetoothProfileListenerAdapter() {
            @Override public void onServiceConnected(int profile, BluetoothProfile proxy) {

                runnable.run();

                if (isBluetoothHeadset()) {
                    ((BluetoothHeadset) proxy).stopVoiceRecognition(proxy.getConnectedDevices().get(0));
                }

                closeProfileProxy(profile, proxy);
            }
        }, isBluetoothHeadset() ? BluetoothProfile.HEADSET : BluetoothProfile.A2DP);
    }

    public void startSco(Runnable onScoConnected/*, Runnable onScoDisconnected*/) {
        //this.onScoDisconnected = onScoDisconnected; //TODO this is useful to stop everything if the user closed the sco call
        if (audioManager.isBluetoothScoAvailableOffCall() && !isScoOn) {
            isScoOn = true;
            serialThread.addTaskBlocking(() -> {
                logger.i(TAG, "Start Sco");
                registerScoReceiver(onScoConnected);
                registerBluetoothProxy(() -> {
                    audioManager.setAudioMode(
                            config.getBluetoothScoAudioMode());
                    audioManager.startBluetoothSco();
                    audioManager.setBluetoothScoOn(true);
                });
            }, 10, TimeUnit.SECONDS);
        } else if (isScoOn) {
            if (mainHandler != null) {
                mainHandler.removeCallbacksAndMessages(null);
                serialThread.release();
            }
            serialThread.addTaskBlocking(() -> {
                mainHandler.post(() -> {
                    onScoConnected.run();
                    serialThread.release();
                });
            }, 10, TimeUnit.SECONDS);
        } else onScoConnected.run();
    }

    public void stopSco(Runnable onScoDisconnected) {
        if (audioManager.isBluetoothScoAvailableOffCall() && isScoOn) {
            if (mainHandler != null) {
                mainHandler.removeCallbacksAndMessages(null);
                serialThread.release();
            }
            serialThread.addTaskBlocking(() -> {
                this.onScoDisconnected = onScoDisconnected;
                logger.i(TAG, "Stop Sco");
                unRegisterBluetoothProxy(() -> {
                    mainHandler.postDelayed(() -> {
                        // We have to call this twice
                        audioManager.stopBluetoothSco();
                        audioManager.stopBluetoothSco();
                        // TODO: Maybe not needed here
                        audioManager.setBluetoothScoOn(false);
                        isScoOn = false;
                        // TODO: There are still race conditions here
                    }, 1150);
                });
            }, 10, TimeUnit.SECONDS);
        } else onScoDisconnected.run();
    }

    public boolean isScoOn() {
        return isScoOn;
    }
}
