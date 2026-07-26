package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PlaylistTickerLayoutPolicyTest {
    @Test
    public void oneOrTwoSongsAreNotDuplicatedToFillThreeLines() {
        assertEquals(1, PlaylistTickerLayoutPolicy.staticLinesToDraw(1, 3));
        assertEquals(2, PlaylistTickerLayoutPolicy.staticLinesToDraw(2, 3));
        assertEquals(3, PlaylistTickerLayoutPolicy.staticLinesToDraw(4, 3));
    }

    @Test
    public void previewHeightFollowsAvailableTitles() {
        assertEquals(1, PlaylistTickerLayoutPolicy.visibleLineCount(1, 3));
        assertEquals(2, PlaylistTickerLayoutPolicy.visibleLineCount(2, 3));
        assertEquals(3, PlaylistTickerLayoutPolicy.visibleLineCount(5, 3));
    }
}
