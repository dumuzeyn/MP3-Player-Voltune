package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertEquals;
import java.util.Arrays;
import java.util.HashSet;
import org.junit.Test;

public class HomeTrackVisibilityTest {
    @Test
    public void songAppearsOnlyInFirstVisibleHomeSection() {
        Track first = new Track("content://music/first", "First", "Artist");
        Track second = new Track("content://music/second", "Second", "Artist");
        HashSet<String> shown = new HashSet<>();

        assertEquals(2, HomeTrackVisibility.takeUnseen(
                Arrays.asList(first, second), shown).size());
        assertEquals(0, HomeTrackVisibility.takeUnseen(
                Arrays.asList(first, second, first), shown).size());
    }
}
