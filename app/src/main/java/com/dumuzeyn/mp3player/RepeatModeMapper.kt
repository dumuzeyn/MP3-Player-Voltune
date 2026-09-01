package com.dumuzeyn.mp3player

import androidx.media3.common.Player

object RepeatModeMapper {
    @JvmStatic
    fun toMedia3(mode: Int): Int = when (mode) {
        1 -> Player.REPEAT_MODE_ONE
        2 -> Player.REPEAT_MODE_ALL
        else -> Player.REPEAT_MODE_OFF
    }

    @JvmStatic
    fun fromMedia3(mode: Int): Int = when (mode) {
        Player.REPEAT_MODE_ONE -> 1
        Player.REPEAT_MODE_ALL -> 2
        else -> 0
    }
}
