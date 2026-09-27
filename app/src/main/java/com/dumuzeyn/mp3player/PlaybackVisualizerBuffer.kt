package com.dumuzeyn.mp3player

import androidx.media3.exoplayer.audio.TeeAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/** A short PCM level history, fed by the playback sink without microphone access. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class PlaybackVisualizerBuffer : TeeAudioProcessor.AudioBufferSink {
    private val levels = FloatArray(96)
    private var next = 0
    private var size = 0
    private var format: AudioPcmFormat? = null
    @Volatile private var enabled = false

    fun setEnabled(value: Boolean) {
        enabled = value
        if (!value) synchronized(this) {
            levels.fill(0f)
            next = 0
            size = 0
        }
    }

    override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
        val configured = runCatching { AudioPcmFormat(sampleRateHz, channelCount, encoding) }.getOrNull()
        synchronized(this) {
            format = configured
            levels.fill(0f)
            next = 0
            size = 0
        }
    }

    override fun handleBuffer(buffer: ByteBuffer) {
        if (!enabled) return
        val current = synchronized(this) { format } ?: return
        val pcm = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        val frames = pcm.remaining() / current.frameBytes
        if (frames == 0) return
        val step = (current.sampleRate / 300).coerceAtLeast(1)
        var power = 0.0
        var sampled = 0
        for (frame in 0 until frames step step) {
            pcm.position(buffer.position() + frame * current.frameBytes)
            repeat(current.channels) {
                val sample = current.sample(pcm).toDouble()
                power += sample * sample
                sampled++
            }
        }
        val level = sqrt(power / sampled).toFloat().coerceIn(0f, 1f)
        if (!enabled) return
        synchronized(this) {
            levels[next] = level
            next = (next + 1) % levels.size
            size = (size + 1).coerceAtMost(levels.size)
        }
    }

    fun snapshot(bars: Int): FloatArray {
        require(bars > 0)
        return synchronized(this) {
            FloatArray(bars).also { result ->
                val count = minOf(size, bars)
                for (index in 0 until count) {
                    result[bars - count + index] = levels[(next - count + index + levels.size) % levels.size]
                }
            }
        }
    }

    companion object {
        val shared = PlaybackVisualizerBuffer()
    }
}
