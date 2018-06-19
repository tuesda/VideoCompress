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
}
