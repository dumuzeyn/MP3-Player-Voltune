package com.dumuzeyn.mp3player

import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView

/** Commits tab navigation without keeping transition mechanics in the Activity. */
internal class TabTransitionCoordinator(
    private val navigation: Navigation,
    private val screen: MainScreenView,
    private val renderer: MainRenderer,
    private val backNavigation: BackNavigationController,
    private val tabs: TabsController,
    private val songs: SongsRenderer,
    private val contentBinding: ContentBinding,
) {
    fun interface ContentBinding {
        fun bind(scrollView: ScrollView, content: LinearLayout)
    }

    interface Navigation {
        fun selectedTab(): Int
        fun setSelectedTab(value: Int)
        fun searchQuery(): String
        fun setSearchQuery(value: String?)
        fun setPreferredDirection(value: Int)
        fun setTransitionRunning(value: Boolean)
    }

    fun complete(request: TabTransitionRequest) {
        recordHistoryIfNeeded(request)
        applyNavigation(request)
        renderer.render()
        tabs.finishTransition(request.targetTab)
    }

    fun completeWithPreview(request: TabTransitionRequest) {
        val previewScroll = requireNotNull(request.scrollView)
        val previewContent = requireNotNull(request.content)
        val previewState = requireNotNull(request.previewState)
        recordHistoryIfNeeded(request)
        renderer.captureCurrentScrollPosition()
        val previous = screen.contentScroll()
        val contentHost = screen.contentHost()
        if (previous != null && previous.parent === contentHost) {
            contentHost.removeView(previous)
        }
        previewScroll.translationX = 0f
        previewScroll.alpha = 1f
        previewScroll.visibility = View.VISIBLE
        previewScroll.setOnScrollChangeListener { _, _, _, _, _ -> songs.loadMoreIfNearBottom() }
        screen.replaceContent(previewScroll, previewContent)
        contentBinding.bind(previewScroll, previewContent)
        applyNavigation(request)
        renderer.adoptPreview(request.targetTab, request.searchQuery, previewState)
        tabs.refreshTabs()
        tabs.finishTransition(request.targetTab)
    }

    private fun recordHistoryIfNeeded(request: TabTransitionRequest) {
        if (request.recordHistory) {
            backNavigation.recordTabState(navigation.selectedTab(), navigation.searchQuery())
        }
    }

    private fun applyNavigation(request: TabTransitionRequest) {
        navigation.setPreferredDirection(request.direction)
        navigation.setSelectedTab(request.targetTab)
        navigation.setSearchQuery(request.searchQuery)
        navigation.setTransitionRunning(false)
    }
}
