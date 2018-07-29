package com.chattylabs.sdk.android.voice;

import junit.framework.Assert;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class TextFilterForUrlTest {

    @Test
    public void apply() {
        String empty = "";
        String httpLink = "http://example.com";
        String httpsLink = "https://example.com/";
        String pathLink = "http://example.com/any/path/";
        String textAndLink = "A text with a http://www.example.com/link/that should be shortened";
        String fullLink = "http://www.example.com/any/path/intex.html?parameter=value%20random&another=#even";
        TextFilter filter = new TextFilterForUrl();

        Assert.assertEquals(filter.apply(empty), "");
        Assert.assertEquals(filter.apply(httpLink), "example.com");
        Assert.assertEquals(filter.apply(httpsLink), "example.com");
        Assert.assertEquals(filter.apply(pathLink), "example.com/any");
        Assert.assertEquals(filter.apply(textAndLink), "A text with a " +
                "www.example.com/link should be shortened");
        Assert.assertEquals(filter.apply(fullLink), "www.example.com/any");
    }
}