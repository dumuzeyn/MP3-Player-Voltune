package com.dumuzeyn.mp3player;

import android.content.Context;
import android.content.SharedPreferences;

/** Loads and persists UI preferences without coupling them to Activity lifecycle code. */
final class UiPreferencesStore {
    private static final String PREFS = "mp3_player_ui";
    private static final String THEME = "theme";
    private static final String CUSTOM_BG = "customBg";
    private static final String CUSTOM_FG = "customFg";
    private static final String CUSTOM_SECONDARY_ACCENT = "customSecondaryAccent";
    private static final String ANIMATIONS = "animations";
    private static final String LANGUAGE = "language";
    private static final String CUSTOM_TIMER = "customTimer";
    private static final String RESUME_WINDOW_MINUTES = "resumeWindowMinutes";
    private static final String PARTICLE_FREQUENCY = "particleFrequency";
    private static final String PARTICLE_SIZE = "particleSize";
    private static final String PARTICLE_LIFETIME = "particleLifetime";
    private static final String PARTICLE_PRIMARY_COLOR = "particlePrimaryColor";
    private static final String PARTICLE_SECONDARY_COLOR = "particleSecondaryColor";
    private static final String FULL_PLAYER_ROTATION_SPEED = "fullPlayerRotationSpeed";
    private static final String CUSTOM_TEXT_COLOR = "customTextColor";
    private static final String TEXT_OUTLINE_ENABLED = "textOutlineEnabled";
    private static final String TEXT_OUTLINE_COLOR = "textOutlineColor";
    private static final String CARD_OPACITY = "cardOpacity";
    private static final String SONG_CARD_OPACITY = "songCardOpacity";
    private static final String FAVORITE_CARD_OPACITY = "favoriteCardOpacity";
    private static final String PLAYLIST_CARD_OPACITY = "playlistCardOpacity";
    private static final String GENRE_CARD_OPACITY = "genreCardOpacity";
    private static final String ARTIST_CARD_OPACITY = "artistCardOpacity";
    private static final String ALBUM_CARD_OPACITY = "albumCardOpacity";
    private static final String SETTINGS_CARD_OPACITY = "settingsCardOpacity";
    private static final String MINI_PLAYER_CARD_OPACITY = "miniPlayerCardOpacity";
    private static final String HEADER_CARD_OPACITY = "headerCardOpacity";
    private static final String DIALOG_CARD_OPACITY = "dialogCardOpacity";
    private static final String PARTICLES_ENABLED = "particlesEnabled";
    private static final String PLAYER_GRADIENT = "playerGradient";
    private static final String CIRCULAR_COVERS = "circularCovers";
    private static final String MAIN_GRADIENT = "mainGradient";
    private static final String MAIN_GRADIENT_START = "mainGradientStart";
    private static final String MAIN_GRADIENT_END = "mainGradientEnd";
    private static final String PLAYER_GRADIENT_START = "playerGradientStart";
    private static final String PLAYER_GRADIENT_END = "playerGradientEnd";
    private static final String MAIN_BACKGROUND_MODE = "mainBackgroundMode";
    private static final String PLAYER_BACKGROUND_MODE = "playerBackgroundMode";
    private static final String MAIN_SOLID_BACKGROUND = "mainSolidBackground";
    private static final String PLAYER_SOLID_BACKGROUND = "playerSolidBackground";
    private static final String MAIN_BACKGROUND_MEDIA_URI = "mainBackgroundMediaUri";
    private static final String PLAYER_BACKGROUND_MEDIA_URI = "playerBackgroundMediaUri";
    private static final String MAIN_BACKGROUND_BLUR = "mainBackgroundBlur";
    private static final String PLAYER_BACKGROUND_BLUR = "playerBackgroundBlur";

    private final MainActivityCore host;

    UiPreferencesStore(MainActivityCore host) {
        this.host = host;
    }

    static String readThemeMode(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(THEME, "light");
    }

    static int readCustomBackground(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(CUSTOM_BG, context.getColor(R.color.voltune_background_light));
    }

    static int readCustomForeground(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(CUSTOM_FG, android.graphics.Color.BLACK);
    }

    static int readCustomSecondaryAccent(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(CUSTOM_SECONDARY_ACCENT,
                        context.getColor(R.color.voltune_secondary_light));
    }

    static int readResumeWindowMinutes(Context context) {
        return Math.max(0, context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(RESUME_WINDOW_MINUTES, 120));
    }

    void load() {
        SharedPreferences preferences = preferences();
        host.themeController.load(preferences);
        host.appearanceState.animations = preferences.getBoolean(ANIMATIONS, true);
        host.appearanceState.language = preferences.getString(LANGUAGE, "ru");
        if (!"en".equals(host.appearanceState.language) && !"ru".equals(host.appearanceState.language)) {
            host.appearanceState.language = "ru";
        }
        host.appearanceState.customTimerMinutes = preferences.getInt(CUSTOM_TIMER, 10);
        host.appearanceState.resumeWindowMinutes = Math.max(0, preferences.getInt(RESUME_WINDOW_MINUTES, 120));
        host.appearanceState.particleFrequency = clamp(preferences.getInt(PARTICLE_FREQUENCY, 45), 10, 100);
        host.appearanceState.particleSize = clamp(preferences.getInt(PARTICLE_SIZE, 100), 60, 150);
        host.appearanceState.particleLifetime = clamp(preferences.getInt(PARTICLE_LIFETIME, 100), 50, 180);
        host.appearanceState.particlePrimaryColor = preferences.getInt(PARTICLE_PRIMARY_COLOR, 0);
        host.appearanceState.particleSecondaryColor = preferences.getInt(PARTICLE_SECONDARY_COLOR, 0);
        host.appearanceState.fullPlayerRotationSpeed = clamp(
                preferences.getInt(FULL_PLAYER_ROTATION_SPEED, 100), 25, 200);
        host.appearanceState.customTextColor = preferences.getInt(CUSTOM_TEXT_COLOR, 0);
        host.appearanceState.textOutlineEnabled = preferences.getBoolean(TEXT_OUTLINE_ENABLED, false);
        host.appearanceState.textOutlineColor = preferences.getInt(TEXT_OUTLINE_COLOR, 0);
        host.appearanceState.cardOpacity = clamp(preferences.getInt(CARD_OPACITY, 82), 35, 100);
        host.appearanceState.songCardOpacity = clamp(
                preferences.getInt(SONG_CARD_OPACITY, host.appearanceState.cardOpacity), 35, 100);
        host.appearanceState.favoriteCardOpacity = clamp(
                preferences.getInt(FAVORITE_CARD_OPACITY, host.appearanceState.songCardOpacity), 35, 100);
        host.appearanceState.playlistCardOpacity = clamp(
                preferences.getInt(PLAYLIST_CARD_OPACITY, host.appearanceState.cardOpacity), 35, 100);
        host.appearanceState.genreCardOpacity = clamp(
                preferences.getInt(GENRE_CARD_OPACITY, host.appearanceState.cardOpacity), 35, 100);
        host.appearanceState.artistCardOpacity = clamp(
                preferences.getInt(ARTIST_CARD_OPACITY, host.appearanceState.cardOpacity), 35, 100);
        host.appearanceState.albumCardOpacity = clamp(
                preferences.getInt(ALBUM_CARD_OPACITY, host.appearanceState.cardOpacity), 35, 100);
        host.appearanceState.settingsCardOpacity = clamp(
                preferences.getInt(SETTINGS_CARD_OPACITY, host.appearanceState.cardOpacity), 35, 100);
        host.appearanceState.miniPlayerCardOpacity = clamp(
                preferences.getInt(MINI_PLAYER_CARD_OPACITY, host.appearanceState.cardOpacity), 35, 100);
        host.appearanceState.headerCardOpacity = clamp(
                preferences.getInt(HEADER_CARD_OPACITY, host.appearanceState.cardOpacity), 35, 100);
        host.appearanceState.dialogCardOpacity = clamp(
                preferences.getInt(DIALOG_CARD_OPACITY, host.appearanceState.cardOpacity), 35, 100);
        host.appearanceState.particlesEnabled = preferences.getBoolean(PARTICLES_ENABLED, true);
        host.appearanceState.circularCovers = preferences.getBoolean(CIRCULAR_COVERS, false);
        host.appearanceState.mainBackgroundMode = preferences.contains(MAIN_BACKGROUND_MODE)
                ? clampBackgroundMode(preferences.getInt(MAIN_BACKGROUND_MODE,
                        BackgroundSettingsController.MODE_SOLID))
                : preferences.getBoolean(MAIN_GRADIENT, false)
                        ? BackgroundSettingsController.MODE_GRADIENT
                        : BackgroundSettingsController.MODE_SOLID;
        host.appearanceState.playerBackgroundMode = preferences.contains(PLAYER_BACKGROUND_MODE)
                ? clampBackgroundMode(preferences.getInt(PLAYER_BACKGROUND_MODE,
                        BackgroundSettingsController.MODE_GRADIENT))
                : preferences.getBoolean(PLAYER_GRADIENT, true)
                        ? BackgroundSettingsController.MODE_GRADIENT
                        : BackgroundSettingsController.MODE_SOLID;
        host.appearanceState.mainSolidBackground = preferences.getInt(MAIN_SOLID_BACKGROUND, 0);
        host.appearanceState.playerSolidBackground = preferences.getInt(PLAYER_SOLID_BACKGROUND, 0);
        host.appearanceState.mainBackgroundMediaUri = preferences.getString(MAIN_BACKGROUND_MEDIA_URI, "");
        host.appearanceState.playerBackgroundMediaUri = preferences.getString(PLAYER_BACKGROUND_MEDIA_URI, "");
        host.appearanceState.mainBackgroundBlur = clamp(preferences.getInt(MAIN_BACKGROUND_BLUR, 20), 0, 100);
        host.appearanceState.playerBackgroundBlur = clamp(preferences.getInt(PLAYER_BACKGROUND_BLUR, 20), 0, 100);
        host.appearanceState.mainGradientStart = preferences.getInt(MAIN_GRADIENT_START, 0xff351b5d);
        host.appearanceState.mainGradientEnd = preferences.getInt(MAIN_GRADIENT_END, 0xff3a3013);
        host.appearanceState.playerGradientStart = preferences.getInt(PLAYER_GRADIENT_START, 0xff351b5d);
        host.appearanceState.playerGradientEnd = preferences.getInt(PLAYER_GRADIENT_END, 0xff3a3013);
    }

    void save() {
        preferences().edit()
                .putString(THEME, host.appearanceState.themeMode)
                .putInt(CUSTOM_BG, host.appearanceState.customBg)
                .putInt(CUSTOM_FG, host.appearanceState.customFg)
                .putInt(CUSTOM_SECONDARY_ACCENT, host.appearanceState.customSecondaryAccent)
                .putBoolean(ANIMATIONS, host.appearanceState.animations)
                .putString(LANGUAGE, host.appearanceState.language)
                .putInt(CUSTOM_TIMER, host.appearanceState.customTimerMinutes)
                .putInt(RESUME_WINDOW_MINUTES, host.appearanceState.resumeWindowMinutes)
                .putInt(PARTICLE_FREQUENCY, host.appearanceState.particleFrequency)
                .putInt(PARTICLE_SIZE, host.appearanceState.particleSize)
                .putInt(PARTICLE_LIFETIME, host.appearanceState.particleLifetime)
                .putInt(PARTICLE_PRIMARY_COLOR, host.appearanceState.particlePrimaryColor)
                .putInt(PARTICLE_SECONDARY_COLOR, host.appearanceState.particleSecondaryColor)
                .putInt(FULL_PLAYER_ROTATION_SPEED, host.appearanceState.fullPlayerRotationSpeed)
                .putInt(CUSTOM_TEXT_COLOR, host.appearanceState.customTextColor)
                .putBoolean(TEXT_OUTLINE_ENABLED, host.appearanceState.textOutlineEnabled)
                .putInt(TEXT_OUTLINE_COLOR, host.appearanceState.textOutlineColor)
                .putInt(CARD_OPACITY, host.appearanceState.cardOpacity)
                .putInt(SONG_CARD_OPACITY, host.appearanceState.songCardOpacity)
                .putInt(FAVORITE_CARD_OPACITY, host.appearanceState.favoriteCardOpacity)
                .putInt(PLAYLIST_CARD_OPACITY, host.appearanceState.playlistCardOpacity)
                .putInt(GENRE_CARD_OPACITY, host.appearanceState.genreCardOpacity)
                .putInt(ARTIST_CARD_OPACITY, host.appearanceState.artistCardOpacity)
                .putInt(ALBUM_CARD_OPACITY, host.appearanceState.albumCardOpacity)
                .putInt(SETTINGS_CARD_OPACITY, host.appearanceState.settingsCardOpacity)
                .putInt(MINI_PLAYER_CARD_OPACITY, host.appearanceState.miniPlayerCardOpacity)
                .putInt(HEADER_CARD_OPACITY, host.appearanceState.headerCardOpacity)
                .putInt(DIALOG_CARD_OPACITY, host.appearanceState.dialogCardOpacity)
                .putBoolean(PARTICLES_ENABLED, host.appearanceState.particlesEnabled)
                .putBoolean(CIRCULAR_COVERS, host.appearanceState.circularCovers)
                .putInt(MAIN_BACKGROUND_MODE, host.appearanceState.mainBackgroundMode)
                .putInt(PLAYER_BACKGROUND_MODE, host.appearanceState.playerBackgroundMode)
                .putInt(MAIN_SOLID_BACKGROUND, host.appearanceState.mainSolidBackground)
                .putInt(PLAYER_SOLID_BACKGROUND, host.appearanceState.playerSolidBackground)
                .putString(MAIN_BACKGROUND_MEDIA_URI, host.appearanceState.mainBackgroundMediaUri)
                .putString(PLAYER_BACKGROUND_MEDIA_URI, host.appearanceState.playerBackgroundMediaUri)
                .putInt(MAIN_BACKGROUND_BLUR, host.appearanceState.mainBackgroundBlur)
                .putInt(PLAYER_BACKGROUND_BLUR, host.appearanceState.playerBackgroundBlur)
                .putInt(MAIN_GRADIENT_START, host.appearanceState.mainGradientStart)
                .putInt(MAIN_GRADIENT_END, host.appearanceState.mainGradientEnd)
                .putInt(PLAYER_GRADIENT_START, host.appearanceState.playerGradientStart)
                .putInt(PLAYER_GRADIENT_END, host.appearanceState.playerGradientEnd)
                .apply();
    }

    private SharedPreferences preferences() {
        return host.getSharedPreferences(PREFS, 0);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static int clampBackgroundMode(int value) {
        return clamp(value, BackgroundSettingsController.MODE_SOLID,
                BackgroundSettingsController.MODE_MEDIA);
    }
}
