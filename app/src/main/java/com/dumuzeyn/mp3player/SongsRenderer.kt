package com.dumuzeyn.mp3player

import android.graphics.Color
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.max
import kotlin.math.min

internal class SongsRenderer(private val host: MainActivityCore) {
    private var pendingTracks: ArrayList<Track>? = null
    private var pendingStart = 0
    private var pendingGeneration = -1
    private var renderedStart = 0
    private var rowStartChildIndex = 0
    private var topSpacer: View? = null
    private var nextRenderScrollY = -1

    fun close() = Unit

    fun render(tracks: ArrayList<Track>) {
        renderSongsState(tracks)
    }

    fun renderLibrary(tracks: ArrayList<Track>, query: String) {
        val songsView = host.songsView
        if (!host.navigationState.renderingTabPreview && songsView != null) {
            songsView.show(tracks, query)
            return
        }
        renderSongsState(host.libraryListController.filter(tracks))
    }

    fun renderSongsState(tracks: ArrayList<Track>) {
        pendingTracks = null
        pendingStart = 0
        pendingGeneration = -1
        renderedStart = 0
        topSpacer = null
        if (tracks.isEmpty()) {
            val message = if (host.navigationState.tabIndex == LibraryTabs.SONGS) {
                host.tr(
                    "Add MP3 or another audio file",
                    "Добавьте MP3 или другой аудиофайл",
                )
            } else {
                host.tr("Nothing here yet", "Здесь пока пусто")
            }
            val empty = host.uiFactory.text(message, 18, true).apply {
                setPadding(host.dp(12), host.dp(24), host.dp(12), host.dp(24))
            }
            host.list.addView(empty)
            host.addMiniSpacerIfNeeded()
            return
        }
        pendingTracks = ArrayList(tracks)
        pendingGeneration = host.navigationState.songRenderGeneration
        rowStartChildIndex = host.list.childCount
        val initialScrollY = nextRenderScrollY
        nextRenderScrollY = -1
        if (initialScrollY > 0 && initializeWindow(initialScrollY)) return
        appendNextSongBatch()
    }

    fun prepareNextRenderForScroll(scrollY: Int) {
        nextRenderScrollY = max(0, scrollY)
    }

    fun loadMoreIfNearBottom() {
        val tracks = pendingTracks
        val scroll = host.contentScroll
        if (scroll == null || tracks == null) return
        prependPreviousBatchIfNeeded()
        if (pendingStart >= tracks.size) return
        val child = scroll.getChildAt(0)
        if (child == null || child.bottom - (scroll.height + scroll.scrollY) > host.dp(900)) return
        appendNextSongBatch()
    }

    fun prepareForScrollRestore(scrollY: Int) {
        if (scrollY > 0 && pendingTracks != null) initializeWindow(scrollY)
    }

    fun captureBatchState(): BatchState = BatchState(
        pendingTracks,
        pendingStart,
        pendingGeneration,
        renderedStart,
        rowStartChildIndex,
        topSpacer,
    )

    fun restoreBatchState(state: BatchState) {
        pendingTracks = state.pendingTracks
        pendingStart = state.pendingStart
        pendingGeneration = state.pendingGeneration
        renderedStart = state.renderedStart
        rowStartChildIndex = state.rowStartChildIndex
        topSpacer = state.topSpacer
    }

    fun songRow(track: Track, showActions: Boolean, showFavoriteAction: Boolean): View =
        songRow(track, showActions, showFavoriteAction, null, null)

    fun songRow(
        track: Track,
        showActions: Boolean,
        showFavoriteAction: Boolean,
        afterPlay: Runnable?,
    ): View = songRow(track, showActions, showFavoriteAction, afterPlay, null)

    fun songRow(
        track: Track,
        showActions: Boolean,
        @Suppress("UNUSED_PARAMETER") showFavoriteAction: Boolean,
        afterPlay: Runnable?,
        actionOverride: Runnable?,
    ): View {
        val container = FrameLayout(host)
        val row = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(host.dp(8), host.dp(4), host.dp(8), host.dp(4))
        }
        host.uiFactory.applyCardStyle(
            row,
            if (host.navigationState.tabIndex == LibraryTabs.FAVORITES) {
                host.appearanceState.favoriteCardOpacity
            } else {
                host.appearanceState.songCardOpacity
            },
        )

        val marker = NowPlayingIndicator.create(host).apply {
            visibility = if (host.isCurrent(track)) View.VISIBLE else View.INVISIBLE
        }
        host.activeSongRows().registerCurrentMarker(track.uri, marker)

        val cover = host.uiFactory.coverView()
        host.artworkUi.loadCover(cover, track, host.purpleSoft)
        val openOrPlay = View.OnClickListener { TrackTapController.handle(host, track, cover) }
        cover.setOnClickListener(openOrPlay)
        row.setOnClickListener(openOrPlay)
        val openDescription = host.tr(
            "Open or play track " + track.title,
            "Открыть или включить песню " + track.title,
        )
        cover.contentDescription = openDescription
        row.contentDescription = openDescription
        row.addView(cover, host.uiFactory.square(52))

        val textColumn = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(host.dp(10), 0, host.dp(6), 0)
        }
        val title = host.uiFactory.text(track.title, 16, true).apply {
            setTextColor(host.primaryText)
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
        }
        textColumn.addView(title)

        val metaRow = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val waveform = host.artworkUi.createWaveform(track, host.isCurrent(track))
        host.activeSongRows().registerWaveform(track.uri, waveform)
        metaRow.addView(waveform, LinearLayout.LayoutParams(0, host.dp(26), 1f))
        val duration = host.uiFactory.text(host.formatTrackDuration(track), 12, false).apply {
            gravity = Gravity.CENTER
            setTextColor(host.secondaryText)
        }
        metaRow.addView(duration, LinearLayout.LayoutParams(host.dp(46), host.dp(26)))
        host.activeSongRows().registerMetadata(track.uri, title, duration)
        textColumn.addView(metaRow)
        row.addView(textColumn, LinearLayout.LayoutParams(0, host.dp(62), 1f))

        if (host.navigationState.tabIndex == LibraryTabs.FAVORITES) {
            val favorite = host.uiFactory.icon(
                if (host.libraryState.favorites.contains(track.uri)) "♥︎" else "♡︎",
            ).apply { textSize = 14f }
            host.uiFactory.applyPlainIconStyle(
                favorite,
                if (host.libraryState.favorites.contains(track.uri)) {
                    host.purple
                } else {
                    host.secondaryText
                },
            )
            favorite.setOnClickListener {
                host.toggleFavorite(track)
                host.render()
            }
            row.addView(favorite, host.uiFactory.square(40))
        } else if (showActions) {
            val actions = host.uiFactory.icon("⋯")
            host.uiFactory.applyPlainIconStyle(actions)
            actions.setOnClickListener {
                if (actionOverride != null) {
                    actionOverride.run()
                } else {
                    host.overlayController.openSongActions(track)
                }
            }
            row.addView(actions, host.uiFactory.square(44))
        }

        val play = host.uiFactory.icon("")
        host.uiFactory.applyPrimaryButtonStyle(play)
        SongRowStateRegistry.applyPlayState(
            play,
            host.isCurrent(track) && host.isPlaybackPlaying(),
        )
        play.setOnClickListener {
            if (host.isCurrent(track)) {
                host.playbackQueueController.toggleOrStart()
            } else {
                host.playbackQueueController.playTrack(track)
            }
            afterPlay?.run()
        }
        host.activeSongRows().registerPlayButton(track.uri, play)
        row.addView(play, host.uiFactory.square(44))
        container.addView(row, FrameLayout.LayoutParams(-1, -2))
        container.addView(marker, NowPlayingIndicator.layoutParams(host))
        return host.uiFactory.spaced(container)
    }

    fun queueRow(track: Track, removeAction: Runnable, playAction: Runnable): View {
        val container = FrameLayout(host)
        val row = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(host.dp(8), host.dp(4), host.dp(8), host.dp(4))
        }
        host.uiFactory.setSurface(
            row,
            host.panel,
            false,
            host.appearanceState.songCardOpacity,
        )

        val marker = NowPlayingIndicator.create(host).apply {
            visibility = if (host.isCurrent(track)) View.VISIBLE else View.INVISIBLE
        }
        val cover = host.uiFactory.coverView()
        host.artworkUi.loadCover(
            cover,
            track,
            if (host.appearanceState.dark) Color.rgb(28, 28, 28) else Color.rgb(235, 235, 235),
        )
        row.addView(cover, host.uiFactory.square(52))

        val title = host.uiFactory.text(track.title, 17, true).apply {
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
            setPadding(host.dp(12), 0, host.dp(8), 0)
            setTextColor(host.fg)
        }
        row.addView(title, LinearLayout.LayoutParams(0, host.dp(62), 1f))

        val remove = host.uiFactory.icon("−")
        host.uiFactory.applyPlainIconStyle(remove, Color.rgb(190, 45, 45))
        remove.setOnClickListener { removeAction.run() }
        row.addView(remove, host.uiFactory.square(44))

        val play = host.uiFactory.icon("")
        host.uiFactory.applyPlainIconStyle(play, host.purple)
        SongRowStateRegistry.applyPlayState(
            play,
            host.isCurrent(track) && host.isPlaybackPlaying(),
        )
        play.setOnClickListener { playAction.run() }
        row.addView(play, host.uiFactory.square(44))
        container.addView(row, FrameLayout.LayoutParams(-1, -2))
        container.addView(marker, NowPlayingIndicator.layoutParams(host))
        return host.uiFactory.spaced(container)
    }

    private fun initializeWindow(scrollY: Int): Boolean {
        val rowHeight = estimatedRowHeight()
        val targetStart = max(0, scrollY / rowHeight - 8)
        if (targetStart <= 0 || targetStart < pendingStart) return false
        while (host.list.childCount > rowStartChildIndex) {
            host.list.removeViewAt(host.list.childCount - 1)
        }
        host.activeSongRows().clear()
        renderedStart = targetStart
        pendingStart = targetStart
        topSpacer = View(host).apply {
            layoutParams = LinearLayout.LayoutParams(-1, targetStart * rowHeight)
        }.also { host.list.addView(it) }
        appendNextSongBatch()
        return true
    }

    private fun prependPreviousBatchIfNeeded() {
        val spacer = topSpacer
        val tracks = pendingTracks
        if (renderedStart <= 0 || spacer == null || tracks == null) return
        val spacerHeight = renderedStart * estimatedRowHeight()
        if (host.contentScroll.scrollY > spacerHeight + host.dp(700)) return
        val newStart = max(0, renderedStart - BATCH_SIZE)
        var insertionIndex = rowStartChildIndex + 1
        for (index in newStart until renderedStart) {
            host.list.addView(songRow(tracks[index], true, true), insertionIndex++)
        }
        renderedStart = newStart
        if (renderedStart == 0) {
            host.list.removeView(spacer)
            topSpacer = null
        } else {
            spacer.layoutParams.height = renderedStart * estimatedRowHeight()
            spacer.requestLayout()
        }
    }

    private fun appendNextSongBatch() {
        val tracks = pendingTracks
        if (
            tracks == null ||
            pendingGeneration != host.navigationState.songRenderGeneration ||
            host.navigationState.tabIndex != LibraryTabs.SONGS &&
            host.navigationState.tabIndex != LibraryTabs.FAVORITES
        ) {
            pendingTracks = null
            return
        }
        if (pendingStart >= tracks.size) return
        val start = pendingStart
        val end = min(tracks.size, start + BATCH_SIZE)
        for (index in start until end) {
            host.list.addView(songRow(tracks[index], true, true))
        }
        pendingStart = end
        if (end >= tracks.size) host.addMiniSpacerIfNeeded()
    }

    private fun estimatedRowHeight(): Int = max(1, host.dp(66))

    class BatchState(
        @JvmField val pendingTracks: ArrayList<Track>?,
        @JvmField val pendingStart: Int,
        @JvmField val pendingGeneration: Int,
        @JvmField val renderedStart: Int,
        @JvmField val rowStartChildIndex: Int,
        @JvmField val topSpacer: View?,
    )

    private companion object {
        const val BATCH_SIZE = 15
    }
}
