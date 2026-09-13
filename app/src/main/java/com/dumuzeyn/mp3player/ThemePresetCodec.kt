package com.dumuzeyn.mp3player

import android.content.SharedPreferences
import org.json.JSONObject

object ThemePresetCodec {
    const val MAX_BYTES = 64 * 1024
    private const val SCHEMA_VERSION = 1
    private val STRING_KEYS = listOf("theme", "mainBackgroundMediaUri", "playerBackgroundMediaUri")
    private val BOOLEAN_KEYS = listOf(
        "textOutlineEnabled", "circularCovers", "particlesEnabled", "animations",
    )
    private val INTEGER_KEYS = listOf(
        "customBg", "customFg", "customSecondaryAccent", "customTextColor",
        "textOutlineColor", "mainBackgroundMode", "playerBackgroundMode",
        "mainSolidBackground", "playerSolidBackground", "mainGradientStart", "mainGradientEnd",
        "playerGradientStart", "playerGradientEnd", "mainBackgroundBlur",
        "playerBackgroundBlur", "cardOpacity", "songCardOpacity", "favoriteCardOpacity",
        "playlistCardOpacity", "genreCardOpacity", "artistCardOpacity", "albumCardOpacity",
        "settingsCardOpacity", "particleFrequency", "particleSize", "particleLifetime",
        "particlePrimaryColor", "particleSecondaryColor", "fullPlayerRotationSpeed",
    )

    @JvmStatic
    fun encode(preferences: SharedPreferences): String {
        val values = JSONObject()
        STRING_KEYS.forEach { key ->
            if (preferences.contains(key)) values.put(key, preferences.getString(key, ""))
        }
        BOOLEAN_KEYS.forEach { key ->
            if (preferences.contains(key)) values.put(key, preferences.getBoolean(key, false))
        }
        INTEGER_KEYS.forEach { key ->
            if (preferences.contains(key)) values.put(key, preferences.getInt(key, 0))
        }
        return JSONObject()
            .put("schemaVersion", SCHEMA_VERSION)
            .put("values", values)
            .toString(2)
    }

    @JvmStatic
    fun decodeInto(encoded: String?, preferences: SharedPreferences) {
        if (encoded == null || encoded.toByteArray(Charsets.UTF_8).size > MAX_BYTES) {
            throw IllegalArgumentException("Theme file is empty or too large")
        }
        val root = JSONObject(encoded)
        if (root.optInt("schemaVersion", -1) != SCHEMA_VERSION) {
            throw IllegalArgumentException("Unsupported theme schema")
        }
        val values = root.optJSONObject("values")
        if (values == null || values.length() > 100) {
            throw IllegalArgumentException("Theme values are missing")
        }

        val editor = preferences.edit()
        STRING_KEYS.forEach { key ->
            if (values.has(key)) {
                val value = values.getString(key)
                if (value.length > 4096) throw IllegalArgumentException("Value too long")
                editor.putString(key, value)
            }
        }
        BOOLEAN_KEYS.forEach { key ->
            if (values.has(key)) editor.putBoolean(key, values.getBoolean(key))
        }
        INTEGER_KEYS.forEach { key ->
            if (values.has(key)) editor.putInt(key, values.getInt(key))
        }
        editor.commit()
    }
}
