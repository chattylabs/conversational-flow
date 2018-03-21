package com.chattylabs.module.voice;

public interface OnPhoneListener {
    void onOutgoingCallStarts();

    void onIncomingCallRinging();

    void onOutgoingCallEnds();

    void onIncomingCallEnds();
}
