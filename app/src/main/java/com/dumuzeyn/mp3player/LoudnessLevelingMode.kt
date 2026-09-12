package com.dumuzeyn.mp3player

internal enum class LoudnessLevelingMode(val fallbackTarget: Float) {
    REDUCE(-20f), BOOST(-14f), BALANCED(-16f);

    fun gainDb(lufs: Float, peakDbfs: Float, target: Float): Float {
        val gain = LoudnessGainPolicy.gainDb(lufs, peakDbfs, target, this == REDUCE)
        return if (this == BOOST) gain.coerceAtLeast(0f) else gain
    }

    fun referenceTarget(levels: List<Float>): Float {
        val finite = levels.filter { it.isFinite() }
        if (finite.isEmpty()) return fallbackTarget
        return when (this) {
            REDUCE -> finite.min()
            BOOST -> finite.max()
            BALANCED -> finite.average().toFloat()
        }.coerceIn(-24f, -10f)
    }

    fun reserveEqualizerHeadroom(gainDb: Float, maximumBandBoostDb: Int): Float {
        val adjusted = LoudnessGainPolicy.accountForEqualizer(gainDb, maximumBandBoostDb)
        return when (this) {
            REDUCE -> adjusted.coerceAtMost(0f)
            BOOST -> adjusted.coerceAtLeast(0f)
            BALANCED -> adjusted
        }
    }

    companion object {
        const val PREFERENCE = "leveling_mode"

        fun fromPreference(value: String?, legacyReduceOnly: Boolean): LoudnessLevelingMode =
            entries.firstOrNull { it.name == value } ?: if (legacyReduceOnly) REDUCE else BALANCED
    }
}
