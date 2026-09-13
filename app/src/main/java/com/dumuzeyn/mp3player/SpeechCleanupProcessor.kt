package com.dumuzeyn.mp3player

import android.content.Context
import java.io.File
import java.util.concurrent.CancellationException

internal class SpeechCleanupProcessor(private val context: Context) {
    fun process(clip: AudioEditClip, output: File, cancelled: () -> Boolean, progress: (Int) -> Unit) {
        val states = ArrayList<SpeechDenoiser>()
        var writer: PcmWaveWriter? = null
        var frames = emptyArray<FloatArray>()
        var used = 0
        var inputFrames = 0L
        var outputFrames = 0L
        var priming = true
        fun checkCancelled() { if (cancelled() || Thread.currentThread().isInterrupted) throw CancellationException() }
        fun processFrame() {
            checkCancelled()
            frames.forEachIndexed { i, samples -> states[i].frame(samples) }
            // RNNoise's analysis/synthesis window delays output by one 10 ms frame.
            if (priming) priming = false
            else repeat(minOf(480L, inputFrames - outputFrames).toInt()) { frame ->
                frames.forEach { checkNotNull(writer).sample(it[frame]) }
                outputFrames++
            }
        }
        try {
            AudioPcmResampler(context).decode(clip, 48000, false, cancelled) { pcm, channels ->
                checkCancelled()
                if (writer == null) {
                    writer = PcmWaveWriter(output, 48000, channels)
                    frames = Array(channels) { FloatArray(480) }
                    repeat(channels) { states.add(SpeechDenoiser()) }
                }
                while (pcm.remaining() >= channels * 4) {
                    repeat(channels) { frames[it][used] = pcm.float }
                    used++
                    inputFrames++
                    if (used == 480) { processFrame(); used = 0 }
                }
                progress((inputFrames * 100 / (clip.durationMs * 48).coerceAtLeast(1)).toInt().coerceIn(0, 99))
            }
            if (used > 0) { frames.forEach { it.fill(0f, used) }; processFrame() }
            frames.forEach { it.fill(0f) }
            processFrame()
            check(inputFrames > 0 && outputFrames == inputFrames) { "Speech cleanup duration mismatch" }
            writer?.close()
            writer = null
            progress(100)
        } catch (failure: Throwable) {
            runCatching { writer?.close() }
            writer = null
            output.delete()
            throw failure
        } finally {
            writer?.close()
            states.forEach { it.close() }
        }
    }
}
