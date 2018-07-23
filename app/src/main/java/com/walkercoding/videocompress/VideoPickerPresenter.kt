package com.walkercoding.videocompress

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.database.Cursor
import android.provider.MediaStore
import android.util.Log
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import java.io.IOException

class VideoPickerPresenter(private val context: Activity) {

    internal fun start() {
        val i = Intent(Intent.ACTION_GET_CONTENT)
        i.type = "video/*"
        context.startActivityForResult(i, REQUEST_FOR_VIDEO)
    }

    internal fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK && requestCode == REQUEST_FOR_VIDEO) {
            data?.apply {
                Single.create<String> {
                    @SuppressLint("Recycle")
                    val path = context.contentResolver.query(this.data, arrayOf(MediaStore.Images.Media.DATA), null, null, null)
                            ?.takeIf { it.moveToFirst() }
                            ?.use { getString(getColumnIndex(MediaStore.Images.Media.DATA)) }
                            ?: ""
                    it.onSuccess(path)
                }
                        .toObservable()
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe {
                            Log.i("zl", "result $it")
                        }
            }
        }
    }

    private inline fun <R> Cursor.use(block: Cursor.() -> R): R {
        return block(this).also {
            try {
                close()
            } catch (ignored: IOException) {
            }
        }
    }

    companion object {
        const val REQUEST_FOR_VIDEO = 0
    }
}