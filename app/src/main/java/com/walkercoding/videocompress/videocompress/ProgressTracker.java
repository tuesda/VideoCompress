package com.walkercoding.videocompress.videocompress;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;

public class ProgressTracker {

    private boolean videoConvertFirstWrite;

    private Handler mainHandler = new Handler(Looper.getMainLooper());

    void firstWrite() {
        videoConvertFirstWrite = true;
    }

    void didWriteData(final File file, final boolean last, final boolean error) {
        final boolean firstWrite = videoConvertFirstWrite;
        if (firstWrite) {
            videoConvertFirstWrite = false;
        }
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (error || last) {
//                    videoConvertQueue.remove(editInfo);
//                    startVideoConvertFromQueue();
                    // remove from convert queue, start new convert
                    Log.e("zl", "DoneOrError: " + error + " file " + file.getAbsolutePath());
                }
                if (error) {
                    // NotificationCenter.getInstance().postNotificationName(NotificationCenter.FilePreparingFailed, editInfo, file.toString());
                    // notify error
                    Log.e("zl", "FilePreparingFailed");
                } else {
                    if (firstWrite) {
                        // NotificationCenter.getInstance().postNotificationName(NotificationCenter.FilePreparingStarted, editInfo, file.toString());
                        // notify converting start
                        Log.e("zl", "FilePreparingStarted");
                    }
                    // NotificationCenter.getInstance().postNotificationName(NotificationCenter.FileNewChunkAvailable, editInfo, file.toString(), last ? file.length() : 0);
                    // notify convert progress
                    Log.e("zl", "FileNewChunkAvailable " + file.length() / 1024 + "kb");
                }
            }
        });
    }
}
