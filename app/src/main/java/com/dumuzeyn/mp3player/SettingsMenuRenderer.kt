package com.dumuzeyn.mp3player

class SettingsMenuRenderer(private val host: MainActivityCore) : MenuRenderer {
    override fun needsMiniSpacer(): Boolean = true

    override fun render() = host.renderSettings()
}
