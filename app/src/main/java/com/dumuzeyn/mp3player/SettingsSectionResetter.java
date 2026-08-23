package com.dumuzeyn.mp3player;

import android.content.SharedPreferences;
import java.util.Arrays;
import java.util.List;

final class SettingsSectionResetter {
    enum Section { APPEARANCE, FULL_PLAYER, ANIMATIONS }

    private SettingsSectionResetter() {
    }

    static void reset(MainActivityCore host, Section section) {
        SharedPreferences.Editor ui = host.getSharedPreferences("mp3_player_ui", 0).edit();
        for (String key : keys(section)) {
            ui.remove(key);
        }
        ui.commit();
        host.reloadUiPreferences();
    }

    private static List<String> keys(Section section) {
        switch (section) {
            case APPEARANCE:
                return Arrays.asList("theme", "customBg", "customFg",
                        "customSecondaryAccent", "customTextColor", "textOutlineEnabled",
                        "textOutlineColor", "mainBackgroundMode", "mainSolidBackground",
                        "mainGradientStart", "mainGradientEnd", "mainBackgroundMediaUri",
                        "mainBackgroundBlur", "cardOpacity", "songCardOpacity",
                        "favoriteCardOpacity", "playlistCardOpacity", "genreCardOpacity",
                        "artistCardOpacity", "albumCardOpacity", "settingsCardOpacity",
                        "circularCovers");
            case FULL_PLAYER:
                return Arrays.asList("playerBackgroundMode", "playerSolidBackground",
                        "playerGradientStart", "playerGradientEnd", "playerBackgroundMediaUri",
                        "playerBackgroundBlur", "fullPlayerRotationSpeed");
            case ANIMATIONS:
                return Arrays.asList("animations", "particlesEnabled", "particleFrequency",
                        "particleSize", "particleLifetime", "particlePrimaryColor",
                        "particleSecondaryColor", "playlistTickerSpeed");
            default:
                return java.util.Collections.emptyList();
        }
    }
}
