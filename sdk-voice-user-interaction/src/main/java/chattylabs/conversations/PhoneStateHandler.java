package chattylabs.conversations;

import android.app.Application;

import chattylabs.android.commons.Tag;
import chattylabs.android.commons.internal.ILogger;

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
            logger.v(TAG, "unregister for phone state listener");
            phoneStateManager.release(application);
            isPhoneStateReceiverRegistered = false;
        }
    }
}
