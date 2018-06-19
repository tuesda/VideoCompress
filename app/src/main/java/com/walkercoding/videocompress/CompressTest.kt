package com.walkercoding.videocompress

import android.content.Context
import android.os.Environment
import android.util.Log
import com.walkercoding.videocompress.videocompress.CompressParam
import com.walkercoding.videocompress.videocompress.VideoCompressor
import java.io.File
import java.util.*

object CompressTest {

    private class Case(val inputPath: String, val width: Int, val height: Int, val durationUs: Long)

    private val MEIZU_1 = Case("/storage/emulated/0/DCIM/Video/V80608-140857.mp4", 1080, 1920, 10 * 1000 * 1000)
    private val MEIZU_2 = Case("/storage/emulated/0/DCIM/Video/V80209-151237.mp4", 1920, 1080, 61 * 1000 * 1000)
    private val ONEPLUS_1 = Case("/storage/emulated/0/DCIM/Camera/VID_20180616_225210.mp4", 1280, 720, 74 * 1000 * 1000)

    private fun getCompressParam(context: Context, case: Case): CompressParam {
        val videoFile = File(case.inputPath)
        val cacheFile = File(getExternalPictureFileDir(context), String.format(Locale.US, "tmp_%d.mp4", System.currentTimeMillis()))

        val compressParam = CompressParam()
        compressParam.filePath = videoFile.absolutePath
        compressParam.cacheFilePath = cacheFile.path

        compressParam.originalWidth = case.width
        compressParam.originalHeight = case.height
        compressParam.durationUs = case.durationUs
        compressParam.resultWidth = compressParam.originalWidth / 2
        compressParam.resultHeight = compressParam.originalHeight / 2
        compressParam.bitrate = 921600

        return compressParam
    }

    private val externalStorageDirectory: File
        get() = Environment.getExternalStorageDirectory()

    fun test(context: Context) {
        compress(getCompressParam(context, MEIZU_1))
        compress(getCompressParam(context, MEIZU_2))
    }

    private fun compress(param: CompressParam) {
        VideoCompressor.compress(param)
                .doOnSubscribe { Log.e("zl", "Start path ${param.filePath}") }
//                .doOnNext { Log.e("zl", "progress $it path ${param.filePath}") }
                .doOnError { Log.e("zl", "Error $it path ${param.filePath}") }
                .doOnComplete { Log.e("zl", "Done ${param.filePath}") }
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
