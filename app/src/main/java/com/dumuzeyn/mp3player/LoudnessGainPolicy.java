package com.dumuzeyn.mp3player;

/** Converts an analyzed loudness result into a bounded, clipping-safe fixed track gain. */
final class LoudnessGainPolicy {
    static final float DEFAULT_TARGET_LUFS = -16.0f;
    static final float PEAK_HEADROOM_DB = -1.0f;
    static final float MAX_BOOST_DB = 8.0f;
    static final float MAX_CUT_DB = -12.0f;

    private LoudnessGainPolicy() {
    }

    static float gainDb(float integratedLufs, float peakDbfs, boolean reduceOnly) {
        return gainDb(integratedLufs, peakDbfs, DEFAULT_TARGET_LUFS, reduceOnly);
    }

    static float gainDb(float integratedLufs, float peakDbfs, float targetLufs,
            boolean reduceOnly) {
        if (!Float.isFinite(integratedLufs) || !Float.isFinite(peakDbfs)) {
            return 0.0f;
        }
        float safeTarget = Math.max(-24.0f, Math.min(-10.0f, targetLufs));
        float targetGain = safeTarget - integratedLufs;
        float peakSafeGain = PEAK_HEADROOM_DB - peakDbfs;
        float gain = Math.min(targetGain, peakSafeGain);
        if (reduceOnly) {
            gain = Math.min(0.0f, gain);
        }
        return Math.max(MAX_CUT_DB, Math.min(MAX_BOOST_DB, gain));
    }

    static float accountForEqualizer(float gainDb, int maximumBandBoostDb) {
        float adjusted = gainDb - Math.max(0, maximumBandBoostDb);
        return Math.max(MAX_CUT_DB, Math.min(MAX_BOOST_DB, adjusted));
    }
}
