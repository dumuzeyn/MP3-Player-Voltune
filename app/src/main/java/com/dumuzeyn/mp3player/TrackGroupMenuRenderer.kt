package com.dumuzeyn.mp3player

import android.text.TextUtils
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import java.util.Locale

internal abstract class TrackGroupMenuRenderer(
    protected val host: MainActivityCore,
) : MenuRenderer {
    abstract fun groupedTracks(): Map<String, ArrayList<Track>>

    abstract fun unknownGroupName(): String

    abstract fun cardOpacity(): Int

    open fun groupSubtitle(name: String, tracks: ArrayList<Track>): String =
        "${tracks.size} ${host.tr("tracks", "треков")}"

    override fun needsMiniSpacer(): Boolean = true

    override fun render() {
        host.playlistController.beginPlaybackBindings(host.navigationState.songRenderGeneration)
        val query = host.navigationState.search.trim().lowercase(Locale.ROOT)
        groupedTracks().forEach { (rawName, tracks) ->
            val name = rawName.takeUnless { it.isBlank() } ?: unknownGroupName()
            if (
                query.isNotEmpty() &&
                !host.containsSearch(name, query) &&
                tracks.none { host.matchesTrackSearch(it, query) }
            ) {
                return@forEach
            }
            host.list.addView(host.uiFactory.spacedLibraryCard(groupCard(name, tracks)))
        }
    }

    private fun groupCard(name: String, tracks: ArrayList<Track>): View {
        val row = host.uiFactory.row().apply {
            id = R.id.group_card
            setPadding(host.dp(6), host.dp(4), host.dp(8), host.dp(4))
            minimumHeight = host.uiFactory.libraryCardHeight()
        }
        host.uiFactory.setSurface(row, host.panel, true, cardOpacity())
        val cover = host.uiFactory.coverView()
        val fallbackColor = host.purpleSoft
        if (tracks.isEmpty()) {
            cover.setBackgroundColor(fallbackColor)
        } else {
            host.artworkUi.loadGroupCover(cover, tracks, fallbackColor)
            if (cover is RotatingCoverImageView) cover.bindPlaylistTracks(tracks)
        }
        row.addView(cover, host.uiFactory.libraryArtwork())

        val labels = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(host.dp(10), 0, host.dp(6), 0)
            addView(host.uiFactory.text(name, 17, true).apply {
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.END
            })
            addView(host.uiFactory.text(groupSubtitle(name, tracks), 13, false))
        }
        row.addView(labels, LinearLayout.LayoutParams(0, host.dp(56), 1.0f))

        val playing = host.playbackQueueController.isPlayingCollection(tracks)
        val play = host.uiFactory.icon(if (playing) StrictIcon.PAUSE else StrictIcon.PLAY)
        host.uiFactory.applyPlainIconStyle(play, host.purple)
        SongRowStateRegistry.applyPlayState(play, playing, host.yellow, host.purple)
        play.setOnClickListener {
            if (host.playbackQueueController.isCurrentCollection(tracks)) {
                host.playbackQueueController.toggleOrStart()
            } else {
                host.playbackQueueController.playList(tracks, false)
            }
        }
        val shuffle = host.uiFactory.shuffleButton()
        host.uiFactory.applyPlainIconStyle(shuffle)
        shuffle.setOnClickListener { host.playbackQueueController.playList(tracks, true) }
        row.addView(shuffle, host.uiFactory.square(44))
        row.addView(play, host.uiFactory.square(44))
        row.setOnClickListener { host.overlayController.openGroup(name, tracks) }
        val marker = NowPlayingIndicator.create(host).apply {
            visibility = if (host.playbackQueueController.isCurrentCollection(tracks)) {
                View.VISIBLE
            } else {
                View.INVISIBLE
            }
        }
        return FrameLayout(host).apply {
            addView(row, FrameLayout.LayoutParams(-1, host.uiFactory.libraryCardHeight()))
            addView(marker, NowPlayingIndicator.layoutParams(host))
            host.playlistController.bindPlaybackState(
                play,
                marker,
                tracks,
                host.navigationState.songRenderGeneration,
                cover as? RotatingCoverImageView,
            )
        }
    }
}
