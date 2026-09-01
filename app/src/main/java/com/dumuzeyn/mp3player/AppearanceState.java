package com.dumuzeyn.mp3player;

/** Persisted visual and interaction preferences. */
final class AppearanceState {
    int customTimerMinutes = 10;
    int resumeWindowMinutes = 120;
    int particleFrequency = 45;
    int particleSize = 100;
    int particleLifetime = 100;
    int particlePrimaryColor;
    int particleSecondaryColor;
    int fullPlayerRotationSpeed = 100;
    int cardOpacity = 82;
    int songCardOpacity = 82;
    int favoriteCardOpacity = 82;
    int playlistCardOpacity = 82;
    int genreCardOpacity = 82;
    int artistCardOpacity = 82;
    int albumCardOpacity = 82;
    int settingsCardOpacity = 82;
    int miniPlayerCardOpacity = 82;
    int headerCardOpacity = 82;
    int dialogCardOpacity = 82;
    boolean dark;
    boolean animations = true;
    boolean particlesEnabled = true;
    int playerBackgroundMode = BackgroundSettingsController.MODE_GRADIENT;
    int mainBackgroundMode = BackgroundSettingsController.MODE_SOLID;
    int mainSolidBackground;
    int playerSolidBackground;
    String mainBackgroundMediaUri = "";
    String playerBackgroundMediaUri = "";
    int mainBackgroundBlur = 20;
    int playerBackgroundBlur = 20;
    boolean circularCovers;
    int mainGradientStart = 0xff351b5d;
    int mainGradientEnd = 0xff3a3013;
    int playerGradientStart = 0xff351b5d;
    int playerGradientEnd = 0xff3a3013;
    String language = "ru";
    String themeMode = "light";
    int customBg = -1;
    int customFg = -16777216;
    int customSecondaryAccent = 0xffffd000;
    int customTextColor;
    boolean textOutlineEnabled;
    int textOutlineColor;
}
