package com.dumuzeyn.mp3player

import android.os.Bundle
import androidx.media3.session.SessionCommand

object Media3Commands {
    const val TIMER_START = "com.dumuzeyn.mp3player.media3.TIMER_START"
    const val TIMER_CANCEL = "com.dumuzeyn.mp3player.media3.TIMER_CANCEL"
    const val AUDIO_EFFECTS = "com.dumuzeyn.mp3player.media3.AUDIO_EFFECTS"
    const val CLEAR_QUEUE = "com.dumuzeyn.mp3player.media3.CLEAR_QUEUE"
    const val DIAGNOSTIC_SNAPSHOT = "com.dumuzeyn.mp3player.media3.DIAGNOSTIC_SNAPSHOT"
    const val ARG_TIMER_MS = "timerMs"

    @JvmField val TIMER_START_COMMAND = SessionCommand(TIMER_START, Bundle.EMPTY)
    @JvmField val TIMER_CANCEL_COMMAND = SessionCommand(TIMER_CANCEL, Bundle.EMPTY)
    @JvmField val AUDIO_EFFECTS_COMMAND = SessionCommand(AUDIO_EFFECTS, Bundle.EMPTY)
    @JvmField val CLEAR_QUEUE_COMMAND = SessionCommand(CLEAR_QUEUE, Bundle.EMPTY)
    @JvmField val DIAGNOSTIC_SNAPSHOT_COMMAND = SessionCommand(DIAGNOSTIC_SNAPSHOT, Bundle.EMPTY)
}
