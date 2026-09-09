package com.dumuzeyn.mp3player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackFadePolicyTest {
    @Test fun fullVolumeBeforeFadeAndSilenceAtEnd() {
        assertEquals(1f, PlaybackFadePolicy.gain(6_000, 10_000, 3_000, 1f), 0f)
        assertEquals(1f, PlaybackFadePolicy.gain(7_000, 10_000, 3_000, 1f), 0f)
        assertEquals(0.5f, PlaybackFadePolicy.gain(8_500, 10_000, 3_000, 1f), 0.001f)
        assertEquals(0f, PlaybackFadePolicy.gain(10_000, 10_000, 3_000, 1f), 0f)
    }

    @Test fun realTimeFadeDurationAccountsForPlaybackSpeed() {
        assertEquals(0.5f, PlaybackFadePolicy.gain(17_000, 20_000, 3_000, 2f), 0.001f)
        assertEquals(0.5f, PlaybackFadePolicy.gain(19_250, 20_000, 3_000, 0.5f), 0.001f)
    }

    @Test fun shortTracksNeverFadeForMoreThanHalfTheirDuration() {
        assertEquals(1f, PlaybackFadePolicy.gain(0, 2_000, 12_000, 1f), 0f)
        assertEquals(0.5f, PlaybackFadePolicy.gain(1_500, 2_000, 12_000, 1f), 0.001f)
    }

    @Test fun unknownOrInvalidTimelinesStayAtFullVolume() {
        assertEquals(1f, PlaybackFadePolicy.gain(100, -1, 3_000, 1f), 0f)
        assertEquals(1f, PlaybackFadePolicy.gain(-1, 10_000, 3_000, 1f), 0f)
        assertEquals(1f, PlaybackFadePolicy.gain(9_000, 10_000, 0, 1f), 0f)
        assertTrue(PlaybackFadePolicy.gain(20_000, 10_000, 3_000, 1f) in 0f..1f)
    }
}
