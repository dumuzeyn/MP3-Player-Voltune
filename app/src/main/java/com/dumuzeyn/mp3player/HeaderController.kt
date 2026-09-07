package com.dumuzeyn.mp3player

import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout

internal class HeaderController(private val host: MainActivityCore) {
    fun buildAppHeader(page: LinearLayout) {
        val header = FrameLayout(host).apply {
            host.uiFactory.applyCardStyle(this, host.appearanceState.headerCardOpacity)
            setPadding(host.dp(12), 0, host.dp(12), 0)
        }
        val row = host.uiFactory.row()
        val icon = ImageView(host).apply {
            setImageBitmap(AppIconRenderer.renderLogo(host, host.purple, host.yellow, host.dp(42)))
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            contentDescription = host.getString(R.string.app_name)
        }
        val iconParams = host.uiFactory.square(36).apply {
            setMargins(0, 0, host.dp(8), 0)
        }
        row.addView(icon, iconParams)

        val brand = SpannableString(host.getString(R.string.app_name)).apply {
            setSpan(
                ForegroundColorSpan(host.purple),
                0,
                1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
        val title = host.uiFactory.text(brand.toString(), 22, true).apply {
            text = brand
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(host.primaryText)
            gravity = Gravity.CENTER_VERTICAL
            includeFontPadding = false
            letterSpacing = 0f
        }
        row.addView(title, LinearLayout.LayoutParams(0, host.dp(52), 1f))
        header.addView(row, FrameLayout.LayoutParams(-1, -1))
        page.addView(
            header,
            LinearLayout.LayoutParams(-1, host.dp(60)).apply {
                setMargins(0, 0, 0, host.dp(8))
            },
        )
    }

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
        addView(play, host.uiFactory.square(52))

        val shuffle = host.uiFactory.shuffleButton().apply {
            setOnClickListener {
                host.playbackQueueController.playList(host.currentVisibleTracks(), true)
            }
        }
        addView(shuffle, host.uiFactory.square(52))
    }

    private fun actionButton(symbol: String, listener: (View) -> Unit): Button =
        host.uiFactory.icon(symbol).apply { setOnClickListener(listener) }
}
