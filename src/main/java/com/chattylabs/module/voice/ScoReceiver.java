package com.chattylabs.module.voice;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;

public class ScoReceiver extends BroadcastReceiver {

    private boolean isAlreadyConnected = false;
    private boolean isAlreadyDisconnected = false;
    protected OnScoListener listener;

    public void setListener(OnScoListener listener) {
        this.listener = listener;
    }

    public OnScoListener getListener() {
        return listener;
    }

    public ScoReceiver() {}

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action != null && action.equals(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)) {
            int state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE,
                                           AudioManager.SCO_AUDIO_STATE_ERROR);
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
