package com.chattylabs.sdk.android.voice;

public interface PhoneStateListener {
    void onOutgoingCallStarts();

    void onIncomingCallRinging();

    void onOutgoingCallEnds();

    void onIncomingCallEnds();
}
