package com.dumuzeyn.mp3player

import android.media.AudioFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackVisualizerBufferTest {
    @Test fun capturesPcmWithoutAdvancingTheAudioBuffer() {
        val visualizer = PlaybackVisualizerBuffer()
        visualizer.flush(48_000, 1, AudioFormat.ENCODING_PCM_16BIT)
        visualizer.setEnabled(true)
        val audio = ByteBuffer.allocate(960).order(ByteOrder.LITTLE_ENDIAN)
        repeat(480) { audio.putShort(16_384) }
        audio.flip()

        visualizer.handleBuffer(audio)

        assertEquals(0, audio.position())
        val bars = visualizer.snapshot(48)
        assertTrue(bars.all { it in 0.49f..0.51f })
    }

    @Test fun disablingAndFlushDiscardStaleLevels() {
        val visualizer = PlaybackVisualizerBuffer()
        visualizer.flush(48_000, 1, AudioFormat.ENCODING_PCM_16BIT)
        visualizer.setEnabled(true)
        visualizer.handleBuffer(ByteBuffer.wrap(byteArrayOf(0, 64)))
        assertTrue(visualizer.snapshot(1)[0] > 0f)

        visualizer.setEnabled(false)
        assertEquals(0f, visualizer.snapshot(1)[0], 0f)
        visualizer.setEnabled(true)
        visualizer.flush(44_100, 2, AudioFormat.ENCODING_PCM_16BIT)
        assertEquals(0f, visualizer.snapshot(1)[0], 0f)
    }

    @Test fun eachNewPcmBufferUpdatesTheWholeWidth() {
        val visualizer = PlaybackVisualizerBuffer()
        visualizer.flush(48_000, 1, AudioFormat.ENCODING_PCM_16BIT)
        visualizer.setEnabled(true)
        val quiet = ByteBuffer.allocate(960).order(ByteOrder.LITTLE_ENDIAN)
        repeat(480) { quiet.putShort(2_048) }
        quiet.flip()
        visualizer.handleBuffer(quiet)
        assertTrue(visualizer.snapshot(48).all { it in 0.06f..0.07f })

        val loud = ByteBuffer.allocate(960).order(ByteOrder.LITTLE_ENDIAN)
        repeat(480) { loud.putShort(16_384) }
        loud.flip()
        visualizer.handleBuffer(loud)
        assertTrue(visualizer.snapshot(48).all { it in 0.49f..0.51f })
    }

    @Test fun differentPartsOfTheSameBufferProduceDifferentBarHeights() {
        val visualizer = PlaybackVisualizerBuffer()
        visualizer.flush(48_000, 1, AudioFormat.ENCODING_PCM_16BIT)
        visualizer.setEnabled(true)
        val audio = ByteBuffer.allocate(960).order(ByteOrder.LITTLE_ENDIAN)
        repeat(240) { audio.putShort(16_384) }
        repeat(240) { audio.putShort(2_048) }
        audio.flip()

        visualizer.handleBuffer(audio)

        val bars = visualizer.snapshot(48)
        assertTrue(bars.take(24).all { it > 0.49f })
        assertTrue(bars.drop(24).all { it < 0.07f })
    }
}
