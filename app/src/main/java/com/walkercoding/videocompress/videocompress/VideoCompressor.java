package com.walkercoding.videocompress.videocompress;

import java.util.concurrent.atomic.AtomicBoolean;

public class VideoCompressor {

    private static final AtomicBoolean IS_CONVERTING = new AtomicBoolean(false);

    public boolean convertVideo(final CompressParam compressParams) {
        final CompressInfo compressInfo = CompressInfo.fromParam(compressParams);

        final boolean isPreviousConverting = IS_CONVERTING.getAndSet(true);
        if (!compressInfo.inputFile.canRead() || isPreviousConverting) {
            compressInfo.didWriteData(true, true);
            IS_CONVERTING.set(false);
            return false;
        }
        compressInfo.firstWrite();
        if (compressInfo.isSizeValid()) {
            final long start = System.currentTimeMillis();
            new ContainerConverter().convert(compressInfo);
            LogUtils.e("compress time = " + (System.currentTimeMillis() - start));
        } else {
            IS_CONVERTING.set(false);
            compressInfo.didWriteData(true, true);
            return false;
        }
        IS_CONVERTING.set(false);
        compressInfo.didWriteData(true, compressInfo.error);
        return true;
    }
}
