package chattylabs.conversations;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;

import chattylabs.android.commons.Tag;
import chattylabs.android.commons.internal.ILogger;

public class BluetoothScoReceiver extends BroadcastReceiver {
    private static String TAG = Tag.make("BluetoothScoReceiver");

    private boolean isAlreadyConnected = false;
    private boolean isAlreadyDisconnected = false;
    protected BluetoothScoListenerAdapter listener;

    // Log stuff
    private ILogger logger;

    public void setListener(BluetoothScoListenerAdapter listener) {
        this.listener = listener;
    }

    public BluetoothScoListenerAdapter getListener() {
        return listener;
    }

    public BluetoothScoReceiver(ILogger logger) {
        this.logger = logger;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action != null)
            logger.i(TAG, "action: " + action);
        if (action != null && action.equals(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)) {
            int state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE,
                                           AudioManager.SCO_AUDIO_STATE_ERROR);
            logger.i(TAG, "state: " + state);
            logger.i(TAG, "isAlreadyConnected: " + isAlreadyConnected);
            logger.i(TAG, "isAlreadyDisconnected: " + isAlreadyDisconnected);
            if (state == AudioManager.SCO_AUDIO_STATE_CONNECTED) {
                if (!isAlreadyConnected) {
                    // TODO: check on analytics how many times this is called
                    isAlreadyDisconnected = false;
                    isAlreadyConnected = true;
                    if (getListener() != null) getListener().onConnected();
                }
            }
            else if (state == AudioManager.SCO_AUDIO_STATE_DISCONNECTED) {
                if (isAlreadyConnected && !isAlreadyDisconnected) {
                    // TODO: check on analytics how many times this is called
                    isAlreadyDisconnected = true;
                    isAlreadyConnected = false;
                    if (getListener() != null) getListener().onDisconnected();
                }
            }
        }
    }
}
