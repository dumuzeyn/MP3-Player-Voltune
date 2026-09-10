package com.dumuzeyn.mp3player

/** One RNNoise recurrent state per channel, owned and released by the processing worker. */
internal class SpeechDenoiser : AutoCloseable {
    private var handle = create()
    fun frame(samples: FloatArray) {
        check(handle != 0L)
        require(samples.size == 480)
        process(handle, samples)
    }
    override fun close() { destroy(handle); handle = 0 }
    private external fun create(): Long
    private external fun process(handle: Long, samples: FloatArray)
    private external fun destroy(handle: Long)
    companion object { init { System.loadLibrary("voltune_audio") } }
}
