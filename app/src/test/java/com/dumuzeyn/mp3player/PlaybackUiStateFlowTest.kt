package com.dumuzeyn.mp3player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PlaybackUiStateFlowTest {
    @Test
    fun stateFlowPublishesTheSameImmutableSnapshotUsedByJavaUi() {
        val state = PlaybackUiState()
        val snapshot = PlaybackSnapshot(
            listOf("track-a", "track-b"),
            "track-b",
            1,
            12_000L,
            180_000L,
            true,
            3,
            2,
            true,
            PlaybackPhase.READY,
            PauseReason.NONE,
            StopReason.NONE,
            null,
            42L,
        )

        state.updateSnapshot(snapshot)

        assertSame(snapshot, state.snapshot())
        assertSame(snapshot, state.state().value)
        assertEquals(listOf("track-a", "track-b"), state.state().value.queueMediaIds)
        assertEquals(12_000L, state.state().value.positionMs)
        assertEquals(true, state.isPlaying())
    }
}
