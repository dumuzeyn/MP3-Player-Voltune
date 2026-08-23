package com.dumuzeyn.mp3player;

import androidx.media3.common.Player;

public final class RepeatModeMapper {
    private RepeatModeMapper() {
    }

    public static int toMedia3(int mode) {
        return mode == 1 ? Player.REPEAT_MODE_ONE
                : mode == 2 ? Player.REPEAT_MODE_ALL : Player.REPEAT_MODE_OFF;
    }

    public static int fromMedia3(int mode) {
        return mode == Player.REPEAT_MODE_ONE ? 1
                : mode == Player.REPEAT_MODE_ALL ? 2 : 0;
    }
}
