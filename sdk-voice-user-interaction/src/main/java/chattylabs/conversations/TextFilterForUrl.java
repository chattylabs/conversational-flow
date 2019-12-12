package chattylabs.conversations;

import android.net.Uri;
import android.util.Log;
import android.util.Patterns;

import chattylabs.android.commons.StringUtils;

/**
 * Once applied it shortens an url leaving only {@code domain/first-path} as a string.
 */
public class TextFilterForUrl implements TextFilter {
    @Override
    public String apply(String message) {
        return StringUtils.replace(Patterns.WEB_URL, message, match -> {
            String urlString = match.group();
            Log.w("TextFilterForUrl", "Matched group: " + urlString);
            Uri uri = Uri.parse(urlString);
            //List<String> pathsSegments = uri.getPathSegments();
            if (uri.getHost() != null) {
                return uri.getHost();
            } else return message;
        });
    }
}
