package com.walkercoding.videocompress.videocompress;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.Locale;

public class ProgressTracker {

    private boolean videoConvertFirstWrite;

    private Handler mainHandler = new Handler(Looper.getMainLooper());

    void firstWrite() {
        videoConvertFirstWrite = true;
    }

    void didWriteData(final boolean finish, final boolean error, final float fraction) {
        final boolean firstWrite = videoConvertFirstWrite;
        if (firstWrite) {
            videoConvertFirstWrite = false;
        }
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (error || finish) {
                    Log.e("zl", error ? "Error!" : "Done!");
                } else {
                    if (firstWrite) {
                        Log.e("zl", "Start!");
                    } else {
                        Log.e("zl", String.format(Locale.US, "Progress:%2d%%", (int) (fraction * 100)));
                    }
                }
            }
        });
    }
}
