package com.walkercoding.videocompress.videocompress

import android.annotation.SuppressLint
import android.media.*
import android.os.Build
import com.walkercoding.videocompress.videocompress.helpers.MP4Builder

import java.nio.ByteBuffer

object Utils {

    @SuppressLint("NewApi")
    internal fun selectCodec(mimeType: String): MediaCodecInfo? {
        val numCodecs = MediaCodecList.getCodecCount()
        var lastCodecInfo: MediaCodecInfo? = null
        for (i in 0 until numCodecs) {
            val codecInfo = MediaCodecList.getCodecInfoAt(i)
            if (!codecInfo.isEncoder) {
                continue
            }
            val types = codecInfo.supportedTypes
            for (type in types) {
                if (type.equals(mimeType, ignoreCase = true)) {
                    lastCodecInfo = codecInfo
                    if (lastCodecInfo!!.name != "OMX.SEC.avc.enc") {
                        return lastCodecInfo
                    } else if (lastCodecInfo.name == "OMX.SEC.AVC.Encoder") {
                        return lastCodecInfo
                    }
                }
            }
        }
        return lastCodecInfo
    }

    internal fun selectTrack(extractor: MediaExtractor, audio: Boolean): Int {
        val numTracks = extractor.trackCount
        for (i in 0 until numTracks) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (audio) {
                if (mime.startsWith("audio/")) {
                    return i
                }
            } else {
                if (mime.startsWith("video/")) {
                    return i
                }
            }
        }
        return -5
    }

    @SuppressLint("NewApi")
    internal fun selectColorFormat(codecInfo: MediaCodecInfo, mimeType: String): Int {
        val capabilities = codecInfo.getCapabilitiesForType(mimeType)
        var lastColorFormat = 0
        for (i in capabilities.colorFormats.indices) {
            val colorFormat = capabilities.colorFormats[i]
            if (isRecognizedFormat(colorFormat)) {
                lastColorFormat = colorFormat
                if (!(codecInfo.name == "OMX.SEC.AVC.Encoder" && colorFormat == 19)) {
                    return colorFormat
                }
            }
        }
        return lastColorFormat
    }

    private fun isRecognizedFormat(colorFormat: Int): Boolean {
        when (colorFormat) {
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar, MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar -> return true
            else -> return false
        }
    }

    @Throws(Exception::class)
    internal fun readAndWriteTrack(compressInfo: CompressInfo, extractor: MediaExtractor, mediaMuxer: MP4Builder, info: MediaCodec.BufferInfo, start: Long, isAudio: Boolean): Long {
        val trackIndex = Utils.selectTrack(extractor, isAudio)
        if (trackIndex >= 0) {
            extractor.selectTrack(trackIndex)
            val trackFormat = extractor.getTrackFormat(trackIndex)
            val muxerTrackIndex = mediaMuxer.addTrack(trackFormat, isAudio)
            val maxBufferSize = trackFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
            var inputDone = false
            if (start > 0) {
                extractor.seekTo(start, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            } else {
                extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            }
            val buffer = ByteBuffer.allocateDirect(maxBufferSize)
            var startTime: Long = -1

            while (!inputDone) {
                CancelChecker.checkCanceled()
                var eof = false
                val index = extractor.sampleTrackIndex
                if (index == trackIndex) {
                    info.size = extractor.readSampleData(buffer, 0)
                    if (Build.VERSION.SDK_INT < 21) {
                        buffer.position(0)
                        buffer.limit(info.size)
                    }
                    if (!isAudio) {
                        val array = buffer.array()
                        if (array != null) {
                            val offset = buffer.arrayOffset()
                            val len = offset + buffer.limit()
                            var writeStart = -1
                            for (a in offset..len - 4) {
                                if (array[a].toInt() == 0 && array[a + 1].toInt() == 0 && array[a + 2].toInt() == 0 && array[a + 3].toInt() == 1 || a == len - 4) {
                                    if (writeStart != -1) {
                                        val l = a - writeStart - if (a != len - 4) 4 else 0
                                        array[writeStart] = (l shr 24).toByte()
                                        array[writeStart + 1] = (l shr 16).toByte()
                                        array[writeStart + 2] = (l shr 8).toByte()
                                        array[writeStart + 3] = l.toByte()
                                        writeStart = a
                                    } else {
                                        writeStart = a
                                    }
                                }
                            }
                        }
                    }
                    if (info.size >= 0) {
                        info.presentationTimeUs = extractor.sampleTime
                    } else {
                        info.size = 0
                        eof = true
                    }

                    if (info.size > 0 && !eof) {
                        if (start > 0 && startTime == -1L) {
                            startTime = info.presentationTimeUs
                        }
                        if (compressInfo.endTime < 0 || info.presentationTimeUs < compressInfo.endTime) {
                            info.offset = 0
                            info.flags = extractor.sampleFlags
                            if (mediaMuxer.writeSampleData(muxerTrackIndex, buffer, info, false)) {
                                compressInfo.listener.onProgress(info.presentationTimeUs / compressInfo.durationUs.toFloat())
                            }
                        } else {
                            eof = true
                        }
                    }
                    if (!eof) {
                        extractor.advance()
                    }
                } else if (index == -1) {
                    eof = true
                } else {
                    extractor.advance()
                }
                if (eof) {
                    inputDone = true
                }
            }

            extractor.unselectTrack(trackIndex)
            return startTime
        }
        return -1
    }
}
