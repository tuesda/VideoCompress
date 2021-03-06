package com.walkercoding.videocompress.videocompress

import io.reactivex.Observable
import io.reactivex.ObservableEmitter
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers

object VideoCompressor {

    private var compressingParam: CompressParam? = null
    private var startTime = 0L

    private val pendingList: MutableList<CompressMeta> = ArrayList()

    fun compress(param: CompressParam): Observable<Float> {
        return Observable.create<CompressParam>({
            pendingList.add(CompressMeta(param, it))
            startNewCompress()
        }).flatMap { compressInternal(it) }
    }

    fun cancel(param: CompressParam) {
        val removeList: MutableList<CompressMeta> = ArrayList()
        pendingList.forEach {
            if (it.compressParam == param) {
                removeList.add(it)
            }
        }
        pendingList.removeAll(removeList)
        if (compressingParam == param) {
            CancelChecker.cancel()
        }
    }

    private fun startNewCompress() {
        if (compressingParam == null && !pendingList.isEmpty()) {
            val meta = pendingList.removeAt(0)
            meta.emitter.onNext(meta.compressParam)
            meta.emitter.onComplete()
        }
    }

    private fun compressInternal(param: CompressParam): Observable<Float> {
        return Observable.create<Float>(createBlock@{
            val listener = CompressListener(it)
            val compressInfo = CompressInfo.fromParam(param, listener)
            if (!compressInfo.inputFile.canRead()) {
                listener.onError(IllegalArgumentException("Input file can not read, path ${compressInfo.inputFile.absoluteFile}"))
                return@createBlock
            }
            if (!compressInfo.isSizeValid) {
                listener.onError(IllegalArgumentException("Result size not valid! width=${compressInfo.resultWidth}, height=${compressInfo.resultHeight}"))
                return@createBlock
            }
            ContainerConverter.convert(compressInfo)
            if (compressInfo.error) {
                listener.onError(IllegalStateException("Error occurs in compress"))
            } else {
                listener.onDone()
            }
        }).subscribeOn(Schedulers.io())
                .doOnSubscribe {
                    compressingParam = param
                    startTime = System.currentTimeMillis()
                    CancelChecker.reset()
                }
                .observeOn(AndroidSchedulers.mainThread())
                .doOnTerminate {
                    compressingParam = null
                    VideoCompressor.startNewCompress()
                }
    }

    private class CompressListener(val emitter: ObservableEmitter<Float>) : Listener {

        override fun onDone() {
            emitter.onComplete()
        }

        override fun onError(throwable: Throwable) {
            emitter.onError(throwable)
        }

        override fun onProgress(fraction: Float) {
            emitter.onNext(fraction)
        }
    }

    private class CompressMeta(val compressParam: CompressParam, val emitter: ObservableEmitter<CompressParam>)
}
