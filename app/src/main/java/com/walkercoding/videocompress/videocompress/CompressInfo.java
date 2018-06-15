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
    int originalWidth;
    int originalHeight;
    int bitrate;
    int rotateRender;

    File inputFile;
    File cacheFile;

    boolean error = false;

    final ProgressTracker progressTracker = new ProgressTracker();

    private CompressInfo() {
    }

    public static CompressInfo fromParam(CompressParam param) {
        CompressInfo info = new CompressInfo();
        info.videoPath = param.filePath;
        info.startTime = param.startTime;
        info.endTime = param.endTime;
        info.resultWidth = param.resultWidth;
        info.resultHeight = param.resultHeight;
        info.rotationValue = param.rotationValue;
        info.originalWidth = param.originalWidth;
        info.originalHeight = param.originalHeight;
        info.bitrate = param.bitrate;
        info.rotateRender = 0;
        info.inputFile = new File(info.videoPath);
        info.cacheFile = new File(param.cacheFilePath);

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

    void didWriteData(final boolean last, final boolean error) {
        progressTracker.didWriteData(cacheFile, last, error);
    }

    void firstWrite() {
        progressTracker.firstWrite();
    }
}
