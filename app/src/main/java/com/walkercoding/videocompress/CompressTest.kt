package com.walkercoding.videocompress

import android.content.Context
import android.os.Environment
import com.walkercoding.videocompress.videocompress.CompressParam
import com.walkercoding.videocompress.videocompress.VideoCompressor
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import java.io.File
import java.util.*

object CompressTest {

    //        private static final String TEST_VIDEO_FILE_PATH = "/storage/emulated/0/DCIM/Video/V80608-140857.mp4"; // 10s
    //    private static final String TEST_VIDEO_FILE_PATH = "/storage/emulated/0/DCIM/Video/V80209-151237.mp4"; // 1 min 122M
    private val TEST_VIDEO_FILE_PATH = "/storage/emulated/0/DCIM/Camera/VID_20180616_225210.mp4" // 1280 * 720, 74s

    private val externalStorageDirectory: File
        get() = Environment.getExternalStorageDirectory()

    fun test(context: Context) {
        val videoFile = File(TEST_VIDEO_FILE_PATH)
        val cacheFile = File(getExternalPictureFileDir(context), String.format(Locale.US, "tmp_%d.mp4", System.currentTimeMillis()))

        val compressParam = CompressParam()
        compressParam.filePath = videoFile.absolutePath
        compressParam.cacheFilePath = cacheFile.path

        compressParam.originalWidth = 1280
        compressParam.originalHeight = 720
        compressParam.durationUs = 74 * 1000 * 1000
        compressParam.resultWidth = compressParam.originalWidth / 2
        compressParam.resultHeight = compressParam.originalHeight / 2
        compressParam.bitrate = 921600

        Observable.create<Any>({
            VideoCompressor().convertVideo(compressParam)
            it.onNext(Any())
            it.onComplete()
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe()
    }

    fun getExternalPictureFileDir(context: Context): File {
        val rootDir = File(externalStorageDirectory, context.packageName)
        if (rootDir.exists() && !rootDir.isDirectory) {
            rootDir.delete()
        }

        val dir = File(externalStorageDirectory, String.format("%s/%s", context.packageName, "videoPress"))
        mkdirsIfNeed(dir)
        return dir
    }

    private fun mkdirsIfNeed(dir: File) {
        if (dir.exists() && !dir.isDirectory) {
            dir.delete()
        }

        if (!dir.exists()) {
            dir.mkdirs()
        }
    }
}
