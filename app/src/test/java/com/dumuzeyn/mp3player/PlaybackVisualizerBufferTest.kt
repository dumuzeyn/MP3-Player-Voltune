package com.dumuzeyn.mp3player

import android.media.AudioFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class PlaybackVisualizerBufferTest {
    @Test fun capturesPcmWithoutAdvancingTheAudioBuffer() {
        val visualizer = PlaybackVisualizerBuffer()
        visualizer.flush(48_000, 1, AudioFormat.ENCODING_PCM_16BIT)
        visualizer.setEnabled(true)
        val audio = tone(220.0)

        visualizer.handleBuffer(audio)

        assertEquals(0, audio.position())
        val bars = visualizer.snapshot(48)
        assertTrue(bars.maxOrNull()!! > 0.5f)
        assertTrue(bars.minOrNull()!! < 0.2f)
    }

    @Test fun disablingAndFlushDiscardStaleLevels() {
        val visualizer = PlaybackVisualizerBuffer()
        visualizer.flush(48_000, 1, AudioFormat.ENCODING_PCM_16BIT)
        visualizer.setEnabled(true)
        visualizer.handleBuffer(tone(220.0))
        assertTrue(visualizer.snapshot(96).any { it > 0f })

        visualizer.setEnabled(false)
        assertEquals(0f, visualizer.snapshot(1)[0], 0f)
        visualizer.setEnabled(true)
        visualizer.flush(44_100, 2, AudioFormat.ENCODING_PCM_16BIT)
        assertEquals(0f, visualizer.snapshot(1)[0], 0f)
    }

    @Test fun frequencyChangesMoveThePeakAcrossTheWholeWidth() {
        val visualizer = PlaybackVisualizerBuffer()
        visualizer.flush(48_000, 1, AudioFormat.ENCODING_PCM_16BIT)
        visualizer.setEnabled(true)
        visualizer.handleBuffer(tone(220.0))
        val low = visualizer.snapshot(96).indices.maxBy { visualizer.snapshot(96)[it] }
        visualizer.handleBuffer(tone(4_000.0))
        val high = visualizer.snapshot(96).indices.maxBy { visualizer.snapshot(96)[it] }
        assertTrue(high > low + 15)
    }

    @Test fun silenceClearsTheSpectrum() {
        val visualizer = PlaybackVisualizerBuffer()
        visualizer.flush(48_000, 1, AudioFormat.ENCODING_PCM_16BIT)
        visualizer.setEnabled(true)
        visualizer.handleBuffer(tone(220.0))
        visualizer.handleBuffer(ByteBuffer.allocate(4096))
        assertTrue(visualizer.snapshot(96).all { it == 0f })
    }

    private fun tone(frequency: Double): ByteBuffer {
        val audio = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN)
        repeat(2048) { frame ->
            audio.putShort((sin(2.0 * PI * frame * frequency / 48_000) * 24_000).toInt().toShort())
        }
        audio.flip()
        return audio
    }
}
