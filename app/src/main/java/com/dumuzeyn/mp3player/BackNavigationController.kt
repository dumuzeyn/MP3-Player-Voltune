package com.dumuzeyn.mp3player

import java.util.ArrayDeque

internal class BackNavigationController(private val host: MainActivityCore) {
    private val tabHistory = ArrayDeque<TabState>()

    fun recordTabState(tabIndex: Int, search: String?) {
        val safeSearch = search.orEmpty()
        val latest = tabHistory.peekLast()
        if (latest?.tabIndex == tabIndex && latest.search == safeSearch) return
        if (tabHistory.size >= MAX_HISTORY) tabHistory.removeFirst()
        tabHistory.addLast(TabState(tabIndex, safeSearch))
    }

    fun handleBack(): Boolean {
        if (host.navigationState.tabAnimating) return true
        if (host.overlayHost?.childCount ?: 0 > 0) {
            val top = host.overlayHost.getChildAt(host.overlayHost.childCount - 1)
            if (!host.playerUiController.closeFullPlayerIfTop(top)) {
                host.overlayHost.removeView(top)
                host.playerUiController.updateMini()
            }
            return true
        }
        val previous = tabHistory.pollLast() ?: return false
        host.restoreTabFromBack(previous.tabIndex, previous.search)
        return true
    }

    private data class TabState(val tabIndex: Int, val search: String)

    companion object {
        private const val MAX_HISTORY = 32
    }
}
