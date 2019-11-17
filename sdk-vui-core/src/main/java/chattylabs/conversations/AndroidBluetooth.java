package chattylabs.conversations;

import android.annotation.SuppressLint;
import android.app.Application;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Handler;

import com.chattylabs.android.commons.Tag;
import com.chattylabs.android.commons.ThreadUtils;
import com.chattylabs.android.commons.internal.ILogger;

import java.util.concurrent.TimeUnit;

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
    private Handler connectedHandler;

    public AndroidBluetooth(Application application, AndroidAudioManager audioManager, ComponentConfig config, ILogger logger) {
        this.application = application;
        this.config = config;
        this.adapter = BluetoothAdapter.getDefaultAdapter();
        this.audioManager = audioManager;
        this.logger = logger;
        this.serialThread = ThreadUtils.newSerialThread();
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
        BluetoothScoListener helper = new BluetoothScoListener() {
            @Override
            @SuppressLint("NewApi")
            public void onConnected() {
//                AudioFocusRequest b = new AudioFocusRequest
//                        .Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
//                        .setAudioAttributes(new AudioAttributes.Builder()
//                                //.setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
//                                //.setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
//                                //.setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED | AudioAttributes.FLAG_HW_AV_SYNC)
//                                .setLegacyStreamType(6)
//                                .build()).build();
//                audioManager.getDefaultAudioManager().requestAudioFocus(b);
                logger.i(TAG, "Sco connected");

                connectedHandler = new Handler();
                connectedHandler.postDelayed(() -> {
                    onScoConnected.run();
                    serialThread.release();
                }, 2500);
            }

            @Override
            public void onDisconnected() {
                logger.i(TAG, "Sco disconnected");

                if (connectedHandler != null) {
                    connectedHandler.removeCallbacksAndMessages(null);
                    connectedHandler = null;
                }
                unregisterScoReceiver();
                onScoDisconnected.run();
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
        getProfileProxy(application, new BluetoothProfileListener() {
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

    public void startSco(Runnable onScoConnected) {
        if (audioManager.isBluetoothScoAvailableOffCall() && !isScoOn) {
            isScoOn = true;
            serialThread.addTaskBlocking(() -> {
                logger.i(TAG, "Start Sco");
                registerScoReceiver(onScoConnected);
                registerBluetoothProxy(() -> {
                    audioManager.setAudioMode(config.getBluetoothScoAudioMode());
                    audioManager.startBluetoothSco();
                    audioManager.setBluetoothScoOn(true);
                });
            }, 10, TimeUnit.SECONDS);
        } else if (isScoOn) {
            serialThread.addTaskBlocking(() -> {
                onScoConnected.run();
                serialThread.release();
            }, 10, TimeUnit.SECONDS);
        } else onScoConnected.run();
    }

    public void stopSco(Runnable runnable) {
        if (audioManager.isBluetoothScoAvailableOffCall() && isScoOn) {
            if (connectedHandler != null) {
                connectedHandler.removeCallbacksAndMessages(null);
                connectedHandler = null;
                serialThread.release();
            }
            serialThread.addTaskBlocking(() -> {
                this.onScoDisconnected = runnable;
                logger.i(TAG, "Stop Sco");
                unRegisterBluetoothProxy(() -> {
                    new Handler().postDelayed(() -> {
                        audioManager.unsetAudioMode();
                        audioManager.stopBluetoothSco();
                        //audioManager.stopBluetoothSco();
                        audioManager.setBluetoothScoOn(false);
                        isScoOn = false;
                        // TODO: There are still race conditions here
                    }, 1150);
                });
            }, 10, TimeUnit.SECONDS);
        } else runnable.run();
    }

    public boolean isScoOn() {
        return isScoOn;
    }
}
