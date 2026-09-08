package com.dumuzeyn.mp3player

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import kotlin.math.max

/** Loads and persists UI preferences without coupling them to Activity lifecycle code. */
internal class UiPreferencesStore(private val host: MainActivityCore) {
    fun load() {
        val preferences = preferences()
        host.themeController.load(preferences)
        with(host.appearanceState) {
            animations = preferences.getBoolean(ANIMATIONS, true)
            language = preferences.getString(LANGUAGE, "ru") ?: "ru"
            if (language != "en" && language != "ru") language = "ru"
            customTimerMinutes = preferences.getInt(CUSTOM_TIMER, 10)
            resumeWindowMinutes = max(0, preferences.getInt(RESUME_WINDOW_MINUTES, 120))
            particleFrequency = preferences.getInt(PARTICLE_FREQUENCY, 45).coerceIn(10, 100)
            particleSize = preferences.getInt(PARTICLE_SIZE, 100).coerceIn(60, 150)
            particleLifetime = preferences.getInt(PARTICLE_LIFETIME, 100).coerceIn(50, 180)
            particlePrimaryColor = preferences.getInt(PARTICLE_PRIMARY_COLOR, 0)
            particleSecondaryColor = preferences.getInt(PARTICLE_SECONDARY_COLOR, 0)
            fullPlayerRotationSpeed = preferences.getInt(FULL_PLAYER_ROTATION_SPEED, 100)
                .coerceIn(25, 200)
            customTextColor = preferences.getInt(CUSTOM_TEXT_COLOR, 0)
            textOutlineEnabled = preferences.getBoolean(TEXT_OUTLINE_ENABLED, false)
            textOutlineColor = preferences.getInt(TEXT_OUTLINE_COLOR, 0)
            cardOpacity = preferences.getInt(CARD_OPACITY, 82).coerceIn(35, 100)
            songCardOpacity = preferences.getInt(SONG_CARD_OPACITY, cardOpacity)
                .coerceIn(35, 100)
            favoriteCardOpacity = preferences.getInt(FAVORITE_CARD_OPACITY, songCardOpacity)
                .coerceIn(35, 100)
            playlistCardOpacity = preferences.getInt(PLAYLIST_CARD_OPACITY, cardOpacity)
                .coerceIn(35, 100)
            genreCardOpacity = preferences.getInt(GENRE_CARD_OPACITY, cardOpacity)
                .coerceIn(35, 100)
            artistCardOpacity = preferences.getInt(ARTIST_CARD_OPACITY, cardOpacity)
                .coerceIn(35, 100)
            albumCardOpacity = preferences.getInt(ALBUM_CARD_OPACITY, cardOpacity)
                .coerceIn(35, 100)
            settingsCardOpacity = preferences.getInt(SETTINGS_CARD_OPACITY, cardOpacity)
                .coerceIn(35, 100)
            miniPlayerCardOpacity = preferences.getInt(MINI_PLAYER_CARD_OPACITY, cardOpacity)
                .coerceIn(35, 100)
            headerCardOpacity = preferences.getInt(HEADER_CARD_OPACITY, cardOpacity)
                .coerceIn(35, 100)
            dialogCardOpacity = preferences.getInt(DIALOG_CARD_OPACITY, cardOpacity)
                .coerceIn(35, 100)
            particlesEnabled = preferences.getBoolean(PARTICLES_ENABLED, true)
            circularCovers = preferences.getBoolean(CIRCULAR_COVERS, false)
            mainBackgroundMode = if (preferences.contains(MAIN_BACKGROUND_MODE)) {
                clampBackgroundMode(
                    preferences.getInt(
                        MAIN_BACKGROUND_MODE,
                        BackgroundSettingsController.MODE_SOLID,
                    ),
                )
            } else if (preferences.getBoolean(MAIN_GRADIENT, false)) {
                BackgroundSettingsController.MODE_GRADIENT
            } else {
                BackgroundSettingsController.MODE_SOLID
            }
            playerBackgroundMode = if (preferences.contains(PLAYER_BACKGROUND_MODE)) {
                clampBackgroundMode(
                    preferences.getInt(
                        PLAYER_BACKGROUND_MODE,
                        BackgroundSettingsController.MODE_GRADIENT,
                    ),
                )
            } else if (preferences.getBoolean(PLAYER_GRADIENT, true)) {
                BackgroundSettingsController.MODE_GRADIENT
            } else {
                BackgroundSettingsController.MODE_SOLID
            }
            mainSolidBackground = preferences.getInt(MAIN_SOLID_BACKGROUND, 0)
            playerSolidBackground = preferences.getInt(PLAYER_SOLID_BACKGROUND, 0)
            mainBackgroundMediaUri = preferences.getString(MAIN_BACKGROUND_MEDIA_URI, "") ?: ""
            playerBackgroundMediaUri = preferences.getString(PLAYER_BACKGROUND_MEDIA_URI, "") ?: ""
            mainBackgroundBlur = preferences.getInt(MAIN_BACKGROUND_BLUR, 20).coerceIn(0, 100)
            playerBackgroundBlur = preferences.getInt(PLAYER_BACKGROUND_BLUR, 20).coerceIn(0, 100)
            mainGradientStart = preferences.getInt(MAIN_GRADIENT_START, 0xff351b5d.toInt())
            mainGradientEnd = preferences.getInt(MAIN_GRADIENT_END, 0xff3a3013.toInt())
            playerGradientStart = preferences.getInt(PLAYER_GRADIENT_START, 0xff351b5d.toInt())
            playerGradientEnd = preferences.getInt(PLAYER_GRADIENT_END, 0xff3a3013.toInt())
        }
    }

    fun save() {
        with(host.appearanceState) {
            preferences().edit()
                .putString(THEME, themeMode)
                .putInt(CUSTOM_BG, customBg)
                .putInt(CUSTOM_FG, customFg)
                .putInt(CUSTOM_SECONDARY_ACCENT, customSecondaryAccent)
                .putBoolean(ANIMATIONS, animations)
                .putString(LANGUAGE, language)
                .putInt(CUSTOM_TIMER, customTimerMinutes)
                .putInt(RESUME_WINDOW_MINUTES, resumeWindowMinutes)
                .putInt(PARTICLE_FREQUENCY, particleFrequency)
                .putInt(PARTICLE_SIZE, particleSize)
                .putInt(PARTICLE_LIFETIME, particleLifetime)
                .putInt(PARTICLE_PRIMARY_COLOR, particlePrimaryColor)
                .putInt(PARTICLE_SECONDARY_COLOR, particleSecondaryColor)
                .putInt(FULL_PLAYER_ROTATION_SPEED, fullPlayerRotationSpeed)
                .putInt(CUSTOM_TEXT_COLOR, customTextColor)
                .putBoolean(TEXT_OUTLINE_ENABLED, textOutlineEnabled)
                .putInt(TEXT_OUTLINE_COLOR, textOutlineColor)
                .putInt(CARD_OPACITY, cardOpacity)
                .putInt(SONG_CARD_OPACITY, songCardOpacity)
                .putInt(FAVORITE_CARD_OPACITY, favoriteCardOpacity)
                .putInt(PLAYLIST_CARD_OPACITY, playlistCardOpacity)
                .putInt(GENRE_CARD_OPACITY, genreCardOpacity)
                .putInt(ARTIST_CARD_OPACITY, artistCardOpacity)
                .putInt(ALBUM_CARD_OPACITY, albumCardOpacity)
                .putInt(SETTINGS_CARD_OPACITY, settingsCardOpacity)
                .putInt(MINI_PLAYER_CARD_OPACITY, miniPlayerCardOpacity)
                .putInt(HEADER_CARD_OPACITY, headerCardOpacity)
                .putInt(DIALOG_CARD_OPACITY, dialogCardOpacity)
                .putBoolean(PARTICLES_ENABLED, particlesEnabled)
                .putBoolean(CIRCULAR_COVERS, circularCovers)
                .putInt(MAIN_BACKGROUND_MODE, mainBackgroundMode)
                .putInt(PLAYER_BACKGROUND_MODE, playerBackgroundMode)
                .putInt(MAIN_SOLID_BACKGROUND, mainSolidBackground)
                .putInt(PLAYER_SOLID_BACKGROUND, playerSolidBackground)
                .putString(MAIN_BACKGROUND_MEDIA_URI, mainBackgroundMediaUri)
                .putString(PLAYER_BACKGROUND_MEDIA_URI, playerBackgroundMediaUri)
                .putInt(MAIN_BACKGROUND_BLUR, mainBackgroundBlur)
                .putInt(PLAYER_BACKGROUND_BLUR, playerBackgroundBlur)
                .putInt(MAIN_GRADIENT_START, mainGradientStart)
                .putInt(MAIN_GRADIENT_END, mainGradientEnd)
                .putInt(PLAYER_GRADIENT_START, playerGradientStart)
                .putInt(PLAYER_GRADIENT_END, playerGradientEnd)
                .apply()
        }
    }

    private fun preferences(): SharedPreferences = host.getSharedPreferences(PREFS, 0)

    companion object {
        private const val PREFS = "mp3_player_ui"
        private const val THEME = "theme"
        private const val CUSTOM_BG = "customBg"
        private const val CUSTOM_FG = "customFg"
        private const val CUSTOM_SECONDARY_ACCENT = "customSecondaryAccent"
        private const val ANIMATIONS = "animations"
        private const val LANGUAGE = "language"
        private const val CUSTOM_TIMER = "customTimer"
        private const val RESUME_WINDOW_MINUTES = "resumeWindowMinutes"
        private const val PARTICLE_FREQUENCY = "particleFrequency"
        private const val PARTICLE_SIZE = "particleSize"
        private const val PARTICLE_LIFETIME = "particleLifetime"
        private const val PARTICLE_PRIMARY_COLOR = "particlePrimaryColor"
        private const val PARTICLE_SECONDARY_COLOR = "particleSecondaryColor"
        private const val FULL_PLAYER_ROTATION_SPEED = "fullPlayerRotationSpeed"
        private const val CUSTOM_TEXT_COLOR = "customTextColor"
        private const val TEXT_OUTLINE_ENABLED = "textOutlineEnabled"
        private const val TEXT_OUTLINE_COLOR = "textOutlineColor"
        private const val CARD_OPACITY = "cardOpacity"
        private const val SONG_CARD_OPACITY = "songCardOpacity"
        private const val FAVORITE_CARD_OPACITY = "favoriteCardOpacity"
        private const val PLAYLIST_CARD_OPACITY = "playlistCardOpacity"
        private const val GENRE_CARD_OPACITY = "genreCardOpacity"
        private const val ARTIST_CARD_OPACITY = "artistCardOpacity"
        private const val ALBUM_CARD_OPACITY = "albumCardOpacity"
        private const val SETTINGS_CARD_OPACITY = "settingsCardOpacity"
        private const val MINI_PLAYER_CARD_OPACITY = "miniPlayerCardOpacity"
        private const val HEADER_CARD_OPACITY = "headerCardOpacity"
        private const val DIALOG_CARD_OPACITY = "dialogCardOpacity"
        private const val PARTICLES_ENABLED = "particlesEnabled"
        private const val PLAYER_GRADIENT = "playerGradient"
        private const val CIRCULAR_COVERS = "circularCovers"
        private const val MAIN_GRADIENT = "mainGradient"
        private const val MAIN_GRADIENT_START = "mainGradientStart"
        private const val MAIN_GRADIENT_END = "mainGradientEnd"
        private const val PLAYER_GRADIENT_START = "playerGradientStart"
        private const val PLAYER_GRADIENT_END = "playerGradientEnd"
        private const val MAIN_BACKGROUND_MODE = "mainBackgroundMode"
        private const val PLAYER_BACKGROUND_MODE = "playerBackgroundMode"
        private const val MAIN_SOLID_BACKGROUND = "mainSolidBackground"
        private const val PLAYER_SOLID_BACKGROUND = "playerSolidBackground"
        private const val MAIN_BACKGROUND_MEDIA_URI = "mainBackgroundMediaUri"
        private const val PLAYER_BACKGROUND_MEDIA_URI = "playerBackgroundMediaUri"
        private const val MAIN_BACKGROUND_BLUR = "mainBackgroundBlur"
        private const val PLAYER_BACKGROUND_BLUR = "playerBackgroundBlur"

        @JvmStatic
        fun readThemeMode(context: Context): String =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(THEME, "light") ?: "light"

        @JvmStatic
        fun readCustomBackground(context: Context): Int =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(CUSTOM_BG, context.getColor(R.color.voltune_background_light))

        @JvmStatic
        fun readCustomForeground(context: Context): Int =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(CUSTOM_FG, Color.BLACK)

        @JvmStatic
        fun readCustomSecondaryAccent(context: Context): Int =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(CUSTOM_SECONDARY_ACCENT, context.getColor(R.color.voltune_secondary_light))

        @JvmStatic
        fun readResumeWindowMinutes(context: Context): Int = max(
            0,
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(RESUME_WINDOW_MINUTES, 120),
        )

        private fun clampBackgroundMode(value: Int): Int = value.coerceIn(
            BackgroundSettingsController.MODE_SOLID,
            BackgroundSettingsController.MODE_MEDIA,
        )
    }
}
