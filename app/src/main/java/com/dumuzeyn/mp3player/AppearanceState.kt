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
    @JvmField var particlesEnabled = false
    @JvmField var playerBackgroundMode = BackgroundSettingsController.MODE_GRADIENT
    @JvmField var mainBackgroundMode = BackgroundSettingsController.MODE_GRADIENT
    @JvmField var mainSolidBackground = 0
    @JvmField var playerSolidBackground = 0
    @JvmField var mainBackgroundMediaUri = ""
    @JvmField var playerBackgroundMediaUri = ""
    @JvmField var mainBackgroundBlur = 20
    @JvmField var playerBackgroundBlur = 20
    @JvmField var circularCovers = false
    @JvmField var mainGradientStart = VoltunePalette.GRADIENT_BLUE
    @JvmField var mainGradientEnd = VoltunePalette.GRADIENT_PURPLE
    @JvmField var playerGradientStart = VoltunePalette.GRADIENT_BLUE
    @JvmField var playerGradientEnd = VoltunePalette.GRADIENT_PURPLE
    @JvmField var language = "ru"
    @JvmField var themeMode = "light"
    @JvmField var customBg = -1
    @JvmField var customFg = -16777216
    @JvmField var customSecondaryAccent = VoltunePalette.GOLD
    @JvmField var customTextColor = 0
    @JvmField var textOutlineEnabled = false
    @JvmField var textOutlineColor = 0
}
