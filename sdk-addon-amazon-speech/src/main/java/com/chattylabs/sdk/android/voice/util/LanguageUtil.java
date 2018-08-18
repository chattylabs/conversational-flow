package com.chattylabs.sdk.android.voice.util;

import com.amazonaws.services.polly.model.LanguageCode;

import java.util.Locale;

public final class LanguageUtil {

    private LanguageUtil() {
        // Language Util
    }

    public static LanguageCode getDeviceLanguageCode(Locale speechLanguage) {
        final Locale currentLocale = speechLanguage != null ? speechLanguage : Locale.getDefault();
        final LanguageCode languageCode = LanguageCode.fromValue(currentLocale.getLanguage() +
                "-" + currentLocale.getCountry().toUpperCase());
        if (languageCode == null) {
            throw new IllegalStateException("Language not supported by Amazon Polly.");
        }

        return languageCode;
    }
}
