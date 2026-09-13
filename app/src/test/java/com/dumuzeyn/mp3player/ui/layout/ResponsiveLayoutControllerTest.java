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

    @Test
    public void centeredPanelFitsShortViewportWithoutChangingWrapContent() {
        assertEquals(212, ResponsiveLayoutController.boundedPanelHeight(420, 240, 28));
        assertEquals(180, ResponsiveLayoutController.boundedPanelHeight(180, 240, 28));
        assertEquals(-2, ResponsiveLayoutController.boundedPanelHeight(-2, 240, 28));
        assertEquals(1, ResponsiveLayoutController.boundedPanelHeight(420, 20, 28));
    }
}
