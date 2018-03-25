package com.chattylabs.module.voice;

import android.support.annotation.NonNull;

public interface EdgeSource {
    EdgeTarget from(@NonNull Node node);
}
