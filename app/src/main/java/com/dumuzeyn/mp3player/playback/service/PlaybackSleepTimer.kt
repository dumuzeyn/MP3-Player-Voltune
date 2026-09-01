package com.dumuzeyn.mp3player.playback.service

import android.content.Context
import com.dumuzeyn.mp3player.VoltuneLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Owns sleep-timer persistence and scheduling for background playback. */
class PlaybackSleepTimer(
    context: Context,
    private val listener: Listener,
) : AutoCloseable {
    fun interface Listener {
        fun onTimerExpired()
    }

    private val context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var expirationJob: Job? = null
    var endsAt: Long = 0L
        private set

    fun start(delayMs: Long) {
        endsAt = System.currentTimeMillis() + delayMs.coerceAtLeast(1_000L)
        persist()
        schedule()
        VoltuneLog.info("sleep_timer_started")
    }

    fun restore() {
        endsAt = readEndsAt(context)
        if (endsAt <= 0L) return
        if (endsAt <= System.currentTimeMillis()) {
            endsAt = 0L
            persist()
            return
        }
        schedule()
        VoltuneLog.info("sleep_timer_restored")
    }

    fun cancel() {
        endsAt = 0L
        expirationJob?.cancel()
        expirationJob = null
        persist()
        VoltuneLog.info("sleep_timer_cancelled")
    }

    override fun close() {
        scope.cancel()
        expirationJob = null
    }

    private fun schedule() {
        expirationJob?.cancel()
        if (endsAt <= 0L) return
        expirationJob = scope.launch {
            delay((endsAt - System.currentTimeMillis()).coerceAtLeast(1_000L))
            expireOrReschedule()
        }
    }

    private fun expireOrReschedule() {
        if (endsAt <= 0L) return
        if (System.currentTimeMillis() < endsAt) {
            schedule()
            return
        }
        VoltuneLog.info("sleep_timer_expired")
        endsAt = 0L
        persist()
        listener.onTimerExpired()
    }

    private fun persist() {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(ENDS_AT, endsAt)
            .apply()
    }

    companion object {
        private const val PREFS = "player_sleep_timer"
        private const val ENDS_AT = "endsAt"

        @JvmStatic
        fun readEndsAt(context: Context): Long = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(ENDS_AT, 0L)
    }
}
