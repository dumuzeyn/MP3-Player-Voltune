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
        assertTrue(visualizer.snapshot(4).last() in 0.49f..0.51f)
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
}
