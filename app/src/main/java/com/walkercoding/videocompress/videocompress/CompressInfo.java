package com.walkercoding.videocompress.videocompress;

import android.os.Build;

import java.io.File;

public class CompressInfo {

    final static String MIME_TYPE = "video/avc";
    static final int TIMEOUT_USEC = 2500;

    String videoPath;
    long startTime;
    long endTime;
    int resultWidth;
    int resultHeight;
    int rotationValue;
    final int originalWidth;
    final int originalHeight;
    final long durationUs;
    int bitrate;
    int rotateRender;

    File inputFile;
    File cacheFile;

    boolean error = false;

    final ProgressTracker progressTracker = new ProgressTracker();

    private CompressInfo(CompressParam param) {
        videoPath = param.filePath;
        startTime = param.startTime;
        endTime = param.endTime;
        resultWidth = param.resultWidth;
        resultHeight = param.resultHeight;
        rotationValue = param.rotationValue;
        originalWidth = param.originalWidth;
        originalHeight = param.originalHeight;
        durationUs = param.durationUs;
        bitrate = param.bitrate;
        rotateRender = 0;
        inputFile = new File(videoPath);
        cacheFile = new File(param.cacheFilePath);
    }

    public static CompressInfo fromParam(CompressParam param) {
        CompressInfo info = new CompressInfo(param);
        info.init();
        return info;
    }

    private void init() {
        if (Build.VERSION.SDK_INT < 18 && resultHeight > resultWidth && resultWidth != originalWidth && resultHeight != originalHeight) {
            int temp = resultHeight;
            resultHeight = resultWidth;
            resultWidth = temp;
            rotationValue = 90;
            rotateRender = 270;
        } else if (Build.VERSION.SDK_INT > 20) {
            if (rotationValue == 90) {
                int temp = resultHeight;
                resultHeight = resultWidth;
                resultWidth = temp;
                rotationValue = 0;
                rotateRender = 270;
            } else if (rotationValue == 180) {
                rotateRender = 180;
                rotationValue = 0;
            } else if (rotationValue == 270) {
                int temp = resultHeight;
                resultHeight = resultWidth;
                resultWidth = temp;
                rotationValue = 0;
                rotateRender = 90;
            }
        }
    }

    boolean isSizeValid() {
        return resultWidth != 0 && resultHeight != 0;
    }

    boolean needCompress() {
        return resultWidth != originalWidth || resultHeight != originalHeight || rotateRender != 0;
    }

    void didWriteData(final boolean finish, final boolean error) {
        didWriteData(finish, error, -1);
    }

    void didWriteData(final boolean finish, final boolean error, final long presentationTimeUs) {
        progressTracker.didWriteData(finish, error, presentationTimeUs > 0 ? (float) (presentationTimeUs / (double) durationUs) : 0);
    }

    void firstWrite() {
        progressTracker.firstWrite();
    }
}
