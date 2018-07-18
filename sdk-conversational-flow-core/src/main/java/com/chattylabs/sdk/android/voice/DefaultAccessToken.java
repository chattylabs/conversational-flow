package com.chattylabs.sdk.android.voice;

import android.support.annotation.RawRes;

import java.util.Date;

public class DefaultAccessToken {

    private final String tokenValue;
    private final Long expirationTimeMillis;
    private final @RawRes int rawResourceId;

    public DefaultAccessToken(@RawRes int rawResourceId) {
        tokenValue = null;
        expirationTimeMillis = null;
        this.rawResourceId = rawResourceId;
    }

    /**
     * @param tokenValue String representation of the access token.
     * @param expirationTime Time when access token will expire.
     */
    public DefaultAccessToken(String tokenValue, Date expirationTime) {
        this.tokenValue = tokenValue;
        this.expirationTimeMillis = (expirationTime == null) ? null : expirationTime.getTime();
        this.rawResourceId = 0;
    }

    /**
     * String representation of the access token.
     */
    public String getTokenValue() {
        return tokenValue;
    }

    /**
     * Time when access token will expire.
     */
    public Date getExpirationTime() {
        if (expirationTimeMillis == null) {
            return null;
        }
        return new Date(expirationTimeMillis);
    }

    /**
     * Raw resource id where the credentials are stored. (Usually a json file)
     */
    public int getRawResourceId() {
        return rawResourceId;
    }
}
