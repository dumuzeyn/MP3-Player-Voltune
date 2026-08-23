package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class TrackTapPolicyTest {
    @Test
    public void differentTrackStartsPlayback() {
        assertEquals(TrackTapPolicy.Action.PLAY, TrackTapPolicy.action(false));
    }

    @Test
    public void currentTrackOpensPlayer() {
        assertEquals(TrackTapPolicy.Action.OPEN_PLAYER, TrackTapPolicy.action(true));
    }
}
