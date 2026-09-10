package com.dumuzeyn.mp3player

import org.junit.Assert.*
import org.junit.Test

class AudioEditProjectTest {
    private fun clip(id: String = "a", lane: Int = 0, offset: Long = 0) = AudioEditClip(
        id, "content://audio/$id", id, 10_000, 1000, 9000, lane, offset,
    )

    @Test fun splitKeepsSourceAndTimelineContiguous() {
        val source = AudioEditProject(listOf(clip(offset = 2000)))
        val split = source.split("a", 3500)
        assertEquals(10_000L, split.durationMs)
        assertEquals(listOf(1000L, 3500L), split.clips.map { it.startMs })
        assertEquals(listOf(3500L, 9000L), split.clips.map { it.endMs })
        assertEquals(listOf(2000L, 4500L), split.clips.map { it.offsetMs })
        assertEquals(1, source.clips.size)
    }

    @Test fun removeRangeClosesGapOnlyOnAffectedLane() {
        val source = AudioEditProject(listOf(clip(), clip("b", offset = 8000), clip("c", lane = 1)))
        val result = source.removeRange("a", 3000, 6000)
        assertEquals(listOf(1000L, 6000L), result.clips.take(2).map { it.startMs })
        assertEquals(listOf(0L, 2000L), result.clips.take(2).map { it.offsetMs })
        assertEquals(5000L, result.clips.first { it.id == "b" }.offsetMs)
        assertEquals(0L, result.clips.first { it.id == "c" }.offsetMs)
    }

    @Test fun removeEntireClipAndJoinRetainsOrder() {
        val source = AudioEditProject(listOf(clip(), clip("b", lane = 1, offset = 4000)))
        val result = source.removeRange("a", 1000, 9000).concatenate()
        assertEquals(listOf("b"), result.clips.map { it.id })
        assertEquals(0L, result.clips.single().offsetMs)
        assertEquals(0, result.clips.single().lane)
    }

    @Test fun concatenationPreservesEditsAndVolume() {
        val result = AudioEditProject(listOf(clip("b", lane = 2).copy(gain = 0.25f), clip(lane = 1)))
            .concatenate()
        assertEquals(16000L, result.durationMs)
        assertEquals(listOf(0L, 8000L), result.clips.map { it.offsetMs })
        assertEquals(0.25f, result.clips.last().gain)
    }

    @Test fun draftRoundTripPreservesAllEditingData() {
        val source = AudioEditProject(listOf(clip(lane = 3, offset = 9000).copy(gain = 0.33f)))
        assertEquals(source, AudioEditStore.decode(AudioEditStore.encode(source)))
    }

    @Test fun rejectOverlapsButAllowTouchingAndOtherLanes() {
        assertThrows(IllegalArgumentException::class.java) {
            AudioEditProject(listOf(clip(), clip("b", offset = 7999)))
        }
        assertEquals(3, AudioEditProject(listOf(clip(), clip("b", offset = 8000), clip("c", lane = 1))).clips.size)
    }

    @Test fun rejectInvalidTrimAndSplitBoundaries() {
        val source = AudioEditProject(listOf(clip()))
        for (position in listOf(0L, 1000L, 9000L, 10000L)) {
            assertThrows(IllegalArgumentException::class.java) { source.split("a", position) }
        }
        assertThrows(IllegalArgumentException::class.java) { clip().copy(endMs = 10001) }
        assertThrows(IllegalArgumentException::class.java) { clip().copy(gain = Float.NaN) }
    }

    @Test fun appendUsesEndOfChosenLane() {
        val source = AudioEditProject(listOf(clip(), clip("b", lane = 1, offset = 500)))
        val result = source.append(clip("c"), 1)
        assertEquals(8500L, result.clips.last().offsetMs)
        assertEquals(1, result.clips.last().lane)
    }
}
