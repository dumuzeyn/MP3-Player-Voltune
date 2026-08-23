package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PlaybackStatisticsPolicyTest {
    @Test
    public void longTrackRequiresThirtySeconds() {
        assertFalse(PlaybackStatisticsPolicy.countsAsPlay(29_999L, 180_000L));
        assertTrue(PlaybackStatisticsPolicy.countsAsPlay(30_000L, 180_000L));
    }

    @Test
    public void shortTrackRequiresMeaningfulFraction() {
        assertFalse(PlaybackStatisticsPolicy.countsAsPlay(9_999L, 20_000L));
        assertTrue(PlaybackStatisticsPolicy.countsAsPlay(10_000L, 20_000L));
    }

    @Test
    public void onlyEarlyUserTransitionCountsAsSkip() {
        assertTrue(PlaybackStatisticsPolicy.countsAsSkip(4_000L, 180_000L, true));
        assertFalse(PlaybackStatisticsPolicy.countsAsSkip(4_000L, 180_000L, false));
        assertFalse(PlaybackStatisticsPolicy.countsAsSkip(12_000L, 180_000L, true));
    }
}
