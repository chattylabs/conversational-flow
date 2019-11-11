package chattylabs.conversations;

import android.app.Application;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Handler;

import com.chattylabs.android.commons.Tag;
import com.chattylabs.android.commons.internal.ILogger;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class AndroidBluetooth {
    private static final String TAG = Tag.make("AndroidBluetooth");

    // Lock
    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();

    // States
    private boolean isScoReceiverRegistered;
    private boolean isBluetoothScoOn;

    // Resources
    private Application application;
    private BluetoothAdapter adapter;
    private AndroidAudioManager audioManager;
    private BluetoothScoReceiver bluetoothScoReceiver;

    // Log stuff
    private ILogger logger;

    public AndroidBluetooth(Application application, AndroidAudioManager audioManager, ILogger logger) {
        this.application = application;
        this.adapter = BluetoothAdapter.getDefaultAdapter();
        this.audioManager = audioManager;
        this.logger = logger;
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

    private void registerScoReceiver(BluetoothScoListener bluetoothScoListener) {
        BluetoothScoListener helper = new BluetoothScoListener() {
            @Override
            public void onConnected() {
                new Handler().postDelayed(bluetoothScoListener::onConnected, 1500);
            }

            @Override
            public void onDisconnected() {
                unregisterScoReceiver();
                bluetoothScoListener.onDisconnected();
                lock.lock();
                try {
                    condition.signalAll();
                } catch (Exception e) {
                    logger.logException(e);
                } finally {
                    lock.unlock();
                }
            }
        };
        bluetoothScoReceiver = new BluetoothScoReceiver(logger);
        bluetoothScoReceiver.setListener(helper);
        if (!isScoReceiverRegistered) {
            logger.v(TAG, "register sco receiver");
            IntentFilter scoFilter = new IntentFilter();
            scoFilter.addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED);
            application.registerReceiver(bluetoothScoReceiver, scoFilter);
            isScoReceiverRegistered = true;
        }
    }

    private void unregisterScoReceiver() {
        if (isScoReceiverRegistered) {
            logger.v(TAG, "unregister sco receiver");
            application.unregisterReceiver(bluetoothScoReceiver);
            isScoReceiverRegistered = false;
        }
    }

    private void registerBluetoothProxy(Runnable runnable) {
        getProfileProxy(application, new BluetoothProfileListener() {
            @Override public void onServiceConnected(int profile, BluetoothProfile proxy) {

                runnable.run();

                if (isBluetoothHeadset()) {
                    ((BluetoothHeadset) proxy).startVoiceRecognition(proxy.getConnectedDevices().get(0));
                }

                logger.v(TAG, "start bluetooth sco");
                closeProfileProxy(profile, proxy);
            }
        }, isBluetoothHeadset() ? BluetoothProfile.HEADSET : BluetoothProfile.A2DP);
    }

    private void unRegisterBluetoothProxy(Runnable runnable) {
        getProfileProxy(application, new BluetoothProfileListener() {
            @Override public void onServiceConnected(int profile, BluetoothProfile proxy) {

                runnable.run();

                if (isBluetoothHeadset()) {
                    ((BluetoothHeadset) proxy).stopVoiceRecognition(proxy.getConnectedDevices().get(0));
                }

                logger.v(TAG, "stop bluetooth sco");
                closeProfileProxy(profile, proxy);
            }
        }, isBluetoothHeadset() ? BluetoothProfile.HEADSET : BluetoothProfile.A2DP);
    }

    public void startSco(BluetoothScoListener bluetoothScoListener) {
        registerScoReceiver(bluetoothScoListener);
        if (audioManager.isBluetoothScoAvailableOffCall() && !isBluetoothScoOn) {
            registerBluetoothProxy(() -> {
                isBluetoothScoOn = true;
                audioManager.startBluetoothSco();
                audioManager.setBluetoothScoOn(true);
            });
        }
    }

    public void stopSco() {
        unregisterScoReceiver();
        if (audioManager.isBluetoothScoAvailableOffCall() && isBluetoothScoOn) {
            registerBluetoothProxy(() -> {
                isBluetoothScoOn = false;
                audioManager.stopBluetoothSco();
                audioManager.stopBluetoothSco();
                audioManager.setBluetoothScoOn(false);
                lock.lock();
                try {
                    condition.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    logger.logException(e);
                } finally {
                    lock.unlock();
                }
            });
        }
    }

    public boolean isScoOn() {
        return isBluetoothScoOn;
    }
}
