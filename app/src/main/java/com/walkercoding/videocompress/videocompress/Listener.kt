package com.walkercoding.videocompress.videocompress

interface Listener {
    fun onDone()
    fun onError(throwable: Throwable)
    fun onProgress(fraction: Float)
}