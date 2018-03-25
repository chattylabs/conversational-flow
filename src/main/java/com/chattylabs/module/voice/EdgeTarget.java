package com.chattylabs.module.voice;

import android.support.annotation.NonNull;

public interface EdgeTarget {
    void to(@NonNull Node node, Node... optNodes);
}
