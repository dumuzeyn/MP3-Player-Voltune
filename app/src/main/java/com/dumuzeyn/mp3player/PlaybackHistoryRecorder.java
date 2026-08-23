package com.dumuzeyn.mp3player;

import android.content.Context;
import androidx.media3.common.C;
import androidx.media3.common.Player;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/** Persists service-owned listening statistics without blocking Media3 callbacks. */
final class PlaybackHistoryRecorder implements AutoCloseable {
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final PlaybackHistoryTracker tracker;
    private boolean closed;

    PlaybackHistoryRecorder(Context context) {
        this.context = context.getApplicationContext();
        tracker = new PlaybackHistoryTracker(new PlaybackHistoryTracker.Listener() {
            @Override
            public void onPlayed(String trackId, boolean completed, long timestamp) {
                execute(database -> database.recordPlayed(trackId, completed, timestamp));
            }

            @Override
            public void onSkipped(String trackId, long timestamp) {
                execute(database -> database.recordSkipped(trackId, timestamp));
            }
        });
    }

    void transition(String mediaId, long durationMs, int reason) {
        boolean userInitiated = reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK;
        tracker.transitionTo(mediaId, safeDuration(durationMs), System.currentTimeMillis(),
                userInitiated);
    }

    void playing(boolean value) {
        tracker.setPlaying(value, System.currentTimeMillis());
    }

    void sample(long durationMs) {
        tracker.updateDuration(safeDuration(durationMs));
        tracker.sample(System.currentTimeMillis());
    }

    void ended(long durationMs) {
        tracker.updateDuration(safeDuration(durationMs));
        tracker.sample(System.currentTimeMillis());
        tracker.finish(false, System.currentTimeMillis());
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        tracker.sample(System.currentTimeMillis());
        tracker.finish(false, System.currentTimeMillis());
        executor.shutdown();
    }

    private void execute(DatabaseAction action) {
        if (closed) {
            return;
        }
        try {
            executor.execute(() -> {
                LibraryDatabase database = new LibraryDatabase(context);
                try {
                    action.run(database);
                } finally {
                    database.close();
                }
            });
        } catch (RejectedExecutionException ignored) {
            // Service shutdown raced with the final playback callback.
        }
    }

    private static long safeDuration(long value) {
        return value == C.TIME_UNSET ? 0L : Math.max(0L, value);
    }

    private interface DatabaseAction {
        void run(LibraryDatabase database);
    }
}
