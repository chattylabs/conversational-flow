package com.chattylabs.sdk.android.voice;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;

class AWSConfiguration {

    private String poolId;

    private String region;

    private AWSConfiguration(String id, String region) {
        this.poolId = id;
        this.region = region;
    }

    public String getPoolId() {
        return poolId;
    }

    public String getRegion() {
        return region;
    }

    public static AWSConfiguration getConfiguration(InputStream inputStream) {
        final JsonObject defaultCognitoIdentity = new JsonParser().parse(new InputStreamReader(inputStream))
                .getAsJsonObject()
                .getAsJsonObject("CredentialsProvider")
                .getAsJsonObject("CognitoIdentity")
                .getAsJsonObject("Default");
        return new AWSConfiguration(defaultCognitoIdentity.get("PoolId").getAsString(),
                defaultCognitoIdentity.get("Region").getAsString());
    }
}
