package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LoudnessGainPolicyTest {
    @Test
    public void gainRespectsBoostCutAndPeakLimits() {
        assertEquals(LoudnessGainPolicy.MAX_BOOST_DB,
                LoudnessGainPolicy.gainDb(-40.0f, -30.0f, false), 0.001f);
        assertEquals(LoudnessGainPolicy.MAX_CUT_DB,
                LoudnessGainPolicy.gainDb(2.0f, -0.1f, false), 0.001f);
        assertTrue(LoudnessGainPolicy.gainDb(-30.0f, -0.2f, false) <= -0.8f);
    }

    @Test
    public void reduceOnlyNeverBoostsAndEqualizerReservesHeadroom() {
        assertEquals(0.0f, LoudnessGainPolicy.gainDb(-30.0f, -20.0f, true), 0.001f);
        assertEquals(-3.0f, LoudnessGainPolicy.accountForEqualizer(3.0f, 6), 0.001f);
    }

    @Test
    public void targetLevelChangesGainWithoutChangingMeasurement() {
        float quietTarget = LoudnessGainPolicy.gainDb(-20.0f, -12.0f, -18.0f, false);
        float loudTarget = LoudnessGainPolicy.gainDb(-20.0f, -12.0f, -14.0f, false);

        assertTrue(loudTarget > quietTarget);
    }
}
