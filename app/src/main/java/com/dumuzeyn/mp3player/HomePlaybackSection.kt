package com.dumuzeyn.mp3player

import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Collections

/** Playback-dependent Home content kept separate from cached library sections. */
internal class HomePlaybackSection(private val host: MainActivityCore) : LinearLayout(host) {
    private val cover: ImageView
    private val title: TextView
    private val duration: TextView
    private val waveform: WaveformView
    private val play: Button
    private var staticTrackKeys: Set<String> = emptySet()
    private var boundTrack: Track? = null

    init {
        orientation = VERTICAL

        val heading = host.uiFactory.text(
            host.tr("Continue listening", "Продолжить прослушивание"),
            18,
            true,
        ).apply { setPadding(0, host.dp(14), 0, host.dp(4)) }
        addView(heading, LayoutParams(-1, host.dp(50)))

        val container = FrameLayout(host)
        val row = LinearLayout(host).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(host.dp(8), host.dp(4), host.dp(8), host.dp(4))
            host.uiFactory.applyCardStyle(this, host.appearanceState.songCardOpacity)
        }

        cover = host.uiFactory.coverView()
        row.addView(cover, host.uiFactory.square(52))

        val textColumn = LinearLayout(host).apply {
            orientation = VERTICAL
            setPadding(host.dp(10), 0, host.dp(6), 0)
        }
        title = host.uiFactory.text("", 16, true).apply {
            setTextColor(host.primaryText)
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
        }
        textColumn.addView(title)

        val metaRow = LinearLayout(host).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        waveform = WaveformView(host, "", host.purpleSoft, host.yellow, false).apply {
            minimumHeight = host.dp(28)
            setPadding(0, host.dp(3), 0, host.dp(3))
        }
        metaRow.addView(waveform, LayoutParams(0, host.dp(26), 1f))
        duration = host.uiFactory.text("", 12, false).apply {
            gravity = Gravity.CENTER
            setTextColor(host.secondaryText)
        }
        metaRow.addView(duration, LayoutParams(host.dp(46), host.dp(26)))
        textColumn.addView(metaRow)
        row.addView(textColumn, LayoutParams(0, host.dp(62), 1f))

        val properties = {
            boundTrack?.let(host.overlayController::openSongActions)
            Unit
        }
        SafeLongPress.bind(row, properties)
        SafeLongPress.bind(cover, properties)
        row.setOnClickListener { TrackTapController.handle(host, boundTrack, cover) }

        play = host.uiFactory.icon("").apply {
            host.uiFactory.applyPlainIconStyle(this, host.purple)
            setOnClickListener {
                val track = boundTrack ?: return@setOnClickListener
                if (host.isCurrent(track)) {
                    host.playbackQueueController.toggleOrStart()
                } else {
                    host.playbackQueueController.playTrack(track)
                }
            }
        }
        row.addView(play, host.uiFactory.square(44))
        container.addView(row, FrameLayout.LayoutParams(-1, -2))
        addView(host.uiFactory.spaced(container))
        visibility = GONE
    }

    fun setStaticTrackKeys(keys: Set<String>) {
        staticTrackKeys = Collections.unmodifiableSet(HashSet(keys))
        refresh()
    }

    fun refresh() {
        val current = host.playbackStateProvider.currentTrack()
        if (current == null || staticTrackKeys.contains(key(current))) {
            boundTrack = current
            waveform.setState(host.purpleSoft, host.yellow, false)
            visibility = GONE
            return
        }
        visibility = VISIBLE
        val previous = boundTrack
        if (previous == null || previous.uri != current.uri) {
            boundTrack = current
            title.text = current.title
            duration.text = host.formatTrackDuration(current)
            waveform.setTrackKey(current.title + current.uri)
            val openOrPlay = View.OnClickListener {
                TrackTapController.handle(host, current, cover)
            }
            cover.setOnClickListener(openOrPlay)
            if (isAttachedToWindow) {
                host.artworkUi.loadUnregisteredCover(
                    cover,
                    current,
                    host.purpleSoft,
                    CoverLoader.THUMB_SIZE,
                )
            }
        }
        waveform.setState(host.purple, host.yellow, host.isPlaybackPlaying())
        SongRowStateRegistry.applyPlayState(play, host.isPlaybackPlaying())
        if (cover is RotatingCoverImageView) cover.updatePlaybackState()
    }

    fun setTransitionPaused(paused: Boolean) {
        waveform.setTransitionPaused(paused)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        boundTrack = null
        refresh()
    }

    private fun key(track: Track): String = track.trackId.ifEmpty { track.uri }
}
