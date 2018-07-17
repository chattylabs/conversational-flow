package com.chattylabs.sdk.android.voice;

public interface HelperAccessToken {
    /**
     * You can implement any blocking operation, by default it runs on a AsyncTask.
     */
    DefaultAccessToken get();
}
