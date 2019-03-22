package com.chattylabs.sdk.android.voice;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

public class PhoneStateManager {

    private PhoneStateListenerAdapter adapter;

    private PhoneStateListener listener = new PhoneStateListener() {
        @Override public void onCallStateChanged(int state, String incomingNumber) {
            super.onCallStateChanged(state, incomingNumber);
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
        }
    };

    public PhoneStateManager() {}

    public void setAdapter(PhoneStateListenerAdapter adapter) {
        this.adapter = adapter;
    }

    public PhoneStateListenerAdapter getAdapter() {
        return adapter;
    }

    public void listen(Context context) {
        TelephonyManager tm = (TelephonyManager) context.getSystemService(Service.TELEPHONY_SERVICE);
        tm.listen(listener, PhoneStateListener.LISTEN_CALL_STATE);
    }

    public void release(Context context) {
        TelephonyManager tm = (TelephonyManager) context.getSystemService(Service.TELEPHONY_SERVICE);
        tm.listen(listener, PhoneStateListener.LISTEN_NONE);
    }
}
