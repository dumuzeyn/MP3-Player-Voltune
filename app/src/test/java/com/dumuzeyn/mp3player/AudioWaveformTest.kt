package com.dumuzeyn.mp3player

import org.junit.Assert.*
import org.junit.Test

class AudioWaveformTest {
    @Test fun peaksUseTimestampsAndPreserveSilence() {
        val accumulator = AudioWaveformAccumulator(4000, 4)
        accumulator.add(1000, -0.2f)
        accumulator.add(1500, 0.4f)
        accumulator.add(3000, 0.8f)
        assertArrayEquals(floatArrayOf(0f, 0.4f, 0f, 0.8f), accumulator.finish().peaks, 0f)
    }

    @Test fun invalidAndOutsideSamplesDoNotPolluteEnvelope() {
        val accumulator = AudioWaveformAccumulator(2000, 2)
        accumulator.add(-1, 1f)
        accumulator.add(2000, 1f)
        accumulator.add(0, Float.NaN)
        accumulator.add(0, Float.POSITIVE_INFINITY)
        accumulator.add(1000, 2f)
        assertArrayEquals(floatArrayOf(0f, 1f), accumulator.finish().peaks, 0f)
    }

    @Test fun renderedRangeUsesMaximumIncludingNarrowTransients() {
        val wave = AudioWaveform(4000, floatArrayOf(0.1f, 0.9f, 0.4f, 0.2f))
        assertEquals(0.9f, wave.peakBetween(0, 3000), 0f)
        assertEquals(0.4f, wave.peakBetween(2000, 3000), 0f)
        assertEquals(0.2f, wave.peakBetween(4000, 4000), 0f)
    }

    @Test fun selectionCannotCrossOrLeaveSource() {
        val selection = AudioWaveformSelection(6000, 1000, 5000)
        selection.start(-100)
        selection.end(9000)
        assertEquals(0, selection.startMs)
        assertEquals(6000, selection.endMs)
        selection.start(6000)
        assertEquals(5999, selection.startMs)
        selection.end(0)
        assertEquals(6000, selection.endMs)
    }

    @Test fun oneMillisecondClipKeepsValidBounds() {
        val selection = AudioWaveformSelection(1, 0, 1)
        selection.start(1)
        selection.end(0)
        assertEquals(0, selection.startMs)
        assertEquals(1, selection.endMs)
    }

    @Test fun resultIsIndependentOfFurtherAccumulation() {
        val accumulator = AudioWaveformAccumulator(1000, 1)
        val snapshot = accumulator.finish()
        accumulator.add(0, 1f)
        assertEquals(0f, snapshot.peaks.single(), 0f)
    }
}
