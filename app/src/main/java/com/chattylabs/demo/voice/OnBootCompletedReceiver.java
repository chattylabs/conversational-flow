package com.chattylabs.demo.voice;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class OnBootCompletedReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.w("OnBootCompletedReceiver", "Device rebooted!");
        GeneralForegroundService.start(context.getApplicationContext());
    }
}
