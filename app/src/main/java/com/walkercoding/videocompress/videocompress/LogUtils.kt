package com.walkercoding.videocompress.videocompress

import android.util.Log

object LogUtils {
    fun e(e: Throwable) {
        Log.e("zl", "Error throwable: " + e.message)
    }

    fun i(message: String) {
        Log.e("zl", "Error msg: $message")
    }
}
