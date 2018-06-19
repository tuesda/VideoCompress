package com.walkercoding.videocompress.videocompress

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Build
import com.walkercoding.videocompress.videocompress.CompressInfo.Companion.MIME_TYPE
import com.walkercoding.videocompress.videocompress.helpers.MP4Builder
import com.walkercoding.videocompress.videocompress.helpers.Mp4Movie
import java.nio.ByteBuffer

class ContainerConverter {
    private var videoStartTime = 0L

    internal fun convert(compressInfo: CompressInfo) {
        var extractor: MediaExtractor? = null
        var muxer: MP4Builder? = null
        try {
            val internalExtractor = MediaExtractor()
            internalExtractor.setDataSource(compressInfo.videoPath!!)

            val movie = Mp4Movie()
            movie.cacheFile = compressInfo.cacheFile
            movie.setRotation(compressInfo.rotationValue)
            movie.setSize(compressInfo.resultWidth, compressInfo.resultHeight)
            val internalMuxer = MP4Builder().createMovie(movie)

            extractor = internalExtractor
            muxer = internalMuxer

            videoStartTime = compressInfo.startTime
            val info = MediaCodec.BufferInfo()
            // mux video
            if (compressInfo.needCompress()
                    // todo need support sdk 17
                    && Build.VERSION.SDK_INT >= 18) {
                val videoIndex = Utils.selectTrack(internalExtractor, false)
                if (videoIndex >= 0) {
                    val videoTime = FrameCompressor().run(compressInfo, Param(internalExtractor, internalMuxer!!, videoIndex))
                    if (videoTime != -1L) {
                        videoStartTime = videoTime
                    }
                }
            } else {
                val videoTime = Utils.readAndWriteTrack(compressInfo, internalExtractor, internalMuxer, info, compressInfo.startTime, false)
                if (videoTime != -1L) {
                    videoStartTime = videoTime
                }
            }
            // mux audio
            if (!compressInfo.error) {
                Utils.readAndWriteTrack(compressInfo, internalExtractor, internalMuxer, info, videoStartTime, true)
            }
        } finally {
            extractor?.release()
            muxer?.finishMovie()
        }
    }

    internal class Param(val extractor: MediaExtractor, val muxer: MP4Builder, val videoIndex: Int) {

        private var inputFormat: MediaFormat? = null

        val sampleTrackIndex: Int
            get() = extractor.sampleTrackIndex

        val sampleTime: Long
            get() = extractor.sampleTime

        fun selectVideoIndex() {
            extractor.selectTrack(videoIndex)
        }

        fun unselectVideoIndex() {
            extractor.unselectTrack(videoIndex)
        }

        fun addTrack(format: MediaFormat): Int {
            return muxer.addTrack(format, false)
        }

        fun seekIfNeed(seekTime: Long) {
            if (seekTime > 0) {
                extractor.seekTo(seekTime, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            } else {
                extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            }
        }

        fun getInputFormat(): MediaFormat {
            return inputFormat ?: let {
                val result = extractor.getTrackFormat(videoIndex)
                inputFormat = result
                result
            }
        }

        fun getOutputFormat(compressInfo: CompressInfo, colorFormat: Int): MediaFormat {
            val outputFormat = MediaFormat.createVideoFormat(MIME_TYPE, compressInfo.resultWidth, compressInfo.resultHeight)
            outputFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat)
            outputFormat.setInteger(MediaFormat.KEY_BIT_RATE, if (compressInfo.bitrate > 0) compressInfo.bitrate else 921600)
            outputFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 25)
            outputFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 10)
            if (Build.VERSION.SDK_INT < 18) {
                outputFormat.setInteger("stride", compressInfo.resultWidth + 32)
                outputFormat.setInteger("slice-height", compressInfo.resultHeight)
            }
            return outputFormat
        }

        fun readSampleData(byteBuffer: ByteBuffer): Int {
            return extractor.readSampleData(byteBuffer, 0)
        }

        fun advance() {
            extractor.advance()
        }

        @Throws(Exception::class)
        fun writeSampleData(trackIndex: Int, encodeBuffer: ByteBuffer, info: MediaCodec.BufferInfo, writeLength: Boolean): Boolean {
            return muxer.writeSampleData(trackIndex, encodeBuffer, info, writeLength)
        }
    }
}
