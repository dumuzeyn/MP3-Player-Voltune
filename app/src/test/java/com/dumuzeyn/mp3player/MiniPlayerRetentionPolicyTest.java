package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MiniPlayerRetentionPolicyTest {
    @Test
    public void playingSessionNeverExpires() {
        assertFalse(MiniPlayerRetentionPolicy.isExpired(
                true, 1L, 1L, 10_000_000L, 1L));
    }

    @Test
    public void playbackRemainsActiveWhilePreparingOrPlaying() {
        assertTrue(MiniPlayerRetentionPolicy.isPlaybackActive(
                true, PlaybackPhase.BUFFERING, StopReason.NONE));
        assertTrue(MiniPlayerRetentionPolicy.isPlaybackActive(
                true, PlaybackPhase.READY, StopReason.NONE));
    }

    @Test
    public void countdownStartsAfterPauseOrQueueEnd() {
        assertFalse(MiniPlayerRetentionPolicy.isPlaybackActive(
                false, PlaybackPhase.READY, StopReason.NONE));
        assertFalse(MiniPlayerRetentionPolicy.isPlaybackActive(
                true, PlaybackPhase.ENDED, StopReason.QUEUE_ENDED));
    }

    @Test
    public void pausedSessionExpiresFromFirstInactiveMoment() {
        long hour = 60L * 60L * 1000L;
        assertFalse(MiniPlayerRetentionPolicy.isExpired(
                false, hour, 5L * hour, 2L * hour, 2L * hour));
        assertTrue(MiniPlayerRetentionPolicy.isExpired(
                false, hour, 5L * hour, 4L * hour, 2L * hour));
    }

    @Test
    public void oldStateFallsBackToLegacySaveTime() {
        assertFalse(MiniPlayerRetentionPolicy.isExpired(
                false, 0L, 1_000L, 1_500L, 1_000L));
        assertTrue(MiniPlayerRetentionPolicy.isExpired(
                false, 0L, 1_000L, 2_500L, 1_000L));
    }

    @Test
    public void twoHourSettingExpiresAnEightHourPause() {
        long hour = 60L * 60L * 1000L;
        assertTrue(MiniPlayerRetentionPolicy.isExpired(
                false, hour, 9L * hour, 9L * hour, 2L * hour));
    }
}
