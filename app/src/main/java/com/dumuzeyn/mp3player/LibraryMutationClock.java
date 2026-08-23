package com.dumuzeyn.mp3player;

import java.util.concurrent.atomic.AtomicLong;

/** Process-local fence that prevents stale scan results from reaching the UI. */
final class LibraryMutationClock {
    private static final AtomicLong VALUE = new AtomicLong();

    private LibraryMutationClock() {
    }

    static long read() {
        return VALUE.get();
    }

    static long advance() {
        return VALUE.incrementAndGet();
    }
}
