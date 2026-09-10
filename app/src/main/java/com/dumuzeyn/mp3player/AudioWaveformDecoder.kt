package com.dumuzeyn.mp3player

import android.content.Context
import kotlin.math.abs

internal class AudioWaveformDecoder(context: Context) {
    private val decoder = AudioPcmDecoder(context)
    fun decode(uri: String, durationMs: Long, cancelled: () -> Boolean): AudioWaveform {
        val envelope = AudioWaveformAccumulator(durationMs * 1000)
        decoder.decode(uri, 0, durationMs, cancelled) { format, pcm, timeUs ->
            var frame = 0L
            while (pcm.remaining() >= format.frameBytes) {
                var peak = 0f
                repeat(format.channels) { peak = maxOf(peak, abs(format.sample(pcm))) }
                envelope.add(timeUs + frame * 1000000L / format.sampleRate, peak)
                frame++
            }
        }
        return envelope.finish()
    }
}
