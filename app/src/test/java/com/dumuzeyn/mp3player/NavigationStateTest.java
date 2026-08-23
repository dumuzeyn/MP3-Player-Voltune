package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NavigationStateTest {
    @Test
    public void keepsSelectedTabSearchAndTransitionStateTogether() {
        NavigationState state = new NavigationState();

        state.setSelectedTab(4);
        state.setSearchQuery("artist");
        state.setPreferredDirection(-1);
        state.setTransitionRunning(true);

        assertEquals(4, state.selectedTab());
        assertEquals("artist", state.searchQuery());
        assertEquals(-1, state.preferredTabDirection);
        assertTrue(state.tabAnimating);

        state.setTransitionRunning(false);
        assertFalse(state.tabAnimating);
    }
}
