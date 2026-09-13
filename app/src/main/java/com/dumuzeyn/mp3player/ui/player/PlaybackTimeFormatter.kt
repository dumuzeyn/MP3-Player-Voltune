package com.dumuzeyn.mp3player.ui.player

import java.util.Locale

object PlaybackTimeFormatter {
    @JvmStatic
    fun formatMilliseconds(milliseconds: Int): String =
        formatSeconds(milliseconds.coerceAtLeast(0) / 1_000L)

    @JvmStatic
    fun formatSeconds(seconds: Long): String {
        val safeSeconds = seconds.coerceAtLeast(0L)
        return "${safeSeconds / 60}:${String.format(Locale.ROOT, "%02d", safeSeconds % 60)}"
    }
}
