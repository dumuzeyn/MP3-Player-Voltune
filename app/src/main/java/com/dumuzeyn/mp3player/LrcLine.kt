package com.dumuzeyn.mp3player

class LrcLine(timeMs: Long, text: String?) {
    @JvmField val timeMs: Long = timeMs.coerceAtLeast(0L)
    @JvmField val text: String = text.orEmpty()
}
