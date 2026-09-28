package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AppearanceStateTest {
    @Test
    public void animationsAreEnabledByDefault() {
        assertTrue(new AppearanceState().animations);
    }

    @Test
    public void sharedOpacityUpdatesEveryCardSurface() {
        AppearanceState state = new AppearanceState();
        state.setAllCardOpacity(64);
        assertEquals(64, state.cardOpacity);
        assertEquals(64, state.songCardOpacity);
        assertEquals(64, state.favoriteCardOpacity);
        assertEquals(64, state.playlistCardOpacity);
        assertEquals(64, state.genreCardOpacity);
        assertEquals(64, state.artistCardOpacity);
        assertEquals(64, state.albumCardOpacity);
        assertEquals(64, state.settingsCardOpacity);
        assertEquals(64, state.miniPlayerCardOpacity);
        assertEquals(64, state.headerCardOpacity);
        assertEquals(64, state.dialogCardOpacity);
    }

    @Test
    public void newVisualShapesPreserveOldDefault() {
        AppearanceState state = new AppearanceState();
        assertEquals("rounded", state.coverShape);
        assertEquals("lightning", state.particleShape);
    }
}
