package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class ThemeContrastPolicyTest {
    @Test public void lowContrastRequestsOutlineWithoutChangingColor() {
        assertTrue(ThemeContrastPolicy.requiresOutline(0xffffffff, 0xfff8f8f8));
        assertTrue(ThemeContrastPolicy.requiresOutline(0xff101010, 0xff151515));
    }

    @Test public void highContrastNeedsNoOutline() {
        assertFalse(ThemeContrastPolicy.requiresOutline(0xff000000, 0xffffffff));
        assertFalse(ThemeContrastPolicy.requiresOutline(0xffffffff, 0xff000000));
    }

    @Test public void similarOutlineColorIsSuppressed() {
        assertFalse(ThemeContrastPolicy.outlineIsDistinct(0xff101010, 0xff181818));
        assertTrue(ThemeContrastPolicy.outlineIsDistinct(0xff101010, 0xffffffff));
    }
}
