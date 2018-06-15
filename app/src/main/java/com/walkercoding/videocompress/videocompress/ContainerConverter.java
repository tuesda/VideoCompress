package com.walkercoding.videocompress.videocompress;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import com.walkercoding.videocompress.videocompress.helpers.MP4Builder;
import com.walkercoding.videocompress.videocompress.helpers.Mp4Movie;

import java.nio.ByteBuffer;

import static com.walkercoding.videocompress.videocompress.CompressInfo.MIME_TYPE;

public class ContainerConverter {
    private MediaExtractor extractor = null;
    private MP4Builder mediaMuxer = null;
    private long videoStartTime;

    void convert(CompressInfo compressInfo) {
        try {
            initConverters(compressInfo);
            Utils.checkConversionCanceled();
            videoStartTime = compressInfo.startTime;
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            // mux video
            if (compressInfo.needCompress()) {
                final int videoIndex = Utils.selectTrack(extractor, false);
                if (videoIndex >= 0) {
                    final long videoTime = new FrameCompressor().run(compressInfo, new Param(extractor, mediaMuxer, videoIndex));
                    if (videoTime != -1) {
                        videoStartTime = videoTime;
                    }
                }
            } else {
                final long videoTime = Utils.readAndWriteTrack(compressInfo, extractor, mediaMuxer, info, compressInfo.startTime, false);
                if (videoTime != -1) {
                    videoStartTime = videoTime;
                }
            }
            // mux audio
            if (!compressInfo.error) {
                Utils.readAndWriteTrack(compressInfo, extractor, mediaMuxer, info, videoStartTime, true);
            }
        } catch (Exception e) {
            compressInfo.error = true;
            LogUtils.e(e);
        } finally {
            if (extractor != null) {
                extractor.release();
            }
            if (mediaMuxer != null) {
                try {
                    mediaMuxer.finishMovie();
                } catch (Exception e) {
                    LogUtils.e(e);
                }
            }
        }
    }

    private void initConverters(CompressInfo compressInfo) throws Exception {
        extractor = new MediaExtractor();
        extractor.setDataSource(compressInfo.videoPath);

        Mp4Movie movie = new Mp4Movie();
        movie.setCacheFile(compressInfo.cacheFile);
        movie.setRotation(compressInfo.rotationValue);
        movie.setSize(compressInfo.resultWidth, compressInfo.resultHeight);
        mediaMuxer = new MP4Builder().createMovie(movie);
    }

    static class Param {
        final MediaExtractor extractor;
        final MP4Builder muxer;
        final int videoIndex;

        private MediaFormat inputFormat;

        Param(MediaExtractor extractor, MP4Builder muxer, int videoIndex) {
            this.extractor = extractor;
            this.muxer = muxer;
            this.videoIndex = videoIndex;
        }

        void selectVideoIndex() {
            extractor.selectTrack(videoIndex);
        }

        void unselectVideoIndex() {
            extractor.unselectTrack(videoIndex);
        }

        int getSampleTrackIndex() {
            return extractor.getSampleTrackIndex();
        }

        int addTrack(MediaFormat format) {
            return muxer.addTrack(format, false);
        }

        void seekIfNeed(long seekTime) {
            if (seekTime > 0) {
                extractor.seekTo(seekTime, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            } else {
                extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            }
        }

        MediaFormat getInputFormat() {
            if (inputFormat == null) {
                inputFormat = extractor.getTrackFormat(videoIndex);
            }
            return inputFormat;
        }

        MediaFormat getOutputFormat(CompressInfo compressInfo, int colorFormat) {
            MediaFormat outputFormat = MediaFormat.createVideoFormat(MIME_TYPE, compressInfo.resultWidth, compressInfo.resultHeight);
            outputFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);
            outputFormat.setInteger(MediaFormat.KEY_BIT_RATE, compressInfo.bitrate > 0 ? compressInfo.bitrate : 921600);
            outputFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 25);
            outputFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 10);
            if (Build.VERSION.SDK_INT < 18) {
                outputFormat.setInteger("stride", compressInfo.resultWidth + 32);
                outputFormat.setInteger("slice-height", compressInfo.resultHeight);
            }
            return outputFormat;
        }

        int readSampleData(ByteBuffer byteBuffer) {
            return extractor.readSampleData(byteBuffer, 0);
        }

        long getSampleTime() {
            return extractor.getSampleTime();
        }

        void advance() {
            extractor.advance();
        }

        boolean writeSampleData(int trackIndex, ByteBuffer encodeBuffer, MediaCodec.BufferInfo info, boolean writeLength) throws Exception {
            return muxer.writeSampleData(trackIndex, encodeBuffer, info, writeLength);
        }
    }
}
