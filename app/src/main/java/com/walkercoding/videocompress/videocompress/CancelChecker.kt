package com.walkercoding.videocompress.videocompress

object CancelChecker {

    private val syncObj = Any()
    private var canceled = false

    @Throws(Exception::class)
    fun checkCanceled() {
        var tmpCancelled = false
        synchronized(syncObj) {
            tmpCancelled = canceled
        }
        if (tmpCancelled) {
            throw RuntimeException("Program has been canceled!")
        }
    }

    fun cancel() {
        synchronized(syncObj) {
            canceled = true
        }
    }

    fun reset() {
        synchronized(syncObj) {
            canceled = false
        }
    }
}