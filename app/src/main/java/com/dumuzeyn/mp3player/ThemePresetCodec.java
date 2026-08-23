package com.dumuzeyn.mp3player;

import android.content.SharedPreferences;
import java.util.Arrays;
import java.util.List;
import org.json.JSONObject;

final class ThemePresetCodec {
    static final int MAX_BYTES = 64 * 1024;
    private static final int SCHEMA_VERSION = 1;
    private static final List<String> STRING_KEYS = Arrays.asList("theme",
            "mainBackgroundMediaUri", "playerBackgroundMediaUri");
    private static final List<String> BOOLEAN_KEYS = Arrays.asList("textOutlineEnabled",
            "circularCovers", "particlesEnabled", "animations");
    private static final List<String> INTEGER_KEYS = Arrays.asList("customBg", "customFg",
            "customSecondaryAccent", "customTextColor", "textOutlineColor",
            "mainBackgroundMode", "playerBackgroundMode", "mainSolidBackground",
            "playerSolidBackground", "mainGradientStart", "mainGradientEnd",
            "playerGradientStart", "playerGradientEnd", "mainBackgroundBlur",
            "playerBackgroundBlur", "cardOpacity", "songCardOpacity",
            "favoriteCardOpacity", "playlistCardOpacity", "genreCardOpacity",
            "artistCardOpacity", "albumCardOpacity", "settingsCardOpacity",
            "particleFrequency", "particleSize", "particleLifetime",
            "particlePrimaryColor", "particleSecondaryColor", "fullPlayerRotationSpeed");

    private ThemePresetCodec() {
    }

    static String encode(SharedPreferences preferences) throws Exception {
        JSONObject root = new JSONObject();
        root.put("schemaVersion", SCHEMA_VERSION);
        JSONObject values = new JSONObject();
        for (String key : STRING_KEYS) {
            if (preferences.contains(key)) values.put(key, preferences.getString(key, ""));
        }
        for (String key : BOOLEAN_KEYS) {
            if (preferences.contains(key)) values.put(key, preferences.getBoolean(key, false));
        }
        for (String key : INTEGER_KEYS) {
            if (preferences.contains(key)) values.put(key, preferences.getInt(key, 0));
        }
        root.put("values", values);
        return root.toString(2);
    }

    static void decodeInto(String encoded, SharedPreferences preferences) throws Exception {
        if (encoded == null || encoded.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                > MAX_BYTES) {
            throw new IllegalArgumentException("Theme file is empty or too large");
        }
        JSONObject root = new JSONObject(encoded);
        if (root.optInt("schemaVersion", -1) != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported theme schema");
        }
        JSONObject values = root.optJSONObject("values");
        if (values == null || values.length() > 100) {
            throw new IllegalArgumentException("Theme values are missing");
        }
        SharedPreferences.Editor editor = preferences.edit();
        for (String key : STRING_KEYS) {
            if (values.has(key)) {
                String value = values.getString(key);
                if (value.length() > 4096) throw new IllegalArgumentException("Value too long");
                editor.putString(key, value);
            }
        }
        for (String key : BOOLEAN_KEYS) {
            if (values.has(key)) editor.putBoolean(key, values.getBoolean(key));
        }
        for (String key : INTEGER_KEYS) {
            if (values.has(key)) editor.putInt(key, values.getInt(key));
        }
        editor.commit();
    }
}
