package com.dumuzeyn.mp3player

import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout

internal class HeaderController(private val host: MainActivityCore) {
    fun renderSectionHeader() {
        host.list.addView(createSectionHeader())
    }

    fun createSectionHeader(): View = createSectionHeader(host.navigationState.tabIndex)

    fun createSongsSectionHeader(): View = createSectionHeader(LibraryTabs.SONGS)

    private fun createSectionHeader(tabIndex: Int): View = LinearLayout(host).apply {
        orientation = LinearLayout.VERTICAL
        when (tabIndex) {
            LibraryTabs.SONGS,
            LibraryTabs.FAVORITES,
            -> addView(libraryActions(tabIndex), LinearLayout.LayoutParams(-1, host.dp(62)))

            LibraryTabs.PLAYLISTS -> {
                val actions = host.uiFactory.row().apply {
                    addView(
                        actionButton("+") { host.overlayController.createPlaylist() },
                        host.uiFactory.square(52),
                    )
                    addView(
                        actionButton("⌕") { host.overlayController.openSearch() },
                        host.uiFactory.square(52),
                    )
                }
                addView(actions, LinearLayout.LayoutParams(-1, host.dp(62)))
            }
        }
    }

    fun refreshSongsSectionHeader(section: View?) {
        if (section == null) return
        val play = section.findViewById<Button>(R.id.section_play) ?: return
        play.text = if (host.playbackQueueController.isPlayingSource(host.currentVisibleTracks())) {
            "Ⅱ"
        } else {
            "▶"
        }
        host.sourcePlayButton = play
    }

    private fun libraryActions(tabIndex: Int): LinearLayout = host.uiFactory.row().apply {
        if (tabIndex == LibraryTabs.SONGS) {
            addView(actionButton("+") { host.audioImportController.openFiles() }, host.uiFactory.square(52))
            addView(actionButton("▣") { host.audioImportController.openFolder() }, host.uiFactory.square(52))
        } else {
            addView(actionButton("+") { host.overlayController.openAddFavorites() }, host.uiFactory.square(52))
        }
        addView(actionButton("⌕") { host.overlayController.openSearch() }, host.uiFactory.square(52))

        val visible = host.currentVisibleTracks()
        val play = actionButton(
            if (host.playbackQueueController.isPlayingSource(visible)) "Ⅱ" else "▶",
        ) {
            val currentVisible = host.currentVisibleTracks()
            if (host.playbackQueueController.isPlayingSource(currentVisible)) {
                host.playbackQueueController.toggleOrStart()
            } else {
                host.playbackQueueController.playList(currentVisible, false)
            }
        }.apply { id = R.id.section_play }
        host.sourcePlayButton = play

        val shuffle = host.uiFactory.shuffleButton().apply {
            setOnClickListener {
                host.playbackQueueController.playList(host.currentVisibleTracks(), true)
            }
        }
        addView(shuffle, host.uiFactory.square(52))
        addView(play, host.uiFactory.square(52))
    }

    private fun actionButton(symbol: String, listener: (View) -> Unit): Button =
        host.uiFactory.icon(symbol).apply { setOnClickListener(listener) }
}
