package com.walkercoding.videocompress.videocompress

import android.os.Build

import java.io.File

class CompressInfo private constructor(param: CompressParam) {

    internal var videoPath: String? = null
    internal var startTime: Long = 0
    internal var endTime: Long = 0
    internal var resultWidth: Int = 0
    internal var resultHeight: Int = 0
    internal var rotationValue: Int = 0
    internal val originalWidth: Int
    internal val originalHeight: Int
    internal val durationUs: Long
    internal var bitrate: Int = 0
    internal var rotateRender: Int = 0

    internal var inputFile: File
    internal var cacheFile: File

    internal var error = false

    lateinit var listener: Listener

    internal val isSizeValid: Boolean
        get() = resultWidth != 0 && resultHeight != 0

    init {
        videoPath = param.filePath
        startTime = param.startTime
        endTime = param.endTime
        resultWidth = param.resultWidth
        resultHeight = param.resultHeight
        rotationValue = param.rotationValue
        originalWidth = param.originalWidth
        originalHeight = param.originalHeight
        durationUs = param.durationUs
        bitrate = param.bitrate
        rotateRender = 0
        inputFile = File(videoPath!!)
        cacheFile = File(param.cacheFilePath!!)
    }

    private fun init() {
        if (Build.VERSION.SDK_INT < 18 && resultHeight > resultWidth && resultWidth != originalWidth && resultHeight != originalHeight) {
            val temp = resultHeight
            resultHeight = resultWidth
            resultWidth = temp
            rotationValue = 90
            rotateRender = 270
        } else if (Build.VERSION.SDK_INT > 20) {
            if (rotationValue == 90) {
                val temp = resultHeight
                resultHeight = resultWidth
                resultWidth = temp
                rotationValue = 0
                rotateRender = 270
            } else if (rotationValue == 180) {
                rotateRender = 180
                rotationValue = 0
            } else if (rotationValue == 270) {
                val temp = resultHeight
                resultHeight = resultWidth
                resultWidth = temp
                rotationValue = 0
                rotateRender = 90
            }
        }
    }

    fun needCompress(): Boolean {
        return resultWidth != originalWidth || resultHeight != originalHeight || rotateRender != 0
    }

    companion object {

        internal val MIME_TYPE = "video/avc"
        internal val TIMEOUT_USEC = 2500

        fun fromParam(param: CompressParam, listener: Listener): CompressInfo {
            val info = CompressInfo(param)
            info.listener = listener
            info.init()
            return info
        }
    }
}
