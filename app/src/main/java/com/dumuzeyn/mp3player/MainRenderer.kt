package com.dumuzeyn.mp3player

import android.os.Trace
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView

/** Owns tab rendering, scroll restoration, and the reusable Home hierarchy. */
internal class MainRenderer(private val host: MainActivityCore) {
    private val homeRenderer = HomeMenuRenderer(host)
    private val songsRenderer = SongsMenuRenderer(host)
    private val favoritesRenderer = FavoritesMenuRenderer(host)
    private val playlistsRenderer = PlaylistsMenuRenderer(host)
    private val soundRenderer: MenuRenderer = SoundMenuRenderer(host)
    private val genresRenderer: MenuRenderer = GenresMenuRenderer(host)
    private val artistsRenderer: MenuRenderer = ArtistsMenuRenderer(host)
    private val albumsRenderer: MenuRenderer = AlbumsMenuRenderer(host)
    private val settingsRenderer: MenuRenderer = SettingsMenuRenderer(host)
    private val foldersRenderer: MenuRenderer = FoldersMenuRenderer(host)
    private val scrollPositions = HashMap<String, Int>()
    private val cachedHomeRows = SongRowStateRegistry()
    private var cachedHomeContent: LinearLayout? = null
    private var cachedHomeModel: HomeContent? = null
    private var cachedHomeKey = ""
    private var renderedMenuKey: String? = null

    fun render() {
        rememberCurrentScrollPosition()
        host.refreshTabs()
        host.navigationState.songRenderGeneration++
        if (
            host.navigationState.tabIndex == LibraryTabs.SONGS &&
            !host.navigationState.renderingTabPreview &&
            host.songsView != null
        ) {
            host.songRows.clear()
            host.sourcePlayButton = null
            host.contentScroll?.visibility = View.GONE
            songsRenderer.render()
            renderedMenuKey = menuKey(host.navigationState.tabIndex, host.navigationState.search)
            host.playerUiController.updateMini()
            return
        }
        host.songsView?.hide()
        host.contentScroll?.visibility = View.VISIBLE
        if (host.navigationState.tabIndex == LibraryTabs.HOME && attachCachedHome()) {
            restoreCurrentScrollPosition()
            host.playerUiController.updateMini()
            return
        }
        host.list.removeAllViews()
        host.songRows.clear()
        host.sourcePlayButton = null
        val renderer = rendererForTab()
        val scrollY = scrollPositionFor(host.navigationState.tabIndex, host.navigationState.search)
        if (
            host.navigationState.tabIndex == LibraryTabs.SONGS ||
            host.navigationState.tabIndex == LibraryTabs.FAVORITES
        ) {
            host.songsRenderer.prepareNextRenderForScroll(scrollY)
        }
        if (host.navigationState.tabIndex == LibraryTabs.HOME) {
            renderAndCacheHome(renderer)
        } else {
            host.renderSectionHeader()
            renderer.render()
            if (renderer.needsMiniSpacer()) host.addMiniSpacerIfNeeded()
        }
        restoreCurrentScrollPosition()
        host.playerUiController.updateMini()
    }

    fun captureScrollBeforeUiRebuild() {
        rememberCurrentScrollPosition()
        renderedMenuKey = null
        invalidateHomeCache()
    }

    fun captureCurrentScrollPosition() = rememberCurrentScrollPosition()

    private fun rememberCurrentScrollPosition() {
        val key = renderedMenuKey ?: return
        val scroll = host.contentScroll ?: return
        if (key.startsWith("${LibraryTabs.SONGS}\n")) return
        scrollPositions[key] = scroll.scrollY.coerceAtLeast(0)
    }

    private fun restoreCurrentScrollPosition() {
        val targetKey = menuKey(host.navigationState.tabIndex, host.navigationState.search)
        renderedMenuKey = targetKey
        val targetScroll = host.contentScroll ?: return
        val scrollY = scrollPositions[targetKey]?.coerceAtLeast(0) ?: 0
        if (scrollY <= 0) {
            targetScroll.scrollTo(0, 0)
            targetScroll.visibility = View.VISIBLE
            return
        }
        targetScroll.visibility = View.INVISIBLE
        targetScroll.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    val observer = targetScroll.viewTreeObserver
                    if (observer.isAlive) observer.removeOnPreDrawListener(this)
                    if (
                        host.contentScroll !== targetScroll ||
                        targetKey != menuKey(
                            host.navigationState.tabIndex,
                            host.navigationState.search,
                        )
                    ) {
                        targetScroll.visibility = View.VISIBLE
                        return true
                    }
                    targetScroll.scrollTo(0, scrollY)
                    targetScroll.visibility = View.VISIBLE
                    return false
                }
            },
        )
    }

    private fun menuKey(tabIndex: Int, search: String?): String =
        "$tabIndex\n${search.orEmpty()}"

    fun renderPreview(
        target: LinearLayout,
        targetIndex: Int,
        targetSearch: String?,
    ): PreviewState {
        Trace.beginSection("Voltune/Home.renderTabPreview")
        try {
            val previousList = host.list
            val previousButton = ButtonState(host.sourcePlayButton)
            val previousBatchState = host.songsRenderer.captureBatchState()
            val previousTab = host.navigationState.tabIndex
            val previousGeneration = host.navigationState.songRenderGeneration
            val previousSearch = host.navigationState.search
            val previousPreview = host.navigationState.renderingTabPreview
            val scrollY = scrollPositionFor(targetIndex, targetSearch)
            try {
                host.previewSongRows.clear()
                host.list = target
                host.navigationState.tabIndex = targetIndex
                host.navigationState.songRenderGeneration = previousGeneration + 1
                host.navigationState.search = targetSearch.orEmpty()
                host.navigationState.renderingTabPreview = true
                host.sourcePlayButton = null
                target.removeAllViews()
                val renderer = rendererForTab(targetIndex)
                if (targetIndex == LibraryTabs.SONGS || targetIndex == LibraryTabs.FAVORITES) {
                    host.songsRenderer.prepareNextRenderForScroll(scrollY)
                }
                if (targetIndex == LibraryTabs.HOME && attachCachedHome()) {
                    // Reuse the detached Home hierarchy, artwork, and playback row bindings.
                } else if (targetIndex == LibraryTabs.HOME) {
                    renderAndCacheHome(renderer)
                } else {
                    host.renderSectionHeader()
                    renderer.render()
                    if (renderer.needsMiniSpacer()) host.addMiniSpacerIfNeeded()
                }
                return PreviewState(
                    scrollY,
                    host.navigationState.songRenderGeneration,
                    host.songsRenderer.captureBatchState(),
                    host.sourcePlayButton,
                )
            } finally {
                host.list = previousList
                host.navigationState.tabIndex = previousTab
                host.navigationState.songRenderGeneration = previousGeneration
                host.navigationState.search = previousSearch
                host.navigationState.renderingTabPreview = previousPreview
                host.sourcePlayButton = previousButton.button
                host.songsRenderer.restoreBatchState(previousBatchState)
            }
        } finally {
            Trace.endSection()
        }
    }

    fun adoptPreview(targetIndex: Int, targetSearch: String?, state: PreviewState) {
        Trace.beginSection("Voltune/Home.adoptPreview")
        try {
            host.songsView?.hide()
            renderedMenuKey = menuKey(targetIndex, targetSearch)
            host.navigationState.songRenderGeneration = state.generation
            host.songsRenderer.restoreBatchState(state.batchState)
            host.songRows.replaceWith(host.previewSongRows)
            host.previewSongRows.clear()
            host.sourcePlayButton = state.sourcePlayButton
            if (targetIndex != LibraryTabs.HOME) {
                host.songRows.refresh(host.songRowStateResolver())
            }
            host.artworkUi.promoteVisibleArtwork()
            host.playerUiController.updateMini()
        } finally {
            Trace.endSection()
        }
    }

    fun discardPreview() = host.previewSongRows.clear()

    fun invalidateHomeCache() {
        cachedHomeContent = null
        cachedHomeModel = null
        cachedHomeKey = ""
        cachedHomeRows.clear()
    }

    fun refreshHomePlayback() {
        if (cachedHomeContent?.isAttachedToWindow == true) homeRenderer.refreshPlaybackSection()
    }

    fun setHomeTransitionPaused(paused: Boolean) = homeRenderer.setPlaybackTransitionPaused(paused)

    private fun attachCachedHome(): Boolean {
        val content = cachedHomeContent ?: return false
        if (cachedHomeModel !== host.libraryState.homeContent || cachedHomeKey != homeKey()) {
            return false
        }
        Trace.beginSection("Voltune/Home.cacheHit")
        try {
            val parent = content.parent
            if (parent is ViewGroup && parent !== host.list) parent.removeView(content)
            if (content.parent !== host.list) {
                host.list.removeAllViews()
                host.list.addView(content, LinearLayout.LayoutParams(-1, -2))
            }
            host.activeSongRows().replaceWith(cachedHomeRows)
            host.activeSongRows().refresh(host.songRowStateResolver())
            homeRenderer.refreshPlaybackSection()
            host.sourcePlayButton = null
            return true
        } finally {
            Trace.endSection()
        }
    }

    private fun renderAndCacheHome(renderer: MenuRenderer) {
        Trace.beginSection("Voltune/Home.cacheMiss")
        try {
            val target = host.list
            val content = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL }
            target.addView(content, LinearLayout.LayoutParams(-1, -2))
            host.list = content
            try {
                host.renderSectionHeader()
                renderer.render()
                if (renderer.needsMiniSpacer()) host.addMiniSpacerIfNeeded()
            } finally {
                host.list = target
            }
            cachedHomeContent = content
            cachedHomeModel = host.libraryState.homeContent
            cachedHomeKey = homeKey()
            cachedHomeRows.replaceWith(host.activeSongRows())
        } finally {
            Trace.endSection()
        }
    }

    private fun homeKey(): String = buildString {
        append(host.appearanceState.language)
        append('|').append(host.appearanceState.circularCovers)
        append('|').append(host.panel)
        append('|').append(host.primaryText)
        append('|').append(host.secondaryText)
        append('|').append(host.appearanceState.playlistCardOpacity)
        append('|').append(host.appearanceState.artistCardOpacity)
        append('|').append(host.appearanceState.albumCardOpacity)
    }

    private fun scrollPositionFor(tabIndex: Int, search: String?): Int =
        scrollPositions[menuKey(tabIndex, search)]?.coerceAtLeast(0) ?: 0

    private fun rendererForTab(): MenuRenderer = rendererForTab(host.navigationState.tabIndex)

    private fun rendererForTab(tabIndex: Int): MenuRenderer = when (tabIndex) {
        LibraryTabs.HOME -> homeRenderer
        LibraryTabs.SONGS -> songsRenderer
        LibraryTabs.FAVORITES -> favoritesRenderer
        LibraryTabs.PLAYLISTS -> playlistsRenderer
        LibraryTabs.SOUND -> soundRenderer
        LibraryTabs.GENRES -> genresRenderer
        LibraryTabs.ARTISTS -> artistsRenderer
        LibraryTabs.ALBUMS -> albumsRenderer
        LibraryTabs.FOLDERS -> foldersRenderer
        LibraryTabs.SETTINGS -> settingsRenderer
        else -> songsRenderer
    }

    private class ButtonState(@JvmField val button: Button?)

    class PreviewState(
        @JvmField val scrollY: Int,
        @JvmField val generation: Int,
        @JvmField val batchState: SongsRenderer.BatchState,
        @JvmField val sourcePlayButton: Button?,
    )
}
