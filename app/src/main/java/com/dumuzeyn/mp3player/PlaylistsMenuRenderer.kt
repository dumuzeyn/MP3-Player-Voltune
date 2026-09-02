package com.dumuzeyn.mp3player

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout

internal class PlaylistsMenuRenderer(private val host: MainActivityCore) : MenuRenderer {
    override fun needsMiniSpacer(): Boolean = true

    override fun render() {
        host.playlistController.beginPlaybackBindings(host.navigationState.songRenderGeneration)
        val playlists = host.playlistController.filteredPlaylists(host.navigationState.search)
        if (playlists.isEmpty()) {
            val empty = host.uiFactory.text(
                host.tr3("No playlists yet", "Плейлистов пока нет", "∅ ▤"),
                18,
                true,
            ).apply { setPadding(host.dp(12), host.dp(24), host.dp(12), host.dp(24)) }
            host.list.addView(empty)
            return
        }
        playlists.forEach { host.list.addView(host.uiFactory.spaced(playlistCard(it))) }
    }

    private fun playlistCard(playlist: Playlist): View {
        val tracks = host.playlistController.sortedPlaylistTracks(playlist)
        val card = LinearLayout(host).apply {
            id = R.id.playlist_card
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(host.dp(7), host.dp(4), host.dp(6), host.dp(4))
        }
        host.uiFactory.setSurface(card, host.panel, false, host.appearanceState.playlistCardOpacity)
        val cover = host.uiFactory.staticCoverView()
        val fallback = if (host.appearanceState.dark) 28 else 235
        val fallbackColor = Color.rgb(fallback, fallback, fallback)
        if (tracks.isEmpty()) {
            cover.setBackgroundColor(fallbackColor)
        } else {
            host.artworkUi.loadUnregisteredCover(cover, tracks[0], fallbackColor, CoverLoader.THUMB_SIZE)
        }
        val coverSize = host.resources.getDimensionPixelSize(R.dimen.playlist_cover_size)
        card.addView(cover, LinearLayout.LayoutParams(coverSize, coverSize))

        val titleColumn = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(host.dp(10), 0, host.dp(5), 0)
        }
        val title = host.uiFactory.text(playlist.name, 17, true)
        host.uiFactory.makeMarquee(title)
        val count = host.uiFactory.text(
            "${playlist.uris.size} ${host.tr3("tracks", "треков", "♪")}",
            12,
            false,
        )
        titleColumn.addView(title, LinearLayout.LayoutParams(-1, host.dp(22)))
        titleColumn.addView(count, LinearLayout.LayoutParams(-1, host.dp(16)))
        card.addView(titleColumn, LinearLayout.LayoutParams(0, -1, 1.0f))

        val actions = host.uiFactory.row()
        val actionSize = host.resources.getDimensionPixelSize(R.dimen.playlist_action_size)
        val delete = host.uiFactory.icon("×")
        host.uiFactory.applyPlainIconStyle(delete, Color.rgb(190, 45, 45))
        delete.setOnClickListener { host.overlayController.confirmDeletePlaylist(playlist) }
        actions.addView(delete, LinearLayout.LayoutParams(actionSize, actionSize))
        val rename = host.uiFactory.icon("✎")
        host.uiFactory.applyPlainIconStyle(rename)
        rename.setOnClickListener { host.overlayController.renamePlaylist(playlist) }
        actions.addView(rename, LinearLayout.LayoutParams(actionSize, actionSize))

        val playing = host.playbackQueueController.isPlayingCollection(tracks)
        val play = host.uiFactory.icon(if (playing) "Ⅱ" else "▶")
        host.uiFactory.applyPlainIconStyle(play, host.purple)
        SongRowStateRegistry.applyPlayState(play, playing)
        play.setOnClickListener {
            if (host.playbackQueueController.isCurrentCollection(tracks)) {
                host.playbackQueueController.toggleOrStart()
            } else {
                host.playbackQueueController.playList(tracks, false)
            }
        }
        actions.addView(play, LinearLayout.LayoutParams(actionSize, actionSize))
        val shuffle = host.uiFactory.shuffleButton()
        host.uiFactory.applyPlainIconStyle(shuffle)
        shuffle.setOnClickListener { host.playbackQueueController.playList(tracks, true) }
        actions.addView(shuffle, LinearLayout.LayoutParams(actionSize, actionSize))
        card.addView(actions, LinearLayout.LayoutParams(actionSize * 4, actionSize))
        card.setOnClickListener { host.overlayController.openPlaylist(playlist) }

        val cardHeight = host.resources.getDimensionPixelSize(R.dimen.playlist_card_height)
        return FrameLayout(host).apply {
            addView(card, FrameLayout.LayoutParams(-1, cardHeight))
            val marker = NowPlayingIndicator.create(host)
            addView(marker, NowPlayingIndicator.layoutParams(host))
            host.playlistController.bindPlaybackState(
                play,
                marker,
                tracks,
                host.navigationState.songRenderGeneration,
            )
            minimumHeight = cardHeight
        }
    }
}
