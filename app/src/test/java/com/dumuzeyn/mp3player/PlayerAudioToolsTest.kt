package com.dumuzeyn.mp3player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerAudioToolsTest {
    @Test fun speedSliderCoversRangeAndRoundTripsEveryStep() {
        assertEquals(0.25f, PlaybackSpeedPolicy.fromProgress(0), 0.0001f)
        assertEquals(4f, PlaybackSpeedPolicy.fromProgress(75), 0.0001f)
        for (step in 0..75) {
            assertEquals(step, PlaybackSpeedPolicy.progress(PlaybackSpeedPolicy.fromProgress(step)))
        }
        assertEquals(1f, PlaybackSpeedPolicy.constrain(Float.NaN), 0f)
        assertEquals(0.25f, PlaybackSpeedPolicy.constrain(-2f), 0f)
        assertEquals(4f, PlaybackSpeedPolicy.constrain(8f), 0f)
    }

    @Test fun speedTapRestoresSelectedSlowerOrFasterCoefficient() {
        for (coefficient in listOf(0.25f, 0.75f, 1f, 1.5f, 4f)) {
            assertEquals(coefficient, PlaybackSpeedPolicy.toggle(1f, coefficient), 0f)
            assertEquals(1f, PlaybackSpeedPolicy.toggle(coefficient, coefficient), 0f)
        }
    }

    @Test fun modesUseQuietLoudAndMeanLibraryReferences() {
        val library = listOf(-22f, -18f, -14f, Float.NaN)
        assertEquals(-22f, LoudnessLevelingMode.REDUCE.referenceTarget(library), 0f)
        assertEquals(-14f, LoudnessLevelingMode.BOOST.referenceTarget(library), 0f)
        assertEquals(-18f, LoudnessLevelingMode.BALANCED.referenceTarget(library), 0f)
        LoudnessLevelingMode.entries.forEach {
            assertTrue(it.referenceTarget(emptyList()).isFinite())
            assertTrue(it.referenceTarget(listOf(-90f)) >= -24f)
            assertTrue(it.referenceTarget(listOf(0f)) <= -10f)
        }
    }

    @Test fun directionalModesNeverChangeTracksInOppositeDirection() {
        for (lufs in -50..0) for (peak in -40..0) {
            val cut = LoudnessLevelingMode.REDUCE.gainDb(lufs.toFloat(), peak.toFloat(), -20f)
            val boost = LoudnessLevelingMode.BOOST.gainDb(lufs.toFloat(), peak.toFloat(), -14f)
            assertTrue(cut in LoudnessGainPolicy.MAX_CUT_DB..0f)
            assertTrue(boost in 0f..LoudnessGainPolicy.MAX_BOOST_DB)
            if (boost > 0f) assertTrue(peak + boost <= LoudnessGainPolicy.PEAK_HEADROOM_DB)
        }
        assertTrue(LoudnessLevelingMode.BALANCED.gainDb(-22f, -12f, -18f) > 0f)
        assertTrue(LoudnessLevelingMode.BALANCED.gainDb(-12f, -2f, -18f) < 0f)
        assertEquals(0f, LoudnessLevelingMode.BOOST.gainDb(Float.NaN, -8f, -14f), 0f)
        assertEquals(0f, LoudnessLevelingMode.BALANCED.gainDb(-22f, -8f, Float.NaN), 0f)
    }

    @Test fun legacyModeIsPreservedUntilExplicitSelection() {
        assertEquals(LoudnessLevelingMode.REDUCE, LoudnessLevelingMode.fromPreference(null, true))
        assertEquals(LoudnessLevelingMode.BALANCED, LoudnessLevelingMode.fromPreference(null, false))
        assertEquals(LoudnessLevelingMode.BOOST, LoudnessLevelingMode.fromPreference("BOOST", true))
    }
}
