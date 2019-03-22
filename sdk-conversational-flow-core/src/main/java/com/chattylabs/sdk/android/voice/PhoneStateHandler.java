package com.chattylabs.sdk.android.voice;

import android.app.Application;
import android.content.Intent;
import android.content.IntentFilter;
import android.telephony.TelephonyManager;

import com.chattylabs.android.commons.Tag;
import com.chattylabs.android.commons.internal.ILogger;

public class PhoneStateHandler {
    private static final String TAG = Tag.make("PhoneStateHandler");

    // States
    private boolean isPhoneStateReceiverRegistered; // released

    // Resources
    private Application application;
    private PhoneStateManager phoneStateManager = new PhoneStateManager();

    // Log stuff
    private ILogger logger;

    PhoneStateHandler(Application application, ILogger logger) {
        this.application = application;
        this.logger = logger;
    }

    void register(PhoneStateListenerAdapter listener) {
        if (!isPhoneStateReceiverRegistered) {
            logger.v(TAG, "register for phone state listener");
            phoneStateManager.setAdapter(listener);
            phoneStateManager.listen(application);
            isPhoneStateReceiverRegistered = true;
        }
    }

    void unregister() {
        if (isPhoneStateReceiverRegistered) {
            logger.v(TAG, "unregister for phone state receiver");
            phoneStateManager.release(application);
            isPhoneStateReceiverRegistered = false;
        }
    }
}
