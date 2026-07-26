package com.dumuzeyn.mp3player;

import android.os.Handler;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/** Debounces and filters immutable track snapshots away from the UI thread. */
final class TrackSearchController implements AutoCloseable {
    interface Callback {
        void filtered(List<Track> tracks);
    }

    static final long DEFAULT_DEBOUNCE_MS = 200L;

    private final Handler mainHandler;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Map<String, Integer> generations = new HashMap<>();
    private final Map<String, Runnable> scheduled = new HashMap<>();
    private boolean closed;

    TrackSearchController(Handler mainHandler) {
        this.mainHandler = mainHandler;
    }

    void filter(String owner, List<Track> source, String query, Callback callback) {
        filter(owner, source, query, DEFAULT_DEBOUNCE_MS, callback);
    }

    void filterImmediately(String owner, List<Track> source, String query, Callback callback) {
        filter(owner, source, query, 0L, callback);
    }

    void cancel(String owner) {
        Runnable pending;
        synchronized (this) {
            nextGeneration(owner);
            pending = scheduled.remove(owner);
        }
        if (pending != null) {
            mainHandler.removeCallbacks(pending);
        }
    }

    @Override
    public synchronized void close() {
        closed = true;
        for (Runnable pending : scheduled.values()) {
            mainHandler.removeCallbacks(pending);
        }
        scheduled.clear();
        generations.clear();
        executor.shutdownNow();
    }

    private void filter(String owner, List<Track> source, String query, long delayMs,
            Callback callback) {
        final ArrayList<Track> snapshot = new ArrayList<>(source);
        final String normalizedQuery = Track.normalizeSearchText(query);
        final int generation;
        final Runnable previous;
        final Runnable task;
        synchronized (this) {
            if (closed) {
                return;
            }
            generation = nextGeneration(owner);
            previous = scheduled.remove(owner);
            task = () -> executeFilter(owner, generation, snapshot, normalizedQuery, callback);
            scheduled.put(owner, task);
        }
        if (previous != null) {
            mainHandler.removeCallbacks(previous);
        }
        if (delayMs <= 0L || normalizedQuery.isEmpty()) {
            task.run();
        } else {
            mainHandler.postDelayed(task, delayMs);
        }
    }

    private void executeFilter(String owner, int generation, ArrayList<Track> snapshot,
            String normalizedQuery, Callback callback) {
        synchronized (this) {
            scheduled.remove(owner);
            if (closed || currentGeneration(owner) != generation) {
                return;
            }
        }
        if (normalizedQuery.isEmpty()) {
            deliver(owner, generation, snapshot, callback);
            return;
        }
        try {
            executor.execute(() -> {
                ArrayList<Track> result = new ArrayList<>();
                for (Track track : snapshot) {
                    if (track.normalizedSearchText.contains(normalizedQuery)) {
                        result.add(track);
                    }
                }
                deliver(owner, generation, result, callback);
            });
        } catch (RejectedExecutionException ignored) {
            // Activity is already closing.
        }
    }

    private void deliver(String owner, int generation, List<Track> result, Callback callback) {
        List<Track> immutable = Collections.unmodifiableList(new ArrayList<>(result));
        mainHandler.post(() -> {
            synchronized (TrackSearchController.this) {
                if (closed || currentGeneration(owner) != generation) {
                    return;
                }
            }
            callback.filtered(immutable);
        });
    }

    private int nextGeneration(String owner) {
        int next = currentGeneration(owner) + 1;
        generations.put(owner, next);
        return next;
    }

    private int currentGeneration(String owner) {
        Integer value = generations.get(owner);
        return value == null ? 0 : value;
    }
}
