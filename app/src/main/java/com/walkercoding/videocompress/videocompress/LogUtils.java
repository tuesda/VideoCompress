package com.walkercoding.videocompress.videocompress;

import android.util.Log;

public class LogUtils {
    public static void e(final Throwable e) {
        Log.e("zl", "Error throwable: " + e.getMessage());
    }

    public static void i(final String message) {
        Log.e("zl", "Error msg: " + message);
    }
}
