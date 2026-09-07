package com.dumuzeyn.mp3player

import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import kotlin.math.max

/** Runs the visible player clock and stops immediately off-page or in background. */
internal class FullPlayerProgressController(
    private val host: MainActivityCore,
    private val playbackState: PlaybackStateProvider,
    private val trackChanged: Runnable,
    private val uiTick: Runnable,
) : AutoCloseable {
    private val ticker = Runnable(::tick)
    private var root: View? = null
    private var seek: SeekBar? = null
    private var elapsed: TextView? = null
    private var remaining: TextView? = null
    private var trackUri = ""
    private var active = false
    private var seekTracking = false

    fun bind(root: View, track: Track?, seek: SeekBar, elapsed: TextView, remaining: TextView) {
        this.root = root
        trackUri = track?.uri.orEmpty()
        this.seek = seek
        this.elapsed = elapsed
        this.remaining = remaining
        updateClock()
    }

    fun setSeekTracking(tracking: Boolean) {
        seekTracking = tracking
    }

    fun setActive(value: Boolean) {
        active = value
        host.uiHandler.removeCallbacks(ticker)
        if (active) {
            updateClock()
            host.uiHandler.postDelayed(ticker, TICK_DELAY_MS)
        }
    }

    private fun tick() {
        val activeRoot = root
        if (!active || activeRoot == null || activeRoot.parent == null) return
        val current = playbackState.currentTrack() ?: return
        if (trackUri != current.uri) {
            trackUri = current.uri
            trackChanged.run()
        }
        updateClock()
        uiTick.run()
        host.uiHandler.postDelayed(ticker, TICK_DELAY_MS)
    }

    private fun updateClock() {
        val track = playbackState.currentTrack() ?: return
        val activeSeek = seek ?: return
        val elapsedView = elapsed ?: return
        val remainingView = remaining ?: return
        if (seekTracking) return

        val duration = host.playbackDurationFor(track)
        val position = max(0, host.playbackPosition())
        activeSeek.max = max(1, duration)
        activeSeek.progress = position
        elapsedView.text = host.formatMs(position)
        remainingView.text = "-" + host.formatMs(max(0, duration - position))
    }

    override fun close() {
        active = false
        host.uiHandler.removeCallbacks(ticker)
        root = null
        seek = null
        elapsed = null
        remaining = null
    }

    private companion object {
        const val TICK_DELAY_MS = 250L
    }
}
