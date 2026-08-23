package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import androidx.media3.common.Player;
import java.util.Arrays;
import org.junit.Test;

public class PlaybackSnapshotCodecTest {
    @Test
    public void snapshotRoundTripPreservesPlaybackState() {
        PlaybackSnapshot source = new PlaybackSnapshot(Arrays.asList("track-a", "track-b"),
                "track-b", 1, 42000L, 180000L, true, Player.STATE_READY,
                Player.REPEAT_MODE_ALL, true, PlaybackPhase.READY, PauseReason.NONE,
                StopReason.NONE, null, 1234L);

        PlaybackSnapshot restored = PlaybackSnapshotCodec.decode(
                PlaybackSnapshotCodec.encode(source));

        assertEquals(source.queueMediaIds, restored.queueMediaIds);
        assertEquals("track-b", restored.currentMediaId);
        assertEquals(1, restored.currentIndex);
        assertEquals(42000L, restored.positionMs);
        assertEquals(Player.REPEAT_MODE_ALL, restored.repeatMode);
        assertEquals(true, restored.shuffleEnabled);
    }

    @Test
    public void damagedOrOldSnapshotFailsClosed() {
        PlaybackSnapshot damaged = PlaybackSnapshotCodec.decode("not-json");
        assertEquals(-1, damaged.currentIndex);
        assertFalse(damaged.playWhenReady);

        PlaybackSnapshot old = PlaybackSnapshotCodec.decode("{\"version\":0}");
        assertEquals(-1, old.currentIndex);
    }
}
