package chattylabs.conversations;

import java.util.regex.Pattern;

import chattylabs.android.commons.StringUtils;

/**
 * Once applied it removes any character within brackets, including the brackets.
 * <br/>i.e. "Any relevant text (with an irrelevant text) on a string" ->
 * "Any relevant text on a string"
 */
public class FilterForBrackets implements Filter {
    @Override
    public String apply(String text) {
        return StringUtils.replace(Pattern.compile("\\([^)]*\\)\\s*"), text, match -> "");
    }
}
