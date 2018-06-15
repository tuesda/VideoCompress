package com.walkercoding.videocompress;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.util.Log;
import com.walkercoding.videocompress.videocompress.CompressParam;
import com.walkercoding.videocompress.videocompress.VideoCompressor;

import java.io.File;
import java.util.Locale;

public class CompressTest {

    //        private static final String TEST_VIDEO_FILE_PATH = "/storage/emulated/0/DCIM/Video/V80608-140857.mp4"; // 10s
    private static final String TEST_VIDEO_FILE_PATH = "/storage/emulated/0/DCIM/Video/V80209-151237.mp4"; // 1 min 122M

    public static void test(Context context) {
        File videoFile = new File(TEST_VIDEO_FILE_PATH);
        File cacheFile = new File(getExternalPictureFileDir(context), String.format(Locale.US, "tmp_%d.mp4", System.currentTimeMillis()));
        Log.e("zl", "cache File " + cacheFile.getAbsolutePath());

        final CompressParam editInfo = new CompressParam();
        editInfo.filePath = videoFile.getAbsolutePath();
        editInfo.cacheFilePath = cacheFile.getPath();

        editInfo.originalWidth = 1920;
        editInfo.originalHeight = 1080;
        editInfo.resultWidth = editInfo.originalWidth / 2;
        editInfo.resultHeight = editInfo.originalHeight / 2;
        editInfo.bitrate = 921600;

        new MyTask(editInfo).execute();
    }

    private static class MyTask extends AsyncTask<Void, Void, Void> {
        private final CompressParam editInfo;

        public MyTask(CompressParam editInfo) {
            this.editInfo = editInfo;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            new VideoCompressor().convertVideo(editInfo);
            return null;
        }
    }

    public static File getExternalPictureFileDir(Context context) {
        File rootDir = new File(getExternalStorageDirectory(), context.getPackageName());
        if (rootDir.exists() && !rootDir.isDirectory()) {
            rootDir.delete();
        }

        File dir = new File(getExternalStorageDirectory(), String.format("%s/%s", context.getPackageName(), "videoPress"));
        mkdirsIfNeed(dir);
        return dir;
    }

    private static File getExternalStorageDirectory() {
        return Environment.getExternalStorageDirectory();
    }

    private static void mkdirsIfNeed(@NonNull File dir) {
        if (dir.exists() && !dir.isDirectory()) {
            dir.delete();
        }

        if (!dir.exists()) {
            dir.mkdirs();
        }
    }
}
