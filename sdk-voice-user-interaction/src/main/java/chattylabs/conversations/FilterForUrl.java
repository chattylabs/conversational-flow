package chattylabs.conversations;

import android.net.Uri;
import android.util.Log;
import android.util.Patterns;

import chattylabs.android.commons.StringUtils;

/**
 * Once applied it shortens a url leaving only {@code domain/} as a string.
 */
public class FilterForUrl implements Filter {
    @Override
    public String apply(String text) {
        return StringUtils.replace(Patterns.WEB_URL, text, match -> {
            String urlString = match.group();
            Log.w("FilterForUrl", "Matched group: " + urlString);
            Uri uri = Uri.parse(urlString);
            //List<String> pathsSegments = uri.getPathSegments();
            if (uri.getHost() != null) {
                return uri.getHost();
            } else return text;
        });
    }
}
