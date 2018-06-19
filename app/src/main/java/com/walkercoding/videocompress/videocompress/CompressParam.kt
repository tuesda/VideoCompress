package com.walkercoding.videocompress.videocompress

class CompressParam {
    var filePath: String? = null
    var cacheFilePath: String? = null

    var startTime = -1L
    var endTime = -1L
    var resultWidth = 0
    var resultHeight = 0
    var bitrate = -1

    var rotationValue = -1
    var originalWidth = 0
    var originalHeight = 0
    var durationUs = 0L

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CompressParam

        if (filePath != other.filePath) return false
        if (cacheFilePath != other.cacheFilePath) return false
        if (startTime != other.startTime) return false
        if (endTime != other.endTime) return false
        if (resultWidth != other.resultWidth) return false
        if (resultHeight != other.resultHeight) return false
        if (bitrate != other.bitrate) return false
        if (rotationValue != other.rotationValue) return false
        if (originalWidth != other.originalWidth) return false
        if (originalHeight != other.originalHeight) return false
        if (durationUs != other.durationUs) return false

        return true
    }

    override fun hashCode(): Int {
        var result = filePath?.hashCode() ?: 0
        result = 31 * result + (cacheFilePath?.hashCode() ?: 0)
        result = 31 * result + startTime.hashCode()
        result = 31 * result + endTime.hashCode()
        result = 31 * result + resultWidth
        result = 31 * result + resultHeight
        result = 31 * result + bitrate
        result = 31 * result + rotationValue
        result = 31 * result + originalWidth
        result = 31 * result + originalHeight
        result = 31 * result + durationUs.hashCode()
        return result
    }


}
