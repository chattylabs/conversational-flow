package com.chattylabs.module.voice;

import android.net.Uri;
import android.util.Patterns;

import com.chattylabs.module.core.StringUtils;

import java.util.List;

public class UrlMessageFilter implements VoiceInteractionComponent.MessageFilter {
    @Override
    public String apply(String message) {
        return StringUtils.replace(Patterns.WEB_URL, message, match -> {
            String urlString = match.group();
            Uri uri = Uri.parse(urlString);
            List<String> pathsSegments = uri.getPathSegments();
            if (pathsSegments != null && pathsSegments.size() > 0) {
                return uri.getHost() + "/" + pathsSegments.get(0);
            } else {
                return uri.getHost();
            }
        });
    }
}
