package com.walkercoding.videocompress

import android.content.Context
import android.os.Environment
import java.io.File

object Utils {

    private val externalStorageDirectory: File
        get() = Environment.getExternalStorageDirectory()

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