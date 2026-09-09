package com.dumuzeyn.mp3player

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import androidx.media3.common.Player
import kotlin.math.abs

/** Applies end-of-track gain independently of normalization and the system volume. */
internal class PlaybackFadeController(context: Context, private val player: Player) :
    Player.Listener, SharedPreferences.OnSharedPreferenceChangeListener, AutoCloseable {
    private val preferences = context.getSharedPreferences(PlaybackFadePolicy.PREFS, 0)
    private val handler = Handler(player.applicationLooper)
    private var baseVolume = 1f
    private var closed = false
    private val tick = Runnable { update() }

    init {
        preferences.registerOnSharedPreferenceChangeListener(this)
        player.addListener(this)
    }

    fun setBaseVolume(value: Float) {
        baseVolume = value.coerceIn(0f, 1f)
        update()
    }

    private fun update() {
        if (closed) return
        handler.removeCallbacks(tick)
        val enabled = preferences.getBoolean(PlaybackFadePolicy.ENABLED, false)
        val fadeMs = preferences.getInt(PlaybackFadePolicy.SECONDS, PlaybackFadePolicy.DEFAULT_SECONDS)
            .coerceIn(1, 12) * 1000L
        val gain = if (enabled) PlaybackFadePolicy.gain(
            player.currentPosition, player.duration, fadeMs, player.playbackParameters.speed,
        ) else 1f
        val volume = baseVolume * gain
        if (abs(player.volume - volume) > 0.0001f) player.volume = volume
        if (enabled && player.isPlaying) handler.postDelayed(tick, if (gain < 1f) 50L else 250L)
    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (events.containsAny(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_POSITION_DISCONTINUITY,
                Player.EVENT_PLAYBACK_STATE_CHANGED, Player.EVENT_IS_PLAYING_CHANGED,
                Player.EVENT_PLAYBACK_PARAMETERS_CHANGED, Player.EVENT_TIMELINE_CHANGED)) update()
    }

    override fun onSharedPreferenceChanged(preferences: SharedPreferences, key: String?) {
        handler.post { update() }
    }

    override fun close() {
        closed = true
        handler.removeCallbacksAndMessages(null)
        preferences.unregisterOnSharedPreferenceChangeListener(this)
        player.removeListener(this)
    }
}
