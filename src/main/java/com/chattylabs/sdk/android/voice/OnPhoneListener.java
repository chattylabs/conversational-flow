package com.chattylabs.sdk.android.voice;

public interface OnPhoneListener {
    void onOutgoingCallStarts();

    void onIncomingCallRinging();

    void onOutgoingCallEnds();

    void onIncomingCallEnds();
}
