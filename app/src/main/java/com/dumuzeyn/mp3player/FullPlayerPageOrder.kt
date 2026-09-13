package com.dumuzeyn.mp3player

object FullPlayerPageOrder {
    const val PLAYER = 0
    const val LYRICS = 1
    const val QUEUE = 2

    @JvmStatic
    fun afterRightSwipe(position: Int): Int = position.coerceAtLeast(PLAYER) -
        if (position > PLAYER) 1 else 0

    @JvmStatic
    fun afterLeftSwipe(position: Int): Int = (position + 1).coerceAtMost(QUEUE)
}
