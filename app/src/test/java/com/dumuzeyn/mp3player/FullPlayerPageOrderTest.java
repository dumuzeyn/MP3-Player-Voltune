package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class FullPlayerPageOrderTest {
    @Test
    public void leftSwipesAdvancePlayerToLyricsToQueueOnTheRight() {
        int page = FullPlayerPageOrder.PLAYER;
        page = FullPlayerPageOrder.afterLeftSwipe(page);
        assertEquals(FullPlayerPageOrder.LYRICS, page);
        page = FullPlayerPageOrder.afterLeftSwipe(page);
        assertEquals(FullPlayerPageOrder.QUEUE, page);
    }

    @Test
    public void rightSwipesReturnQueueToLyricsToPlayer() {
        int page = FullPlayerPageOrder.QUEUE;
        page = FullPlayerPageOrder.afterRightSwipe(page);
        assertEquals(FullPlayerPageOrder.LYRICS, page);
        page = FullPlayerPageOrder.afterRightSwipe(page);
        assertEquals(FullPlayerPageOrder.PLAYER, page);
    }
}
