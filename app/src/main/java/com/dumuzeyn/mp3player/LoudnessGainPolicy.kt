package com.dumuzeyn.mp3player

/** Converts an analyzed loudness result into a bounded, clipping-safe fixed track gain. */
object LoudnessGainPolicy {
    const val DEFAULT_TARGET_LUFS = -16.0f
    const val PEAK_HEADROOM_DB = -1.0f
    const val MAX_BOOST_DB = 8.0f
    const val MAX_CUT_DB = -12.0f

    @JvmStatic
    fun gainDb(integratedLufs: Float, peakDbfs: Float, reduceOnly: Boolean): Float =
        gainDb(integratedLufs, peakDbfs, DEFAULT_TARGET_LUFS, reduceOnly)

    @JvmStatic
    fun gainDb(
        integratedLufs: Float,
        peakDbfs: Float,
        targetLufs: Float,
        reduceOnly: Boolean,
    ): Float {
        if (!integratedLufs.isFinite() || !peakDbfs.isFinite()) return 0.0f
        val safeTarget = targetLufs.coerceIn(-24.0f, -10.0f)
        val targetGain = safeTarget - integratedLufs
        val peakSafeGain = PEAK_HEADROOM_DB - peakDbfs
        var gain = minOf(targetGain, peakSafeGain)
        if (reduceOnly) gain = minOf(0.0f, gain)
        return gain.coerceIn(MAX_CUT_DB, MAX_BOOST_DB)
    }

    @JvmStatic
    fun accountForEqualizer(gainDb: Float, maximumBandBoostDb: Int): Float =
        (gainDb - maximumBandBoostDb.coerceAtLeast(0)).coerceIn(MAX_CUT_DB, MAX_BOOST_DB)
}
