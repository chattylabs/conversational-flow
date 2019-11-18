package chattylabs.conversations;

import android.net.Uri;
import android.util.Patterns;

import com.chattylabs.android.commons.StringUtils;

import java.util.List;

/**
 * Once applied it shortens an url leaving only {@code domain/first-path} as a string.
 */
public class TextFilterForUrl implements TextFilter {
    @Override
    public String apply(String message) {
        return StringUtils.replace(Patterns.WEB_URL, message, match -> {
            String urlString = match.group();
            Uri uri = Uri.parse(urlString);
            List<String> pathsSegments = uri.getPathSegments();
            if (pathsSegments != null && !pathsSegments.isEmpty()) {
                return uri.getHost() + "/" + pathsSegments.get(0);
            } else {
                return uri.getHost();
            }
        });
    }
}
