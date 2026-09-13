package com.dumuzeyn.mp3player

/** Sanitized user-defined visibility and order for the library wheel. */
internal class MenuConfigurationModel(
    requestedOrder: Collection<Int> = LibraryTabs.ALL,
    requestedEnabled: Collection<Int> = LibraryTabs.ALL,
) {
    private val order = sanitizeOrder(requestedOrder).toMutableList()
    private val enabled = requestedEnabled
        .filterTo(LinkedHashSet()) { it in LibraryTabs.ALL }
        .apply { add(LibraryTabs.SETTINGS) }

    fun orderedTabs(): List<Int> = order.toList()

    fun visibleTabs(): List<Int> = order.filter(enabled::contains)

    fun visibleCount(): Int = enabled.size

    fun isVisible(tabId: Int): Boolean = tabId in enabled

    fun setEnabled(tabId: Int, value: Boolean): Boolean {
        if (tabId !in LibraryTabs.ALL || tabId == LibraryTabs.SETTINGS && !value) return false
        return if (value) enabled.add(tabId) else enabled.remove(tabId)
    }

    fun move(from: Int, to: Int): Boolean {
        if (from !in order.indices || to !in order.indices || from == to) return false
        val tab = order.removeAt(from)
        order.add(to, tab)
        return true
    }

    fun adjacent(currentTab: Int, direction: Int): Int {
        val visible = visibleTabs()
        if (visible.isEmpty()) return LibraryTabs.SETTINGS
        val current = visible.indexOf(currentTab).takeIf { it >= 0 } ?: 0
        return if (direction >= 0) {
            visible[(current + 1) % visible.size]
        } else {
            visible[(current - 1 + visible.size) % visible.size]
        }
    }

    fun direction(currentTab: Int, targetTab: Int): Int {
        val visible = visibleTabs()
        val current = visible.indexOf(currentTab)
        val target = visible.indexOf(targetTab)
        if (current < 0 || target < 0 || current == target) return 1
        val forward = (target - current + visible.size) % visible.size
        val backward = (current - target + visible.size) % visible.size
        return if (forward <= backward) 1 else -1
    }

    fun visibleOrFirst(tabId: Int): Int =
        tabId.takeIf(::isVisible) ?: visibleTabs().firstOrNull() ?: LibraryTabs.SETTINGS

    private companion object {
        fun sanitizeOrder(requested: Collection<Int>): List<Int> = buildList {
            requested.forEach { tab -> if (tab in LibraryTabs.ALL && tab !in this) add(tab) }
            LibraryTabs.ALL.forEach { tab -> if (tab !in this) add(tab) }
        }
    }
}
