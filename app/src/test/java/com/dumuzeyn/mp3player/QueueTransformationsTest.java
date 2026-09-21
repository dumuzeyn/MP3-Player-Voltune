package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import org.junit.Test;

public class QueueTransformationsTest {
    @Test
    public void moveKeepsEveryElement() {
        assertEquals(Arrays.asList("b", "c", "a"),
                QueueTransformations.move(Arrays.asList("a", "b", "c"), 0, 2));
    }

    @Test
    public void playNextMovesExistingItem() {
        assertEquals(Arrays.asList("a", "c", "b"),
                QueueTransformations.playNext(Arrays.asList("a", "b", "c"), "c", 0));
    }

    @Test
    public void randomSubsetRespectsRequestedAndAvailableCounts() {
        List<String> source = Arrays.asList("a", "b", "c", "d");
        List<String> subset = QueueTransformations.randomSubset(source, 3, new Random(7));

        assertEquals(3, subset.size());
        assertEquals(3, new HashSet<>(subset).size());
        assertFalse(subset.equals(source.subList(0, 3)));
        assertEquals(1, QueueTransformations.randomSubset(source, 0, new Random(7)).size());
        assertEquals(4, QueueTransformations.randomSubset(source, 99, new Random(7)).size());
    }
}
