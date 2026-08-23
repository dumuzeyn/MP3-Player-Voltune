package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class CloseableRegistryTest {
    @Test
    public void closesEveryResourceWhenOneThrows() {
        CloseableRegistry registry = new CloseableRegistry();
        AtomicInteger closed = new AtomicInteger();
        registry.add(closed::incrementAndGet);
        registry.add(() -> {
            throw new IllegalStateException("expected");
        });
        registry.add(closed::incrementAndGet);

        registry.closeAll();
        registry.closeAll();

        assertEquals(2, closed.get());
    }
}
