package com.chattylabs.module.voice;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.TelephonyManager;

public class PhoneStateReceiver extends BroadcastReceiver {

    private boolean incomingCall = false;
    private boolean outgoingCall = false;
    private int outgoingCallFlag;
    protected OnPhoneListener listener;

    public void setListener(OnPhoneListener listener) {
        this.listener = listener;
    }

    public OnPhoneListener getListener() {
        return listener;
    }

    public PhoneStateReceiver() {}

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null || getListener() == null) return;
        if(intent.getAction().equals(Intent.ACTION_NEW_OUTGOING_CALL)) {
            // OUTGOING CALL START
            outgoingCall = true;
            incomingCall = false;
            outgoingCallFlag = 2;
            getListener().onOutgoingCallStarts();
        } else {
            TelephonyManager tm = (TelephonyManager) context.getSystemService(Service.TELEPHONY_SERVICE);
            if (tm != null) {
                switch (tm.getCallState()) {
                    case TelephonyManager.CALL_STATE_RINGING:
                        // INCOMING CALL RINGING
                        outgoingCall = false;
                        incomingCall = true;
                        getListener().onIncomingCallRinging();
                        break;
                    case TelephonyManager.CALL_STATE_OFFHOOK:
                        if(outgoingCall) outgoingCallFlag++;
                        break;
                    case TelephonyManager.CALL_STATE_IDLE:
                        if(outgoingCall) {
                            outgoingCallFlag--;
                            if (outgoingCallFlag == 2) {
                                // OUTGOING CALL ENDS
                                outgoingCall = false;
                                incomingCall = false;
                                getListener().onOutgoingCallEnds();
                            }
                        } else {
                            if(incomingCall) {
                                // INCOMING CALL ENDS
                                outgoingCall = false;
                                incomingCall = false;
                                getListener().onIncomingCallEnds();
                            }
                        }
                        break;
                }
            }
        }
    }
}
