package com.dumuzeyn.mp3player

object SettingsSectionResetter {
    enum class Section { APPEARANCE, FULL_PLAYER, ANIMATIONS }

    @JvmStatic
    fun reset(host: MainActivityCore, section: Section) {
        val editor = host.getSharedPreferences("mp3_player_ui", 0).edit()
        keys(section).forEach(editor::remove)
        editor.commit()
        host.reloadUiPreferences()
    }

    private fun keys(section: Section): List<String> = when (section) {
        Section.APPEARANCE -> listOf(
            "theme", "customBg", "customFg", "customSecondaryAccent", "customTextColor",
            "textOutlineEnabled", "textOutlineColor", "mainBackgroundMode",
            "mainSolidBackground", "mainGradientStart", "mainGradientEnd",
            "mainBackgroundMediaUri", "mainBackgroundBlur", "cardOpacity", "songCardOpacity",
            "favoriteCardOpacity", "playlistCardOpacity", "genreCardOpacity",
            "artistCardOpacity", "albumCardOpacity", "settingsCardOpacity", "circularCovers",
        )

        Section.FULL_PLAYER -> listOf(
            "playerBackgroundMode", "playerSolidBackground", "playerGradientStart",
            "playerGradientEnd", "playerBackgroundMediaUri", "playerBackgroundBlur",
            "fullPlayerRotationSpeed",
        )

        Section.ANIMATIONS -> listOf(
            "animations", "particlesEnabled", "particleFrequency", "particleSize",
            "particleLifetime", "particlePrimaryColor", "particleSecondaryColor",
        )
    }
}
