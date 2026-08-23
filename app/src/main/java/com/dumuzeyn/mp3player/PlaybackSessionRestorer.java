package com.dumuzeyn.mp3player;

import android.content.Context;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import com.dumuzeyn.mp3player.data.playback.PlaybackStateManager;
import java.util.ArrayList;

/** Restores persisted state into an empty Media3 player after process recreation. */
final class PlaybackSessionRestorer {
    private final Context context;
    private final PlaybackStateManager stateManager;
    private final MediaItemMapper mapper;

    PlaybackSessionRestorer(Context context, PlaybackStateManager stateManager,
            MediaItemMapper mapper) {
        this.context = context.getApplicationContext();
        this.stateManager = stateManager;
        this.mapper = mapper;
    }

    void restore(Player player) {
        PlaybackStateManager.State state = stateManager.load();
        ArrayList<Track> queue = PlaybackQueueResolver.restore(
                TrackStore.load(context), state.queueUris, null);
        if (queue.isEmpty()) {
            return;
        }
        ArrayList<MediaItem> items = new ArrayList<>();
        for (Track track : queue) {
            items.add(mapper.toMediaItem(track));
        }
        int index = Math.max(0, Math.min(state.index, items.size() - 1));
        player.setMediaItems(items, index, Math.max(0, state.position));
        player.setRepeatMode(RepeatModeMapper.toMedia3(state.loopMode));
        player.setShuffleModeEnabled(false);
        player.prepare();
        if (state.playing) {
            player.play();
        }
    }
}
