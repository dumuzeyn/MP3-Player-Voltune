package com.dumuzeyn.mp3player

import kotlin.math.abs

internal data class AudioWaveform(val durationUs: Long, val peaks: FloatArray) {
    fun peakBetween(fromUs: Long, toUs: Long): Float {
        val first = (fromUs.coerceAtLeast(0) * peaks.size / durationUs).toInt().coerceIn(peaks.indices)
        val last = ((toUs.coerceAtLeast(0) * peaks.size - 1) / durationUs).toInt().coerceIn(peaks.indices)
        var peak = 0f
        for (index in first..maxOf(first, last)) peak = maxOf(peak, peaks[index])
        return peak
    }
}

/** Fixed-size peak envelope, indexed by decoded timestamps rather than packet sizes. */
internal class AudioWaveformAccumulator(private val durationUs: Long, buckets: Int = 2048) {
    private val peaks = FloatArray(buckets)
    init { require(durationUs > 0 && buckets in 1..8192) }

    fun add(timeUs: Long, amplitude: Float) {
        if (timeUs !in 0 until durationUs || !amplitude.isFinite()) return
        val index = (timeUs * peaks.size / durationUs).toInt()
        peaks[index] = maxOf(peaks[index], abs(amplitude).coerceAtMost(1f))
    }

    fun finish() = AudioWaveform(durationUs, peaks.copyOf())
}

internal class AudioWaveformSelection(val durationMs: Long, startMs: Long, endMs: Long) {
    var startMs = startMs
        private set
    var endMs = endMs
        private set
    init { require(durationMs > 0 && startMs >= 0 && endMs > startMs && endMs <= durationMs) }

    fun start(value: Long) { startMs = value.coerceIn(0, endMs - 1) }
    fun end(value: Long) { endMs = value.coerceIn(startMs + 1, durationMs) }
}
