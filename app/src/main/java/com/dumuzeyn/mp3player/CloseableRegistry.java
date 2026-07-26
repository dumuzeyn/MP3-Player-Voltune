package com.dumuzeyn.mp3player;

import java.util.ArrayList;
import java.util.List;

/** Closes every registered activity-scoped resource even if one close operation fails. */
final class CloseableRegistry {
    private final List<AutoCloseable> closeables = new ArrayList<>();
    private boolean closed;

    void add(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        if (closed) {
            closeQuietly(closeable);
            return;
        }
        closeables.add(closeable);
    }

    void closeAll() {
        if (closed) {
            return;
        }
        closed = true;
        for (int index = closeables.size() - 1; index >= 0; index--) {
            closeQuietly(closeables.get(index));
        }
        closeables.clear();
    }

    private static void closeQuietly(AutoCloseable closeable) {
        try {
            closeable.close();
        } catch (Exception ignored) {
            // One faulty resource must not prevent the remaining resources from closing.
        }
    }
}
