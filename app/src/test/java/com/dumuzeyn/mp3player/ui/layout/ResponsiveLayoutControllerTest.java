package com.dumuzeyn.mp3player.ui.layout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ResponsiveLayoutControllerTest {
    @Test
    public void tabletModeStartsAtSixHundredDp() {
        assertFalse(ResponsiveLayoutController.isTabletWidth(599));
        assertTrue(ResponsiveLayoutController.isTabletWidth(600));
        assertTrue(ResponsiveLayoutController.isTabletWidth(840));
    }

    @Test
    public void centeredPanelFitsNarrowPhoneWithMargins() {
        assertEquals(292, ResponsiveLayoutController.boundedPanelWidth(350, 320, 28));
        assertEquals(280, ResponsiveLayoutController.boundedPanelWidth(280, 320, 28));
    }
}
