package com.dumuzeyn.mp3player;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import com.dumuzeyn.mp3player.data.playback.PlaybackStateManager;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/** Restores persisted state into an empty Media3 player after process recreation. */
final class PlaybackSessionRestorer implements AutoCloseable {
    private final Context context;
    private final PlaybackStateManager stateManager;
    private final MediaItemMapper mapper;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "playback-restore");
        thread.setDaemon(true);
        return thread;
    });
    private volatile boolean closed;

    PlaybackSessionRestorer(Context context, PlaybackStateManager stateManager,
            MediaItemMapper mapper) {
        this.context = context.getApplicationContext();
        this.stateManager = stateManager;
        this.mapper = mapper;
    }

    void restore(Player player) {
        PlaybackStateManager.State state = stateManager.load();
        if (state.uri.isEmpty() && state.queueUris.isEmpty()) {
            return;
        }
        long retentionMs = UiPreferencesStore.readResumeWindowMinutes(context) * 60_000L;
        if (MiniPlayerRetentionPolicy.isExpired(state.playing, state.inactiveSince,
                state.savedAt, System.currentTimeMillis(), retentionMs)) {
            stateManager.clear();
            return;
        }
        try {
            executor.execute(() -> restoreLoaded(player, state));
        } catch (RejectedExecutionException ignored) {
            // The service is already closing.
        }
    }

    @Override
    public void close() {
        closed = true;
        mainHandler.removeCallbacksAndMessages(null);
        executor.shutdownNow();
    }

    private void restoreLoaded(Player player, PlaybackStateManager.State state) {
        ArrayList<Track> queue = PlaybackQueueResolver.restore(
                TrackStore.load(context), state.queueUris, null);
        ArrayList<MediaItem> items = new ArrayList<>();
        for (Track track : queue) {
            items.add(mapper.toMediaItem(track));
        }
        mainHandler.post(() -> apply(player, state, items));
    }

    private void apply(Player player, PlaybackStateManager.State state,
            ArrayList<MediaItem> items) {
        if (closed || items.isEmpty() || player.getMediaItemCount() > 0) return;
        int index = Math.max(0, Math.min(state.index, items.size() - 1));
        player.setMediaItems(items, index, Math.max(0, state.position));
        player.setRepeatMode(RepeatModeMapper.toMedia3(state.loopMode));
        player.setShuffleModeEnabled(false);
        player.prepare();
        if (state.playing) player.play();
    }
}
