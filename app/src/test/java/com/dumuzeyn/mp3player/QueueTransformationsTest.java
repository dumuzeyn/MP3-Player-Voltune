package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
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
}
