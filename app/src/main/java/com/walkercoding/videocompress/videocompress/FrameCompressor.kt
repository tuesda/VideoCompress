package com.walkercoding.videocompress.videocompress

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build
import com.walkercoding.videocompress.videocompress.CompressInfo.Companion.MIME_TYPE
import com.walkercoding.videocompress.videocompress.CompressInfo.Companion.TIMEOUT_USEC
import com.walkercoding.videocompress.videocompress.helpers.InputSurface
import com.walkercoding.videocompress.videocompress.helpers.OutputSurface
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.schedulers.Schedulers
import java.io.IOException
import java.nio.ByteBuffer

class FrameCompressor {

    private var decoder: MediaCodec? = null
    private var encoder: MediaCodec? = null
    private var inputSurface: InputSurface? = null
    private var outputSurface: OutputSurface? = null

    private var outputDone = false
    private var inputDone = false
    private var decoderDone = false
    private var videoTrackIndex = INVALID_VIDEO_TRACK_INDEX

    private var decoderInputBuffers: Array<ByteBuffer>? = null
    private var encoderOutputBuffers: Array<ByteBuffer>? = null
    private var encoderInputBuffers: Array<ByteBuffer>? = null

    private val info = MediaCodec.BufferInfo()
    private var videoStartTime: Long = -1

    private val deviceOption: DeviceOption
        get() {
            val option = DeviceOption()
            if (Build.VERSION.SDK_INT < 18) {
                val codecInfo = Utils.selectCodec(MIME_TYPE)
                option.colorFormat = Utils.selectColorFormat(codecInfo!!, MIME_TYPE)
                if (option.colorFormat == 0) {
                    throw RuntimeException("no supported color format")
                }
                val codecName = codecInfo.name
                if (codecName.contains("OMX.qcom.")) {
                    option.processorType = PROCESSOR_TYPE_QCOM
                    if (Build.VERSION.SDK_INT == 16) {
                        if (option.manufacturer == "lge" || option.manufacturer == "nokia") {
                            option.swapUV = 1
                        }
                    }
                } else if (codecName.contains("OMX.Intel.")) {
                    option.processorType = PROCESSOR_TYPE_INTEL
                } else if (codecName == "OMX.MTK.VIDEO.ENCODER.AVC") {
                    option.processorType = PROCESSOR_TYPE_MTK
                } else if (codecName == "OMX.SEC.AVC.Encoder") {
                    option.processorType = PROCESSOR_TYPE_SEC
                    option.swapUV = 1
                } else if (codecName == "OMX.TI.DUCATI1.VIDEO.H264E") {
                    option.processorType = PROCESSOR_TYPE_TI
                }
                LogUtils.i("codec = " + codecInfo.name + " manufacturer = " + option.manufacturer + "device = " + Build.MODEL)
            } else {
                option.colorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
            }
            LogUtils.i("colorFormat = " + option.colorFormat)
            return option
        }

    @Throws(IOException::class)
    private fun initCoders(containerParam: ContainerConverter.Param, compressInfo: CompressInfo, deviceOption: DeviceOption) {
        // setup encoder
        encoder = MediaCodec.createEncoderByType(MIME_TYPE)
        encoder!!.configure(containerParam.getOutputFormat(compressInfo, deviceOption.colorFormat), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        if (Build.VERSION.SDK_INT >= 18) {
            inputSurface = InputSurface(encoder!!.createInputSurface())
            inputSurface!!.makeCurrent()
        }
        encoder!!.start()
        // setup decoder
        decoder = MediaCodec.createDecoderByType(containerParam.getInputFormat().getString(MediaFormat.KEY_MIME))
        if (Build.VERSION.SDK_INT >= 18) {
            outputSurface = OutputSurface()
        } else {
            outputSurface = OutputSurface(compressInfo.resultWidth, compressInfo.resultHeight, compressInfo.rotateRender)
        }
        decoder!!.configure(containerParam.getInputFormat(), outputSurface!!.surface, null, 0)
        decoder!!.start()
    }

    private fun getDecoderInputBuffer(index: Int): ByteBuffer {
        if (Build.VERSION.SDK_INT < 21) {
            if (decoderInputBuffers == null) {
                decoderInputBuffers = decoder!!.inputBuffers
            }
            return decoderInputBuffers!![index]
        } else {
            return decoder!!.getInputBuffer(index)
        }
    }

    private fun getEncodeOutputBuffer(index: Int): ByteBuffer? {
        if (Build.VERSION.SDK_INT < 21) {
            if (encoderOutputBuffers == null) {
                encoderOutputBuffers = encoder!!.outputBuffers
            }
            return encoderOutputBuffers!![index]
        } else {
            return encoder!!.getOutputBuffer(index)
        }
    }

    private fun getEncoderInputBuffers(index: Int): ByteBuffer {
        if (encoderInputBuffers == null) {
            encoderInputBuffers = encoder!!.inputBuffers
        }
        return encoderInputBuffers!![index]
    }

    private fun decodeFromExtractor(containerParam: ContainerConverter.Param) {
        if (!inputDone) {
            val index = containerParam.sampleTrackIndex
            if (index == containerParam.videoIndex) {
                val inputBufIndex = decoder!!.dequeueInputBuffer(TIMEOUT_USEC.toLong())
                if (inputBufIndex >= 0) {
                    val inputBuf = getDecoderInputBuffer(inputBufIndex)
                    val chunkSize = containerParam.readSampleData(inputBuf)
                    if (chunkSize < 0) {
                        finishDecoder(inputBufIndex)
                    } else {
                        decoder!!.queueInputBuffer(inputBufIndex, 0, chunkSize, containerParam.sampleTime, 0)
                        containerParam.advance()
                    }
                }
            } else if (index == -1) {
                // EOF
                val inputBufIndex = decoder!!.dequeueInputBuffer(TIMEOUT_USEC.toLong())
                if (inputBufIndex >= 0) {
                    finishDecoder(inputBufIndex)
                }
            }
        }
    }

    private fun finishDecoder(inputBufIndex: Int) {
        decoder!!.queueInputBuffer(inputBufIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        inputDone = true
    }

    /**
     * @return true -- encoder doesn't has pending surface that need be written to muxer
     * so need feed new surface to encoder
     */
    @Throws(Exception::class)
    private fun encodeToMuxer(containerParam: ContainerConverter.Param, compressInfo: CompressInfo): Boolean {
        val encoderOutputIndex = encoder!!.dequeueOutputBuffer(info, TIMEOUT_USEC.toLong())
        if (encoderOutputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
            return true
        }
        if (encoderOutputIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
            if (Build.VERSION.SDK_INT < 21) {
                // invalid encoderOutputBuffers
                encoderOutputBuffers = null
            }
            return false
        }
        if (encoderOutputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            val newFormat = encoder!!.outputFormat
            if (videoTrackIndex == INVALID_VIDEO_TRACK_INDEX) {
                videoTrackIndex = containerParam.addTrack(newFormat)
            }
            return false
        }
        if (encoderOutputIndex < 0) {
            throw RuntimeException("unexpected result from encoder.dequeueOutputBuffer: $encoderOutputIndex")
        }
        val encodedData = getEncodeOutputBuffer(encoderOutputIndex)
                ?: throw RuntimeException("encoderOutputBuffer $encoderOutputIndex was null")
        if (info.size > 1) {
            if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                if (containerParam.writeSampleData(videoTrackIndex, encodedData, info, true)) {
                    compressInfo.listener.onProgress(info.presentationTimeUs / (compressInfo.durationUs.toFloat()))
                }
            } else if (videoTrackIndex == INVALID_VIDEO_TRACK_INDEX) {
                addVideoTrackToMuxer(compressInfo, containerParam, encodedData)
            }
        }
        outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
        encoder!!.releaseOutputBuffer(encoderOutputIndex, false)
        return false
    }

    private fun addVideoTrackToMuxer(compressInfo: CompressInfo, containerParam: ContainerConverter.Param, encodedData: ByteBuffer) {
        val csd = ByteArray(info.size)
        encodedData.limit(info.offset + info.size)
        encodedData.position(info.offset)
        encodedData.get(csd)
        var sps: ByteBuffer? = null
        var pps: ByteBuffer? = null
        for (a in info.size - 1 downTo 0) {
            if (a > 3) {
                if (csd[a].toInt() == 1 && csd[a - 1].toInt() == 0 && csd[a - 2].toInt() == 0 && csd[a - 3].toInt() == 0) {
                    sps = ByteBuffer.allocate(a - 3)
                    pps = ByteBuffer.allocate(info.size - (a - 3))
                    sps!!.put(csd, 0, a - 3).position(0)
                    pps!!.put(csd, a - 3, info.size - (a - 3)).position(0)
                    break
                }
            } else {
                break
            }
        }
        val newFormat = MediaFormat.createVideoFormat(MIME_TYPE, compressInfo.resultWidth, compressInfo.resultHeight)
        if (sps != null) {
            newFormat.setByteBuffer("csd-0", sps)
            newFormat.setByteBuffer("csd-1", pps)
        }
        videoTrackIndex = containerParam.addTrack(newFormat)
    }

    private fun decoderToEncoder(compressInfo: CompressInfo, deviceOption: DeviceOption, adjustSize: AdjustSize) {
        if (!decoderDone) {
            val decoderStatus = decoder!!.dequeueOutputBuffer(info, TIMEOUT_USEC.toLong())
            if (decoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER || decoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                return
            }
            if (decoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val newFormat = decoder!!.outputFormat
                LogUtils.i("newFormat = $newFormat")
                return
            }
            if (decoderStatus < 0) {
                throw RuntimeException("unexpected result from decoder.dequeueOutputBuffer: $decoderStatus")
            }
            var doRender = info.size != 0 || Build.VERSION.SDK_INT < 18 && info.presentationTimeUs != 0L
            if (compressInfo.endTime > 0 && info.presentationTimeUs >= compressInfo.endTime) {
                // cut tail
                inputDone = true
                decoderDone = true
                doRender = false
                info.flags = info.flags or MediaCodec.BUFFER_FLAG_END_OF_STREAM
            }
            if (compressInfo.startTime > 0 && videoStartTime == -1L) {
                // cut header
                if (info.presentationTimeUs < compressInfo.startTime) {
                    doRender = false
                    LogUtils.i("drop frame startTime = " + compressInfo.startTime + " present time = " + info.presentationTimeUs)
                } else {
                    videoStartTime = info.presentationTimeUs
                }
            }
            decoder!!.releaseOutputBuffer(decoderStatus, doRender)
            if (doRender) {
                try {
                    outputSurface!!.awaitNewImage()
                    if (Build.VERSION.SDK_INT >= 18) {
                        outputSurface!!.drawImage(false)
                        inputSurface!!.setPresentationTime(info.presentationTimeUs * 1000)
                        inputSurface!!.swapBuffers()
                    } else {
                        val inputBufIndex = encoder!!.dequeueInputBuffer(TIMEOUT_USEC.toLong())
                        if (inputBufIndex >= 0) {
                            outputSurface!!.drawImage(true)
                            val rgbBuf = outputSurface!!.frame
                            val yuvBuf = getEncoderInputBuffers(inputBufIndex)
                            yuvBuf.clear()
                            JniUtils.convertVideoFrame(rgbBuf, yuvBuf, deviceOption.colorFormat, compressInfo.resultWidth, compressInfo.resultHeight, adjustSize.padding, deviceOption.swapUV)
                            encoder!!.queueInputBuffer(inputBufIndex, 0, adjustSize.bufferSize, info.presentationTimeUs, 0)
                        } else {
                            LogUtils.i("input buffer not available")
                        }
                    }
                } catch (e: Exception) {
                    LogUtils.e(e)
                }

            }
            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                LogUtils.i("decoder stream end")
                if (Build.VERSION.SDK_INT >= 18) {
                    encoder!!.signalEndOfInputStream()
                } else {
                    val inputBufIndex = encoder!!.dequeueInputBuffer(TIMEOUT_USEC.toLong())
                    if (inputBufIndex >= 0) {
                        encoder!!.queueInputBuffer(inputBufIndex, 0, 1, info.presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    }
                }
            }
        }
    }

    internal fun run(compressInfo: CompressInfo, containerParam: ContainerConverter.Param): Long {
        val deviceOption = deviceOption
        val adjustSize = getAdjustSize(compressInfo, deviceOption)

        containerParam.selectVideoIndex()
        containerParam.seekIfNeed(compressInfo.startTime)

        try {
            initCoders(containerParam, compressInfo, deviceOption)

            Observable.create(ObservableOnSubscribe<Any> {
                while (!inputDone) {
                    decodeFromExtractor(containerParam)
                }
            })
                    .subscribeOn(Schedulers.newThread())
                    .doOnError { throwable ->
                        if (throwable is Exception) {
                            throw throwable
                        }
                    }
                    .subscribe()

            while (!outputDone) {
                // write encoder data to muxer first, then read to encoder from decoder
                if (encodeToMuxer(containerParam, compressInfo)) {
                    if (!decoderDone) {
                        decoderToEncoder(compressInfo, deviceOption, adjustSize)
                    }
                }
            }
        } catch (e: Exception) {
            LogUtils.e(e)
            compressInfo.error = true
        }

        containerParam.unselectVideoIndex()
        release()
        return videoStartTime
    }

    private fun release() {
        if (outputSurface != null) {
            outputSurface!!.release()
        }
        if (inputSurface != null) {
            inputSurface!!.release()
        }
        if (decoder != null) {
            decoder!!.stop()
            decoder!!.release()
        }
        if (encoder != null) {
            encoder!!.stop()
            encoder!!.release()
        }
    }

    private class DeviceOption {
        internal var colorFormat: Int = 0
        internal var processorType = PROCESSOR_TYPE_OTHER
        internal var manufacturer = Build.MANUFACTURER.toLowerCase()
        internal var swapUV = 0
    }

    private class AdjustSize {
        internal var padding = 0
        internal var bufferSize: Int = 0
    }

    private fun getAdjustSize(compressInfo: CompressInfo, deviceOption: DeviceOption): AdjustSize {
        val adjustSize = AdjustSize()
        var resultHeightAligned = compressInfo.resultHeight
        adjustSize.bufferSize = compressInfo.resultWidth * compressInfo.resultHeight * 3 / 2

        if (deviceOption.processorType == PROCESSOR_TYPE_OTHER) {
            if (compressInfo.resultHeight % 16 != 0) {
                resultHeightAligned += 16 - compressInfo.resultHeight % 16
                adjustSize.padding = compressInfo.resultWidth * (resultHeightAligned - compressInfo.resultHeight)
                adjustSize.bufferSize += adjustSize.padding * 5 / 4
            }
        } else if (deviceOption.processorType == PROCESSOR_TYPE_QCOM) {
            if (deviceOption.manufacturer.toLowerCase() != "lge") {
                val uvoffset = compressInfo.resultWidth * compressInfo.resultHeight + 2047 and 2047.inv()
                adjustSize.padding = uvoffset - compressInfo.resultWidth * compressInfo.resultHeight
                adjustSize.bufferSize += adjustSize.padding
            }
        } else if (deviceOption.processorType == PROCESSOR_TYPE_TI) {
            //resultHeightAligned = 368;
            //bufferSize = resultWidth * resultHeightAligned * 3 / 2;
            //resultHeightAligned += (16 - (resultHeight % 16));
            //padding = resultWidth * (resultHeightAligned - resultHeight);
            //bufferSize += padding * 5 / 4;
        } else if (deviceOption.processorType == PROCESSOR_TYPE_MTK) {
            if (deviceOption.manufacturer == "baidu") {
                resultHeightAligned += 16 - compressInfo.resultHeight % 16
                adjustSize.padding = compressInfo.resultWidth * (resultHeightAligned - compressInfo.resultHeight)
                adjustSize.bufferSize += adjustSize.padding * 5 / 4
            }
        }

        return adjustSize
    }

    companion object {

        private val PROCESSOR_TYPE_OTHER = 0
        private val PROCESSOR_TYPE_QCOM = 1
        private val PROCESSOR_TYPE_INTEL = 2
        private val PROCESSOR_TYPE_MTK = 3
        private val PROCESSOR_TYPE_SEC = 4
        private val PROCESSOR_TYPE_TI = 5

        private val INVALID_VIDEO_TRACK_INDEX = -5
    }
}