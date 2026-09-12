package com.dumuzeyn.mp3player

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.AudioEffect
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

internal class AudioEffectsManager(context: Context) {
    private val context = context.applicationContext
    private var equalizer: Equalizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    fun adjustedNormalizationGainDb(analyzedGainDb: Float): Float {
        val preferences = context.getSharedPreferences(EqualizerController.PREFS, 0)
        if (!preferences.getBoolean(VolumeLevelingController.ENABLED, false)) return 0f
        var maximumBandBoost = 0
        if (preferences.getBoolean(EqualizerController.ENABLED, false)) {
            for (band in 0 until EqualizerController.BAND_COUNT) {
                maximumBandBoost = max(
                    maximumBandBoost,
                    preferences.getInt(EqualizerController.BAND_PREFIX + band, 0),
                )
            }
        }
        val mode = LoudnessLevelingMode.fromPreference(
            preferences.getString(LoudnessLevelingMode.PREFERENCE, null),
            preferences.getBoolean(TrackLoudnessNormalizer.REDUCE_ONLY, false),
        )
        return mode.reserveEqualizerHeadroom(analyzedGainDb, maximumBandBoost)
    }

    fun apply(audioSessionId: Int, normalizationGainDb: Float) {
        release()
        if (audioSessionId <= 0) return
        val preferences = context.getSharedPreferences(EqualizerController.PREFS, 0)
        if (preferences.getBoolean(EqualizerController.ENABLED, false)) {
            applyEqualizer(preferences, audioSessionId)
        }
        if (preferences.getBoolean(VolumeLevelingController.ENABLED, false)) {
            applyFixedLoudnessGain(audioSessionId, normalizationGainDb)
        }
    }

    fun release() {
        equalizer?.let(::releaseEffect)
        equalizer = null
        loudnessEnhancer?.let(::releaseEffect)
        loudnessEnhancer = null
    }

    @Suppress("DEPRECATION")
    private fun applyEqualizer(preferences: SharedPreferences, audioSessionId: Int) {
        try {
            val effect = Equalizer(0, audioSessionId)
            val levelRange = effect.bandLevelRange
            val bandCount = effect.numberOfBands
            for (band in 0 until bandCount) {
                val profileIndex = if (bandCount <= 1) {
                    0
                } else {
                    (band * (EqualizerController.BAND_COUNT - 1f) / (bandCount - 1f)).roundToInt()
                }
                val db = preferences.getInt(EqualizerController.BAND_PREFIX + profileIndex, 0)
                val milliBel = max(levelRange[0].toInt(), min(levelRange[1].toInt(), db * 100))
                effect.setBandLevel(band.toShort(), milliBel.toShort())
            }
            effect.enabled = true
            equalizer = effect
            VoltuneLog.info("equalizer_applied bands=$bandCount")
        } catch (error: RuntimeException) {
            VoltuneLog.failure("equalizer_unavailable", error)
        }
    }

    @Suppress("DEPRECATION")
    private fun applyFixedLoudnessGain(audioSessionId: Int, gainDb: Float) {
        if (gainDb <= 0.05f) return
        try {
            val effect = LoudnessEnhancer(audioSessionId)
            effect.setTargetGain((min(8f, gainDb) * 100f).roundToInt())
            effect.enabled = true
            loudnessEnhancer = effect
            VoltuneLog.info("fixed_loudness_gain_applied")
        } catch (error: RuntimeException) {
            VoltuneLog.failure("fixed_loudness_gain_unavailable", error)
        }
    }

    private fun releaseEffect(effect: AudioEffect) {
        try {
            effect.release()
        } catch (_: RuntimeException) {
        }
    }

    companion object {
        @JvmStatic
        fun playerVolumeForGainDb(gainDb: Float): Float {
            if (gainDb >= 0f) return 1f
            return 10.0.pow(max(LoudnessGainPolicy.MAX_CUT_DB, gainDb) / 20.0).toFloat()
        }
    }
}
