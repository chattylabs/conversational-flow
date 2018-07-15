package com.chattylabs.sdk.android.voice.addon.google;

import android.content.Context;
import android.support.annotation.RawRes;

import com.chattylabs.sdk.android.common.Tag;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

public interface GoogleAccessToken {
    String TAG = Tag.make("AccessTokenHelper");

    List<String> SCOPE = Collections.singletonList("https://www.googleapis.com/auth/cloud-platform");

    /** This method runs by default on a AsyncTask thread */
    AccessToken retrieve();

    static AccessToken generateFromRawFile(Context context, @RawRes int rawResourceId) throws IOException {
        final InputStream stream = context.getResources().openRawResource(rawResourceId);
        final GoogleCredentials credentials = GoogleCredentials.fromStream(stream).createScoped(SCOPE);
        return credentials.refreshAccessToken();
    }
}
