package com.dumuzeyn.mp3player

/** Persisted visual and interaction preferences. */
class AppearanceState {
    @JvmField var customTimerMinutes = 10
    @JvmField var resumeWindowMinutes = 120
    @JvmField var particleFrequency = 45
    @JvmField var particleSize = 100
    @JvmField var particleLifetime = 100
    @JvmField var particlePrimaryColor = 0
    @JvmField var particleSecondaryColor = 0
    @JvmField var fullPlayerRotationSpeed = 100
    @JvmField var cardOpacity = 82
    @JvmField var songCardOpacity = 82
    @JvmField var favoriteCardOpacity = 82
    @JvmField var playlistCardOpacity = 82
    @JvmField var genreCardOpacity = 82
    @JvmField var artistCardOpacity = 82
    @JvmField var albumCardOpacity = 82
    @JvmField var settingsCardOpacity = 82
    @JvmField var miniPlayerCardOpacity = 82
    @JvmField var headerCardOpacity = 82
    @JvmField var dialogCardOpacity = 82
    @JvmField var dark = false
    @JvmField var animations = true
    @JvmField var particlesEnabled = true
    @JvmField var playerBackgroundMode = BackgroundSettingsController.MODE_GRADIENT
    @JvmField var mainBackgroundMode = BackgroundSettingsController.MODE_SOLID
    @JvmField var mainSolidBackground = 0
    @JvmField var playerSolidBackground = 0
    @JvmField var mainBackgroundMediaUri = ""
    @JvmField var playerBackgroundMediaUri = ""
    @JvmField var mainBackgroundBlur = 20
    @JvmField var playerBackgroundBlur = 20
    @JvmField var circularCovers = false
    @JvmField var mainGradientStart = 0xff351b5d.toInt()
    @JvmField var mainGradientEnd = 0xff3a3013.toInt()
    @JvmField var playerGradientStart = 0xff351b5d.toInt()
    @JvmField var playerGradientEnd = 0xff3a3013.toInt()
    @JvmField var language = "ru"
    @JvmField var themeMode = "light"
    @JvmField var customBg = -1
    @JvmField var customFg = -16777216
    @JvmField var customSecondaryAccent = 0xffffd000.toInt()
    @JvmField var customTextColor = 0
    @JvmField var textOutlineEnabled = false
    @JvmField var textOutlineColor = 0
}
