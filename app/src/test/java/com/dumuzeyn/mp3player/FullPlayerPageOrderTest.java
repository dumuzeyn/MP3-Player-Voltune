package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class FullPlayerPageOrderTest {
    @Test
    public void rightSwipesAdvancePlayerToLyricsToQueue() {
        int page = FullPlayerPageOrder.PLAYER;
        page = FullPlayerPageOrder.afterRightSwipe(page);
        assertEquals(FullPlayerPageOrder.LYRICS, page);
        page = FullPlayerPageOrder.afterRightSwipe(page);
        assertEquals(FullPlayerPageOrder.QUEUE, page);
    }

    @Test
    public void leftSwipesReturnQueueToLyricsToPlayer() {
        int page = FullPlayerPageOrder.QUEUE;
        page = FullPlayerPageOrder.afterLeftSwipe(page);
        assertEquals(FullPlayerPageOrder.LYRICS, page);
        page = FullPlayerPageOrder.afterLeftSwipe(page);
        assertEquals(FullPlayerPageOrder.PLAYER, page);
    }
}
