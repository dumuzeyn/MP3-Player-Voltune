package com.dumuzeyn.mp3player

/** Persists the menu wheel configuration independently from visual preferences. */
internal class MenuConfigurationController(private val host: MainActivityCore) {
    private var model = MenuConfigurationModel()

    fun load() {
        val preferences = host.getSharedPreferences(PREFS, 0)
        val order = decode(preferences.getString(ORDER, null))
        val enabled = if (preferences.contains(ENABLED)) {
            decode(preferences.getString(ENABLED, null))
        } else {
            LibraryTabs.ALL
        }
        model = MenuConfigurationModel(order, enabled)
        save()
    }

    fun orderedTabs(): List<Int> = model.orderedTabs()

    fun visibleTabs(): List<Int> = model.visibleTabs()

    fun visibleCount(): Int = model.visibleCount()

    fun isVisible(tabId: Int): Boolean = model.isVisible(tabId)

    fun setEnabled(tabId: Int, enabled: Boolean): Boolean {
        if (!model.setEnabled(tabId, enabled)) return false
        save()
        return true
    }

    fun move(from: Int, to: Int): Boolean {
        if (!model.move(from, to)) return false
        save()
        return true
    }

    fun adjacent(currentTab: Int, direction: Int): Int = model.adjacent(currentTab, direction)

    fun direction(currentTab: Int, targetTab: Int): Int = model.direction(currentTab, targetTab)

    fun visibleOrFirst(tabId: Int): Int = model.visibleOrFirst(tabId)

    private fun save() {
        host.getSharedPreferences(PREFS, 0).edit()
            .putString(ORDER, encode(model.orderedTabs()))
            .putString(ENABLED, encode(model.visibleTabs()))
            .apply()
    }

    private fun decode(raw: String?): List<Int> = raw.orEmpty()
        .split(',')
        .mapNotNull(String::toIntOrNull)

    private fun encode(tabs: List<Int>): String = tabs.joinToString(",")

    private companion object {
        const val PREFS = "mp3_player_ui"
        const val ORDER = "menuOrder"
        const val ENABLED = "enabledMenus"
    }
}
