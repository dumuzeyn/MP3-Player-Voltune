package com.dumuzeyn.mp3player

object TrackTapPolicy {
    enum class Action { PLAY, OPEN_PLAYER }

    @JvmStatic
    fun action(current: Boolean): Action = if (current) Action.OPEN_PLAYER else Action.PLAY
}
