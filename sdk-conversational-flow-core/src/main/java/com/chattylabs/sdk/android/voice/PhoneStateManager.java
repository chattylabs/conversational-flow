package com.chattylabs.sdk.android.voice;

import android.app.Service;
import android.content.Context;
import android.os.Looper;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

public class PhoneStateManager {

    private PhoneStateListenerAdapter adapter;
    private boolean quitLooper;

    private PhoneStateListener listener;

    public PhoneStateManager() {}

    public void setAdapter(PhoneStateListenerAdapter adapter) {
        this.adapter = adapter;
    }

    public PhoneStateListenerAdapter getAdapter() {
        return adapter;
    }

    public void listen(Context context) {
        TelephonyManager tm = (TelephonyManager) context.getSystemService(Service.TELEPHONY_SERVICE);
        new Thread(() -> {
            quitLooper = false;
            Looper.prepare();
            listener = new PhoneStateListener() {
                @Override public void onCallStateChanged(int state, String incomingNumber) {
                    switch(state) {
                        case TelephonyManager.CALL_STATE_IDLE:
                            break;
                        case TelephonyManager.CALL_STATE_OFFHOOK:
                            if(incomingNumber==null) {
                                //outgoing call
                                getAdapter().onOutgoingCallEnds();
                            } else {
                                //incoming call
                                getAdapter().onIncomingCallEnds();
                            }
                            break;
                        case TelephonyManager.CALL_STATE_RINGING:
                            if(incomingNumber==null) {
                                //outgoing call
                                getAdapter().onOutgoingCallStarts();
                            } else {
                                //incoming call
                                getAdapter().onIncomingCallRinging();
                            }
                            break;
                    }
                    if(quitLooper) {
                        Looper looper = Looper.myLooper();
                        if (looper != null) looper.quit();
                    }
                    super.onCallStateChanged(state, incomingNumber);
                }
            };
            tm.listen(listener, PhoneStateListener.LISTEN_CALL_STATE);
            Looper.loop();
        }).start();
    }

    public void release(Context context) {
        if (listener != null) {
            quitLooper = true;
            TelephonyManager tm = (TelephonyManager) context.getSystemService(Service.TELEPHONY_SERVICE);
            tm.listen(listener, PhoneStateListener.LISTEN_NONE);
        }
    }
}
