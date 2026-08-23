package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import org.junit.Test;

public class QueueRemovalPlanTest {
    @Test
    public void removingCurrentFirstTrackMovesToNext() {
        QueueRemovalPlan plan = QueueRemovalPlan.create(Arrays.asList("A", "B", "C"), 0,
                new HashSet<>(Collections.singletonList("A")));

        assertEquals(Arrays.asList("B", "C"), plan.remaining);
        assertEquals(0, plan.currentIndex);
        assertTrue(plan.currentRemoved);
    }

    @Test
    public void removingMiddleTrackDoesNotMoveCurrentFirstTrack() {
        QueueRemovalPlan plan = QueueRemovalPlan.create(Arrays.asList("A", "B", "C"), 0,
                new HashSet<>(Collections.singletonList("B")));

        assertEquals(Arrays.asList("A", "C"), plan.remaining);
        assertEquals(0, plan.currentIndex);
        assertFalse(plan.currentRemoved);
    }

    @Test
    public void removingTracksBeforeCurrentAdjustsIndex() {
        QueueRemovalPlan plan = QueueRemovalPlan.create(Arrays.asList("A", "B", "C"), 2,
                new HashSet<>(Collections.singletonList("A")));

        assertEquals(Arrays.asList("B", "C"), plan.remaining);
        assertEquals(1, plan.currentIndex);
        assertFalse(plan.currentRemoved);
    }
}
