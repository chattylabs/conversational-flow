package com.chattylabs.sdk.android.voice;

public abstract class PhoneStateListenerAdapter {
    public void onOutgoingCallStarts() {}

    public void onIncomingCallRinging() {}

    public void onOutgoingCallEnds() {}

    public void onIncomingCallEnds() {}
}
