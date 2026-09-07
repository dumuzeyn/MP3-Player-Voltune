package com.dumuzeyn.mp3player

import android.annotation.TargetApi
import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.AudioEffect
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

internal class AudioEffectsManager(context: Context) {
    private val context = context.applicationContext
    private var equalizer: Equalizer? = null
    private var dynamicsProcessing: AudioEffect? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var customDynamicsUnsupported = false

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
        return LoudnessGainPolicy.accountForEqualizer(analyzedGainDb, maximumBandBoost)
    }

    fun apply(audioSessionId: Int, normalizationGainDb: Float) {
        release()
        if (audioSessionId <= 0) return
        val preferences = context.getSharedPreferences(EqualizerController.PREFS, 0)
        if (preferences.getBoolean(EqualizerController.ENABLED, false)) {
            applyEqualizer(preferences, audioSessionId)
        }
        if (preferences.getBoolean(VolumeLevelingController.ENABLED, false)) {
            applyVolumeLeveling(audioSessionId)
            applyFixedLoudnessGain(audioSessionId, normalizationGainDb)
        }
    }

    fun release() {
        equalizer?.let(::releaseEffect)
        equalizer = null
        dynamicsProcessing?.let(::releaseEffect)
        dynamicsProcessing = null
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

    private fun applyVolumeLeveling(audioSessionId: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        if (customDynamicsUnsupported) {
            applyCompatibleVolumeLeveling(audioSessionId)
            return
        }
        try {
            dynamicsProcessing = Api28VolumeLeveling.createCustom(audioSessionId)
            VoltuneLog.info("volume_leveling_applied")
        } catch (customConfigError: RuntimeException) {
            customDynamicsUnsupported = true
            VoltuneLog.failure("volume_leveling_custom_config_failed", customConfigError)
            applyCompatibleVolumeLeveling(audioSessionId)
        }
    }

    @TargetApi(Build.VERSION_CODES.P)
    private fun applyCompatibleVolumeLeveling(audioSessionId: Int) {
        try {
            val effect = Api28VolumeLeveling.createCompatible(audioSessionId)
            dynamicsProcessing = effect
            VoltuneLog.info("volume_leveling_applied_compatible channels=${effect.channelCount}")
        } catch (defaultConfigError: RuntimeException) {
            VoltuneLog.failure("volume_leveling_default_config_failed", defaultConfigError)
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

    @TargetApi(Build.VERSION_CODES.P)
    private object Api28VolumeLeveling {
        fun createCustom(audioSessionId: Int): DynamicsProcessing {
            // Keep a slow peak guard so loud and quiet passages do not pump.
            val limiter = DynamicsProcessing.Limiter(
                true,
                true,
                0,
                10f,
                1_500f,
                10f,
                -1f,
                0f,
            )
            val config = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_TIME_RESOLUTION,
                2,
                false,
                0,
                false,
                0,
                false,
                0,
                true,
            ).setLimiterAllChannelsTo(limiter).build()
            return DynamicsProcessing(0, audioSessionId, config).apply { enabled = true }
        }

        fun createCompatible(audioSessionId: Int): DynamicsProcessing {
            val effect = DynamicsProcessing(0, audioSessionId, null)
            for (channel in 0 until effect.channelCount) {
                val compressor = effect.getMbcByChannelIndex(channel)
                for (band in 0 until compressor.bandCount) {
                    val settings = compressor.getBand(band)
                    settings.isEnabled = false
                    effect.setMbcBandByChannelIndex(channel, band, settings)
                }
                val limiter = effect.getLimiterByChannelIndex(channel)
                limiter.isEnabled = true
                limiter.attackTime = 10f
                limiter.releaseTime = 1_500f
                limiter.ratio = 10f
                limiter.threshold = -1f
                effect.setLimiterByChannelIndex(channel, limiter)
            }
            effect.enabled = true
            return effect
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
