package com.chattylabs.sdk.android.voice;

import android.app.Application;
import android.content.Intent;
import android.content.IntentFilter;
import android.telephony.TelephonyManager;

import com.chattylabs.sdk.android.common.Tag;
import com.chattylabs.sdk.android.common.internal.ILogger;

public class PhoneStateHandler {
    private static final String TAG = Tag.make("PhoneStateHandler");

    // States
    private boolean isPhoneStateReceiverRegistered; // released

    // Resources
    private Application application;
    private PhoneStateReceiver phoneStateReceiver = new PhoneStateReceiver();

    // Log stuff
    private ILogger logger;

    PhoneStateHandler(Application application, ILogger logger) {
        this.application = application;
        this.logger = logger;
    }

    public boolean isPhoneStateReceiverRegistered() {
        return isPhoneStateReceiverRegistered;
    }

    void registerReceiver(PhoneStateListener listener) {
        if (!isPhoneStateReceiverRegistered) {
            logger.v(TAG, "register for phone state receiver");
            IntentFilter phoneFilter = new IntentFilter();
            phoneFilter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
            phoneFilter.addAction(Intent.ACTION_NEW_OUTGOING_CALL);
            phoneStateReceiver.setListener(listener);
            application.registerReceiver(phoneStateReceiver, phoneFilter);
            isPhoneStateReceiverRegistered = true;
        }
    }

    void unregisterReceiver() {
        if (isPhoneStateReceiverRegistered) {
            logger.v(TAG, "unregister for phone state receiver");
            application.unregisterReceiver(phoneStateReceiver);
            isPhoneStateReceiverRegistered = false;
        }
    }
}
