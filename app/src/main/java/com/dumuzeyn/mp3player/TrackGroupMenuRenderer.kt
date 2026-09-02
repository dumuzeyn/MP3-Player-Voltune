package com.dumuzeyn.mp3player

import android.graphics.Color
import android.text.TextUtils
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
            host.list.addView(host.uiFactory.spaced(groupCard(name, tracks)))
        }
    }

    private fun groupCard(name: String, tracks: ArrayList<Track>): LinearLayout {
        val row = host.uiFactory.row().apply {
            setPadding(host.dp(6), host.dp(4), host.dp(8), host.dp(4))
        }
        host.uiFactory.setSurface(row, host.panel, false, cardOpacity())
        val cover = host.uiFactory.coverView()
        val fallback = if (host.appearanceState.dark) 28 else 235
        val fallbackColor = Color.rgb(fallback, fallback, fallback)
        if (tracks.isEmpty()) {
            cover.setBackgroundColor(fallbackColor)
        } else {
            host.artworkUi.loadCover(cover, tracks[0], fallbackColor)
            if (cover is RotatingCoverImageView) cover.bindTracks(tracks)
        }
        row.addView(cover, host.uiFactory.square(52))

        val labels = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(host.dp(10), 0, host.dp(6), 0)
            addView(host.uiFactory.text(name, 17, true).apply {
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.END
            })
            addView(host.uiFactory.text(groupSubtitle(name, tracks), 13, false))
        }
        row.addView(labels, LinearLayout.LayoutParams(0, host.dp(62), 1.0f))

        val playing = host.playbackQueueController.isPlayingSource(tracks)
        val play = host.uiFactory.icon(if (playing) "Ⅱ" else "▶")
        host.uiFactory.applyPlainIconStyle(play, host.purple)
        SongRowStateRegistry.applyPlayState(play, playing)
        play.setOnClickListener {
            if (host.playbackQueueController.isPlayingSource(tracks)) {
                host.playbackQueueController.toggleOrStart()
            } else {
                host.playbackQueueController.playList(tracks, false)
            }
        }
        row.addView(play, host.uiFactory.square(44))
        val shuffle = host.uiFactory.shuffleButton()
        host.uiFactory.applyPlainIconStyle(shuffle)
        shuffle.setOnClickListener { host.playbackQueueController.playList(tracks, true) }
        row.addView(shuffle, host.uiFactory.square(44))
        row.setOnClickListener { host.overlayController.openGroup(name, tracks) }
        return row
    }
}
