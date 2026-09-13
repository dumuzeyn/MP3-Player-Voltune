package com.dumuzeyn.mp3player

/** Current library destination and transient tab transition state. */
internal class NavigationState : TabTransitionCoordinator.Navigation {
    @JvmField var tabIndex = 0
    @JvmField var preferredTabDirection = 0
    @JvmField var tabAnimating = false
    @JvmField var search = ""
    @JvmField var fullPlayerOpening = false
    @JvmField var songRenderGeneration = 0
    @JvmField var renderingTabPreview = false

    override fun selectedTab(): Int = tabIndex

    override fun setSelectedTab(value: Int) {
        tabIndex = value.coerceAtLeast(0)
    }

    override fun searchQuery(): String = search

    override fun setSearchQuery(value: String?) {
        search = value.orEmpty()
    }

    override fun setPreferredDirection(value: Int) {
        preferredTabDirection = value
    }

    override fun setTransitionRunning(value: Boolean) {
        tabAnimating = value
    }
}
