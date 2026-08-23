package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AppearanceStateTest {
    @Test
    public void animationsAreEnabledByDefault() {
        assertTrue(new AppearanceState().animations);
    }
}
