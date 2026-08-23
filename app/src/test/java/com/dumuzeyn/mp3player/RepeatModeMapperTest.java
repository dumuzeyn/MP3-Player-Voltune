package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertEquals;
import androidx.media3.common.Player;
import org.junit.Test;

public class RepeatModeMapperTest {
    @Test
    public void mapsAllVoltuneRepeatModesExactly() {
        assertEquals(Player.REPEAT_MODE_OFF, RepeatModeMapper.toMedia3(0));
        assertEquals(Player.REPEAT_MODE_ONE, RepeatModeMapper.toMedia3(1));
        assertEquals(Player.REPEAT_MODE_ALL, RepeatModeMapper.toMedia3(2));
        assertEquals(0, RepeatModeMapper.fromMedia3(Player.REPEAT_MODE_OFF));
        assertEquals(1, RepeatModeMapper.fromMedia3(Player.REPEAT_MODE_ONE));
        assertEquals(2, RepeatModeMapper.fromMedia3(Player.REPEAT_MODE_ALL));
    }
}
