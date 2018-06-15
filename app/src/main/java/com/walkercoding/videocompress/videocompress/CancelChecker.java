package com.walkercoding.videocompress.videocompress;

public class CancelChecker {
    private final Object videoConvertSync = new Object();
    private boolean cancelCurrentVideoConversion = false;

    public void checkConversionCanceled() throws Exception {
        boolean cancelConversion;
        synchronized (videoConvertSync) {
            cancelConversion = cancelCurrentVideoConversion;
        }
        if (cancelConversion) {
            throw new RuntimeException("canceled conversion");
        }
    }

    public void cancelVideoConvert(CompressParam editInfo) {
        if (editInfo == null) {
            cancelInternal();
        } else {
            if (!isConvertQueueEmpty()) {
                if (isConvertingVideo(editInfo)) {
                    cancelInternal();
                } else {
                    removeFromConvertQueue(editInfo);
                }
            }
        }
    }

    private boolean isConvertQueueEmpty() {
        return false;
    }

    private boolean isConvertingVideo(CompressParam info) {
        return true;
    }

    private void removeFromConvertQueue(CompressParam info) {

    }

    private void cancelInternal() {
        synchronized (videoConvertSync) {
            cancelCurrentVideoConversion = true;
        }
    }

    private void startConvert() {
        synchronized (videoConvertSync) {
            cancelCurrentVideoConversion = false;
        }
    }
}
