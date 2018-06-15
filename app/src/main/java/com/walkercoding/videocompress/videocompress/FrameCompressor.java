package com.walkercoding.videocompress.videocompress;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import com.walkercoding.videocompress.videocompress.helpers.InputSurface;
import com.walkercoding.videocompress.videocompress.helpers.OutputSurface;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

import java.io.IOException;
import java.nio.ByteBuffer;

import static com.walkercoding.videocompress.videocompress.CompressInfo.MIME_TYPE;
import static com.walkercoding.videocompress.videocompress.CompressInfo.TIMEOUT_USEC;

public class FrameCompressor {

    private final static int PROCESSOR_TYPE_OTHER = 0;
    private final static int PROCESSOR_TYPE_QCOM = 1;
    private final static int PROCESSOR_TYPE_INTEL = 2;
    private final static int PROCESSOR_TYPE_MTK = 3;
    private final static int PROCESSOR_TYPE_SEC = 4;
    private final static int PROCESSOR_TYPE_TI = 5;

    private MediaCodec decoder = null;
    private MediaCodec encoder = null;
    private InputSurface inputSurface = null;
    private OutputSurface outputSurface = null;

    private boolean outputDone = false;
    private boolean inputDone = false;
    private boolean decoderDone = false;

    private static final int INVALID_VIDEO_TRACK_INDEX = -5;
    private int videoTrackIndex = INVALID_VIDEO_TRACK_INDEX;

    private ByteBuffer[] decoderInputBuffers = null;
    private ByteBuffer[] encoderOutputBuffers = null;
    private ByteBuffer[] encoderInputBuffers = null;

    private MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
    private long videoStartTime = -1;

    private void initCoders(ContainerConverter.Param containerParam, CompressInfo compressInfo, DeviceOption deviceOption) throws IOException {
        // setup encoder
        encoder = MediaCodec.createEncoderByType(MIME_TYPE);
        encoder.configure(containerParam.getOutputFormat(compressInfo, deviceOption.colorFormat), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        if (Build.VERSION.SDK_INT >= 18) {
            inputSurface = new InputSurface(encoder.createInputSurface());
            inputSurface.makeCurrent();
        }
        encoder.start();
        // setup decoder
        decoder = MediaCodec.createDecoderByType(containerParam.getInputFormat().getString(MediaFormat.KEY_MIME));
        if (Build.VERSION.SDK_INT >= 18) {
            outputSurface = new OutputSurface();
        } else {
            outputSurface = new OutputSurface(compressInfo.resultWidth, compressInfo.resultHeight, compressInfo.rotateRender);
        }
        decoder.configure(containerParam.getInputFormat(), outputSurface.getSurface(), null, 0);
        decoder.start();
    }

    private ByteBuffer getDecoderInputBuffer(int index) {
        if (Build.VERSION.SDK_INT < 21) {
            if (decoderInputBuffers == null) {
                decoderInputBuffers = decoder.getInputBuffers();
            }
            return decoderInputBuffers[index];
        } else {
            return decoder.getInputBuffer(index);
        }
    }

    private ByteBuffer getEncodeOutputBuffer(int index) {
        if (Build.VERSION.SDK_INT < 21) {
            if (encoderOutputBuffers == null) {
                encoderOutputBuffers = encoder.getOutputBuffers();
            }
            return encoderOutputBuffers[index];
        } else {
            return encoder.getOutputBuffer(index);
        }
    }

    private ByteBuffer getEncoderInputBuffers(int index) {
        if (encoderInputBuffers == null) {
            encoderInputBuffers = encoder.getInputBuffers();
        }
        return encoderInputBuffers[index];
    }

    private void fromExtractorToDecoder(ContainerConverter.Param containerParam) {
        if (!inputDone) {
            final int index = containerParam.getSampleTrackIndex();
            if (index == containerParam.videoIndex) {
                int inputBufIndex = decoder.dequeueInputBuffer(TIMEOUT_USEC);
                if (inputBufIndex >= 0) {
                    ByteBuffer inputBuf = getDecoderInputBuffer(inputBufIndex);
                    int chunkSize = containerParam.readSampleData(inputBuf);
                    if (chunkSize < 0) {
                        finishDecoder(inputBufIndex);
                    } else {
                        decoder.queueInputBuffer(inputBufIndex, 0, chunkSize, containerParam.getSampleTime(), 0);
                        containerParam.advance();
                    }
                }
            } else if (index == -1) {
                // EOF
                int inputBufIndex = decoder.dequeueInputBuffer(TIMEOUT_USEC);
                if (inputBufIndex >= 0) {
                    finishDecoder(inputBufIndex);
                }
            }
        }
    }

    private void finishDecoder(int inputBufIndex) {
        decoder.queueInputBuffer(inputBufIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
        inputDone = true;
    }

    /**
     * @return true -- encoder doesn't has pending surface that need be written to muxer
     * so need feed new surface to encoder
     */
    private boolean fromEncoderToMuxer(ContainerConverter.Param containerParam, CompressInfo compressInfo) throws Exception {
        int encoderOutputIndex = encoder.dequeueOutputBuffer(info, TIMEOUT_USEC);
        if (encoderOutputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
            return true;
        }
        if (encoderOutputIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
            if (Build.VERSION.SDK_INT < 21) {
                // invalid encoderOutputBuffers
                encoderOutputBuffers = null;
            }
            return false;
        }
        if (encoderOutputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            MediaFormat newFormat = encoder.getOutputFormat();
            if (videoTrackIndex == INVALID_VIDEO_TRACK_INDEX) {
                videoTrackIndex = containerParam.addTrack(newFormat);
            }
            return false;
        }
        if (encoderOutputIndex < 0) {
            throw new RuntimeException("unexpected result from encoder.dequeueOutputBuffer: " + encoderOutputIndex);
        }
        ByteBuffer encodedData = getEncodeOutputBuffer(encoderOutputIndex);
        if (encodedData == null) {
            throw new RuntimeException("encoderOutputBuffer " + encoderOutputIndex + " was null");
        }
        if (info.size > 1) {
            if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                if (containerParam.writeSampleData(videoTrackIndex, encodedData, info, true)) {
                    compressInfo.didWriteData(false, false);
                }
            } else if (videoTrackIndex == INVALID_VIDEO_TRACK_INDEX) {
                addVideoTrackToMuxer(compressInfo, containerParam, encodedData);
            }
        }
        outputDone = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
        encoder.releaseOutputBuffer(encoderOutputIndex, false);
        return false;
    }

    private void addVideoTrackToMuxer(CompressInfo compressInfo, ContainerConverter.Param containerParam, ByteBuffer encodedData) {
        byte[] csd = new byte[info.size];
        encodedData.limit(info.offset + info.size);
        encodedData.position(info.offset);
        encodedData.get(csd);
        ByteBuffer sps = null;
        ByteBuffer pps = null;
        for (int a = info.size - 1; a >= 0; a--) {
            if (a > 3) {
                if (csd[a] == 1 && csd[a - 1] == 0 && csd[a - 2] == 0 && csd[a - 3] == 0) {
                    sps = ByteBuffer.allocate(a - 3);
                    pps = ByteBuffer.allocate(info.size - (a - 3));
                    sps.put(csd, 0, a - 3).position(0);
                    pps.put(csd, a - 3, info.size - (a - 3)).position(0);
                    break;
                }
            } else {
                break;
            }
        }
        MediaFormat newFormat = MediaFormat.createVideoFormat(MIME_TYPE, compressInfo.resultWidth, compressInfo.resultHeight);
        if (sps != null) {
            newFormat.setByteBuffer("csd-0", sps);
            newFormat.setByteBuffer("csd-1", pps);
        }
        videoTrackIndex = containerParam.addTrack(newFormat);
    }

    private void fromDecoderToEncoder(CompressInfo compressInfo, DeviceOption deviceOption, AdjustSize adjustSize) {
        if (!decoderDone) {
            int decoderStatus = decoder.dequeueOutputBuffer(info, TIMEOUT_USEC);
            if (decoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER
                    || decoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                return;
            }
            if (decoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat newFormat = decoder.getOutputFormat();
                LogUtils.e("newFormat = " + newFormat);
                return;
            }
            if (decoderStatus < 0) {
                throw new RuntimeException("unexpected result from decoder.dequeueOutputBuffer: " + decoderStatus);
            }
            boolean doRender = info.size != 0 || (Build.VERSION.SDK_INT < 18 && info.presentationTimeUs != 0);
            if (compressInfo.endTime > 0 && info.presentationTimeUs >= compressInfo.endTime) {
                // cut tail
                inputDone = true;
                decoderDone = true;
                doRender = false;
                info.flags |= MediaCodec.BUFFER_FLAG_END_OF_STREAM;
            }
            if (compressInfo.startTime > 0 && videoStartTime == -1) {
                // cut header
                if (info.presentationTimeUs < compressInfo.startTime) {
                    doRender = false;
                    LogUtils.e("drop frame startTime = " + compressInfo.startTime + " present time = " + info.presentationTimeUs);
                } else {
                    videoStartTime = info.presentationTimeUs;
                }
            }
            decoder.releaseOutputBuffer(decoderStatus, doRender);
            if (doRender) {
                try {
                    outputSurface.awaitNewImage();
                    if (Build.VERSION.SDK_INT >= 18) {
                        outputSurface.drawImage(false);
                        inputSurface.setPresentationTime(info.presentationTimeUs * 1000);
                        inputSurface.swapBuffers();
                    } else {
                        int inputBufIndex = encoder.dequeueInputBuffer(TIMEOUT_USEC);
                        if (inputBufIndex >= 0) {
                            outputSurface.drawImage(true);
                            ByteBuffer rgbBuf = outputSurface.getFrame();
                            ByteBuffer yuvBuf = getEncoderInputBuffers(inputBufIndex);
                            yuvBuf.clear();
                            JniUtils.convertVideoFrame(rgbBuf, yuvBuf, deviceOption.colorFormat, compressInfo.resultWidth, compressInfo.resultHeight, adjustSize.padding, deviceOption.swapUV);
                            encoder.queueInputBuffer(inputBufIndex, 0, adjustSize.bufferSize, info.presentationTimeUs, 0);
                        } else {
                            LogUtils.e("input buffer not available");
                        }
                    }
                } catch (Exception e) {
                    LogUtils.e(e);
                }
            }
            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                LogUtils.e("decoder stream end");
                if (Build.VERSION.SDK_INT >= 18) {
                    encoder.signalEndOfInputStream();
                } else {
                    int inputBufIndex = encoder.dequeueInputBuffer(TIMEOUT_USEC);
                    if (inputBufIndex >= 0) {
                        encoder.queueInputBuffer(inputBufIndex, 0, 1, info.presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    }
                }
            }
        }
    }

    long run(final CompressInfo compressInfo, final ContainerConverter.Param containerParam) {
        try {
            final DeviceOption deviceOption = getDeviceOption();
            final AdjustSize adjustSize = getAdjustSize(compressInfo, deviceOption);

            containerParam.selectVideoIndex();
            containerParam.seekIfNeed(compressInfo.startTime);
            initCoders(containerParam, compressInfo, deviceOption);

            final Consumer<Throwable> onError = new Consumer<Throwable>() {
                @Override
                public void accept(Throwable throwable) throws Exception {
                    if (throwable instanceof Exception) {
                        throw (Exception) throwable;
                    }
                }
            };
            Observable.create(new ObservableOnSubscribe<Object>() {
                @Override
                public void subscribe(ObservableEmitter<Object> emitter) throws Exception {
                    while (!inputDone) {
                        fromExtractorToDecoder(containerParam);
                    }
                }
            })
                    .subscribeOn(Schedulers.newThread())
                    .doOnError(onError)
                    .subscribe();

            while (!outputDone) {
                // write encoder data to muxer first, then read to encoder from decoder
                if (fromEncoderToMuxer(containerParam, compressInfo)) {
                    if (!decoderDone) {
                        fromDecoderToEncoder(compressInfo, deviceOption, adjustSize);
                    }
                }
            }
        } catch (Exception e) {
            LogUtils.e(e);
            compressInfo.error = true;
        }

        containerParam.unselectVideoIndex();

        if (outputSurface != null) {
            outputSurface.release();
        }
        if (inputSurface != null) {
            inputSurface.release();
        }
        if (decoder != null) {
            decoder.stop();
            decoder.release();
        }
        if (encoder != null) {
            encoder.stop();
            encoder.release();
        }

        return videoStartTime;
    }

    private static class DeviceOption {
        private int colorFormat;
        private int processorType = PROCESSOR_TYPE_OTHER;
        private String manufacturer = Build.MANUFACTURER.toLowerCase();
        private int swapUV = 0;
    }

    private DeviceOption getDeviceOption() {
        DeviceOption option = new DeviceOption();
        if (Build.VERSION.SDK_INT < 18) {
            MediaCodecInfo codecInfo = Utils.selectCodec(MIME_TYPE);
            option.colorFormat = Utils.selectColorFormat(codecInfo, MIME_TYPE);
            if (option.colorFormat == 0) {
                throw new RuntimeException("no supported color format");
            }
            String codecName = codecInfo.getName();
            if (codecName.contains("OMX.qcom.")) {
                option.processorType = PROCESSOR_TYPE_QCOM;
                if (Build.VERSION.SDK_INT == 16) {
                    if (option.manufacturer.equals("lge") || option.manufacturer.equals("nokia")) {
                        option.swapUV = 1;
                    }
                }
            } else if (codecName.contains("OMX.Intel.")) {
                option.processorType = PROCESSOR_TYPE_INTEL;
            } else if (codecName.equals("OMX.MTK.VIDEO.ENCODER.AVC")) {
                option.processorType = PROCESSOR_TYPE_MTK;
            } else if (codecName.equals("OMX.SEC.AVC.Encoder")) {
                option.processorType = PROCESSOR_TYPE_SEC;
                option.swapUV = 1;
            } else if (codecName.equals("OMX.TI.DUCATI1.VIDEO.H264E")) {
                option.processorType = PROCESSOR_TYPE_TI;
            }
            LogUtils.e("codec = " + codecInfo.getName() + " manufacturer = " + option.manufacturer + "device = " + Build.MODEL);
        } else {
            option.colorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface;
        }
        LogUtils.e("colorFormat = " + option.colorFormat);
        return option;
    }

    private static class AdjustSize {
        private int padding = 0;
        private int bufferSize;
    }

    private AdjustSize getAdjustSize(CompressInfo compressInfo, DeviceOption deviceOption) {
        AdjustSize adjustSize = new AdjustSize();
        int resultHeightAligned = compressInfo.resultHeight;
        adjustSize.bufferSize = compressInfo.resultWidth * compressInfo.resultHeight * 3 / 2;

        if (deviceOption.processorType == PROCESSOR_TYPE_OTHER) {
            if (compressInfo.resultHeight % 16 != 0) {
                resultHeightAligned += (16 - (compressInfo.resultHeight % 16));
                adjustSize.padding = compressInfo.resultWidth * (resultHeightAligned - compressInfo.resultHeight);
                adjustSize.bufferSize += adjustSize.padding * 5 / 4;
            }
        } else if (deviceOption.processorType == PROCESSOR_TYPE_QCOM) {
            if (!deviceOption.manufacturer.toLowerCase().equals("lge")) {
                int uvoffset = (compressInfo.resultWidth * compressInfo.resultHeight + 2047) & ~2047;
                adjustSize.padding = uvoffset - (compressInfo.resultWidth * compressInfo.resultHeight);
                adjustSize.bufferSize += adjustSize.padding;
            }
        } else if (deviceOption.processorType == PROCESSOR_TYPE_TI) {
            //resultHeightAligned = 368;
            //bufferSize = resultWidth * resultHeightAligned * 3 / 2;
            //resultHeightAligned += (16 - (resultHeight % 16));
            //padding = resultWidth * (resultHeightAligned - resultHeight);
            //bufferSize += padding * 5 / 4;
        } else if (deviceOption.processorType == PROCESSOR_TYPE_MTK) {
            if (deviceOption.manufacturer.equals("baidu")) {
                resultHeightAligned += (16 - (compressInfo.resultHeight % 16));
                adjustSize.padding = compressInfo.resultWidth * (resultHeightAligned - compressInfo.resultHeight);
                adjustSize.bufferSize += adjustSize.padding * 5 / 4;
            }
        }

        return adjustSize;
    }
}