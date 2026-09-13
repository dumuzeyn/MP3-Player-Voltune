package com.dumuzeyn.mp3player

import java.io.File

internal class DemucsSeparator(model: File) : AutoCloseable {
    private var handle = create(model.absolutePath)
    fun window(stereo: FloatArray, progress: Progress): Array<FloatArray> {
        check(handle != 0L)
        return separate(handle, stereo, progress)
    }
    override fun close() { destroy(handle); handle = 0 }
    fun interface Progress { fun update(value: Float): Boolean }
    external fun verifyAttention(): Boolean
    private external fun create(path: String): Long
    private external fun separate(handle: Long, stereo: FloatArray, progress: Progress): Array<FloatArray>
    private external fun destroy(handle: Long)
    companion object { init { System.loadLibrary("voltune_demucs") } }
}
