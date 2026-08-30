package com.dumuzeyn.mp3player;

import android.content.ComponentName;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import com.dumuzeyn.mp3player.data.playback.PlaybackStateManager;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Connects the UI to Media3 and exposes playback commands plus a read-only UI snapshot. */
final class PlaybackController implements Player.Listener {
    private static final int MAX_PENDING_COMMANDS = 24;

    private final MainActivityCore host;
    private final MediaItemMapper mapper = new MediaItemMapper();
    private final ArrayDeque<Runnable> pendingCommands = new ArrayDeque<>();
    private ListenableFuture<MediaController> controllerFuture;
    private MediaController controller;
    private boolean discardExpiredSession;
    private boolean released;

    PlaybackController(MainActivityCore host) {
        this.host = host;
    }

    void connect() {
        if (controllerFuture != null || released) {
            return;
        }
        SessionToken token = new SessionToken(host,
                new ComponentName(host, Media3PlayerService.class));
        controllerFuture = new MediaController.Builder(host, token).buildAsync();
        controllerFuture.addListener(() -> host.runOnUiThread(this::finishConnection),
                Runnable::run);
    }

    void restorePersistedUiState() {
        PlaybackStateManager stateManager = new PlaybackStateManager(host);
        PlaybackStateManager.State state = stateManager.load();
        if (!hasSavedSession(state)) {
            return;
        }
        long resumeWindowMs = Math.max(0L, host.appearanceState.resumeWindowMinutes) * 60000L;
        boolean expired = MiniPlayerRetentionPolicy.isExpired(
                state.playing, state.inactiveSince, state.savedAt,
                System.currentTimeMillis(), resumeWindowMs);
        if (expired) {
            discardExpiredSession = true;
            stateManager.clear();
            clearProjectedSession();
            return;
        }
        Track current = host.findTrack(state.uri);
        ArrayList<Track> restoredQueue = PlaybackQueueResolver.restore(
                host.libraryState.tracks, state.queueUris, current);
        if (restoredQueue.isEmpty()) {
            return;
        }
        int index = Math.max(0, Math.min(state.index, restoredQueue.size() - 1));
        if (current == null) {
            current = restoredQueue.get(index);
        } else {
            int restoredIndex = restoredQueue.indexOf(current);
            if (restoredIndex >= 0) {
                index = restoredIndex;
            }
        }
        ArrayList<String> mediaIds = new ArrayList<>();
        for (Track track : restoredQueue) {
            mediaIds.add(mapper.mediaId(track));
        }
        host.playbackUiState.queue.clear();
        host.playbackUiState.queue.addAll(restoredQueue);
        host.updatePlaybackSnapshot(new PlaybackSnapshot(mediaIds, mapper.mediaId(current),
                index, state.position, Math.max(current.durationMs, state.duration),
                state.playing, Player.STATE_READY, RepeatModeMapper.toMedia3(state.loopMode),
                state.shuffle, PlaybackPhase.READY,
                state.playing ? PauseReason.NONE : PauseReason.USER, StopReason.NONE,
                null, state.savedAt));
    }

    private void finishConnection() {
        if (released || controllerFuture == null) {
            return;
        }
        try {
            controller = controllerFuture.get();
            controller.addListener(this);
            discardExpiredControllerSession();
            synchronizeUi(true);
            while (!pendingCommands.isEmpty()) {
                pendingCommands.removeFirst().run();
            }
        } catch (Exception error) {
            VoltuneLog.failure("media_controller_connection_failed", error);
            pendingCommands.clear();
        }
    }

    void enforceMiniPlayerRetention() {
        PlaybackStateManager stateManager = new PlaybackStateManager(host);
        PlaybackStateManager.State state = stateManager.load();
        if (!hasSavedSession(state)) {
            return;
        }
        long retentionMs = Math.max(0L, host.appearanceState.resumeWindowMinutes) * 60_000L;
        if (!MiniPlayerRetentionPolicy.isExpired(state.playing, state.inactiveSince,
                state.savedAt, System.currentTimeMillis(), retentionMs)) {
            return;
        }
        discardExpiredSession = true;
        stateManager.clear();
        discardExpiredControllerSession();
        clearProjectedSession();
    }

    private void discardExpiredControllerSession() {
        if (!discardExpiredSession || controller == null || controller.getPlayWhenReady()) {
            return;
        }
        discardExpiredSession = false;
        controller.stop();
        controller.clearMediaItems();
        controller.sendCustomCommand(Media3Commands.CLEAR_QUEUE_COMMAND, Bundle.EMPTY);
    }

    private void clearProjectedSession() {
        host.playbackUiState.queue.clear();
        host.updatePlaybackSnapshot(PlaybackSnapshot.empty());
        host.playerUiController.updateMini();
    }

    private static boolean hasSavedSession(PlaybackStateManager.State state) {
        return !state.uri.isEmpty() || !state.queueUris.isEmpty();
    }

    void release() {
        released = true;
        pendingCommands.clear();
        if (controller != null) {
            controller.removeListener(this);
            controller.release();
            controller = null;
        } else if (controllerFuture != null) {
            MediaController.releaseFuture(controllerFuture);
        }
        controllerFuture = null;
    }

    private void whenConnected(Runnable command) {
        if (released) {
            return;
        }
        if (controller != null) {
            command.run();
            return;
        }
        if (pendingCommands.size() >= MAX_PENDING_COMMANDS) {
            pendingCommands.removeFirst();
        }
        pendingCommands.addLast(command);
        connect();
    }

    void submitQueue(List<Track> source, int index, int positionMs, int repeatMode,
            boolean playWhenReady) {
        ArrayList<Track> queue = new ArrayList<>(source);
        whenConnected(() -> {
            if (queue.isEmpty()) {
                return;
            }
            ArrayList<MediaItem> items = new ArrayList<>();
            for (Track track : queue) {
                items.add(mapper.toMediaItem(track));
            }
            int safeIndex = Math.max(0, Math.min(index, items.size() - 1));
            controller.setMediaItems(items, safeIndex, Math.max(0, positionMs));
            controller.setShuffleModeEnabled(false);
            controller.setRepeatMode(RepeatModeMapper.toMedia3(repeatMode));
            controller.prepare();
            if (playWhenReady) {
                controller.play();
            } else {
                controller.pause();
            }
        });
    }

    void toggle() {
        whenConnected(() -> {
            if (controller.getMediaItemCount() == 0) {
                return;
            }
            if (controller.isPlaying() || controller.getPlayWhenReady()) {
                controller.pause();
            } else {
                if (controller.getPlaybackState() == Player.STATE_IDLE) {
                    controller.prepare();
                }
                controller.play();
            }
        });
    }

    void next() {
        whenConnected(() -> {
            if (controller.hasNextMediaItem()) {
                controller.seekToNextMediaItem();
                controller.play();
            }
        });
    }

    void previous() {
        whenConnected(() -> {
            controller.seekToPreviousMediaItem();
            controller.play();
        });
    }

    void cycleRepeatMode() {
        int nextMode = (host.repeatMode() + 1) % 3;
        whenConnected(() -> controller.setRepeatMode(
                RepeatModeMapper.toMedia3(nextMode)));
    }

    void clearQueue() {
        whenConnected(() -> {
            controller.stop();
            controller.clearMediaItems();
            controller.sendCustomCommand(Media3Commands.CLEAR_QUEUE_COMMAND, Bundle.EMPTY);
        });
    }

    void addQueueItem(Track track) {
        whenConnected(() -> controller.addMediaItem(mapper.toMediaItem(track)));
    }

    void addQueueItems(List<Track> tracks) {
        ArrayList<Track> requested = new ArrayList<>(tracks);
        whenConnected(() -> {
            ArrayList<MediaItem> additions = new ArrayList<>();
            for (Track track : requested) {
                if (indexOfMediaId(mapper.mediaId(track)) < 0) {
                    additions.add(mapper.toMediaItem(track));
                }
            }
            if (!additions.isEmpty()) {
                controller.addMediaItems(additions);
            }
        });
    }

    void playNext(Track track) {
        whenConnected(() -> {
            int existing = indexOfMediaId(mapper.mediaId(track));
            int insertion = Math.min(controller.getCurrentMediaItemIndex() + 1,
                    controller.getMediaItemCount());
            if (existing >= 0) {
                int destination = existing < insertion ? insertion - 1 : insertion;
                if (existing != destination) {
                    controller.moveMediaItem(existing, destination);
                }
            } else {
                controller.addMediaItem(insertion, mapper.toMediaItem(track));
            }
        });
    }

    void moveQueueItem(int from, int to) {
        whenConnected(() -> {
            if (from >= 0 && to >= 0 && from < controller.getMediaItemCount()
                    && to < controller.getMediaItemCount() && from != to) {
                controller.moveMediaItem(from, to);
            }
        });
    }

    void seekQueueItem(int index) {
        whenConnected(() -> {
            if (index >= 0 && index < controller.getMediaItemCount()) {
                controller.seekToDefaultPosition(index);
                controller.play();
            }
        });
    }

    void removeQueueItem(int index) {
        whenConnected(() -> {
            if (index >= 0 && index < controller.getMediaItemCount()) {
                controller.removeMediaItem(index);
            }
        });
    }

    void removeQueueItems(java.util.Set<String> mediaIds) {
        java.util.HashSet<String> removed = new java.util.HashSet<>(mediaIds);
        whenConnected(() -> {
            for (int index = controller.getMediaItemCount() - 1; index >= 0; index--) {
                if (removed.contains(controller.getMediaItemAt(index).mediaId)) {
                    controller.removeMediaItem(index);
                }
            }
            if (controller.getMediaItemCount() == 0) {
                controller.stop();
                controller.sendCustomCommand(Media3Commands.CLEAR_QUEUE_COMMAND, Bundle.EMPTY);
            }
        });
    }

    private int indexOfMediaId(String mediaId) {
        for (int index = 0; index < controller.getMediaItemCount(); index++) {
            if (mediaId.equals(controller.getMediaItemAt(index).mediaId)) {
                return index;
            }
        }
        return -1;
    }

    void seekTo(int positionMs) {
        whenConnected(() -> controller.seekTo(Math.max(0, positionMs)));
    }

    void startSleepTimer(long delayMs) {
        Bundle args = new Bundle();
        args.putLong(Media3Commands.ARG_TIMER_MS, Math.max(1000L, delayMs));
        whenConnected(() -> controller.sendCustomCommand(
                Media3Commands.TIMER_START_COMMAND, args));
    }

    void cancelSleepTimer() {
        whenConnected(() -> controller.sendCustomCommand(
                Media3Commands.TIMER_CANCEL_COMMAND, Bundle.EMPTY));
    }

    void refreshAudioEffects() {
        whenConnected(() -> controller.sendCustomCommand(
                Media3Commands.AUDIO_EFFECTS_COMMAND, Bundle.EMPTY));
    }

    long currentPosition() {
        return controller == null ? host.playbackSnapshot().positionMs
                : Math.max(0L, controller.getCurrentPosition());
    }

    long duration() {
        if (controller == null || controller.getDuration() == C.TIME_UNSET) {
            return host.playbackSnapshot().durationMs;
        }
        return Math.max(0L, controller.getDuration());
    }

    boolean hasPlaybackSession() {
        if (controller != null) {
            return controller.getMediaItemCount() > 0;
        }
        return !new PlaybackStateManager(host).load().queueUris.isEmpty();
    }

    @Nullable
    private Track currentTrack() {
        if (controller == null) {
            return null;
        }
        MediaItem item = controller.getCurrentMediaItem();
        if (item == null || item.localConfiguration == null) {
            return null;
        }
        return host.findTrack(item.localConfiguration.uri.toString());
    }

    private void synchronizeUi(boolean refreshRows) {
        if (controller == null) {
            return;
        }
        int previousIndex = host.currentTrackIndex();
        Track previous = previousIndex >= 0 && previousIndex < host.libraryState.tracks.size()
                ? host.libraryState.tracks.get(previousIndex) : null;
        Track current = currentTrack();
        host.updatePlaybackSnapshot(snapshotFromController());
        synchronizeQueueProjection();
        boolean trackChanged = previous == null ? current != null
                : current == null || !previous.uri.equals(current.uri);
        host.playerUiController.updateMini();
        if (trackChanged || refreshRows) {
            host.refreshAfterTrackChange();
        }
        host.playerUiController.syncPlaybackUi();
    }

    private void synchronizeQueueProjection() {
        Map<String, Track> tracksById = new HashMap<>();
        Map<String, Track> tracksByUri = new HashMap<>();
        for (Track track : host.libraryState.tracks) {
            tracksById.put(mapper.mediaId(track), track);
            tracksByUri.put(track.uri, track);
        }
        host.playbackUiState.queue.clear();
        for (int itemIndex = 0; itemIndex < controller.getMediaItemCount(); itemIndex++) {
            MediaItem item = controller.getMediaItemAt(itemIndex);
            Track track = tracksById.get(item.mediaId);
            if (track == null && item.localConfiguration != null) {
                track = tracksByUri.get(item.localConfiguration.uri.toString());
            }
            if (track != null) {
                host.playbackUiState.queue.add(track);
            }
        }
    }

    private PlaybackSnapshot snapshotFromController() {
        ArrayList<String> mediaIds = new ArrayList<>();
        for (int index = 0; index < controller.getMediaItemCount(); index++) {
            mediaIds.add(controller.getMediaItemAt(index).mediaId);
        }
        PlaybackPhase phase = phase(controller.getPlaybackState());
        MediaItem item = controller.getCurrentMediaItem();
        String mediaId = item == null ? "" : item.mediaId;
        long duration = controller.getDuration() == C.TIME_UNSET
                ? 0L : Math.max(0L, controller.getDuration());
        return new PlaybackSnapshot(mediaIds, mediaId,
                controller.getCurrentMediaItemIndex(), controller.getCurrentPosition(), duration,
                controller.getPlayWhenReady(), controller.getPlaybackState(),
                controller.getRepeatMode(), controller.getShuffleModeEnabled(), phase,
                controller.getPlayWhenReady() ? PauseReason.NONE : PauseReason.USER,
                phase == PlaybackPhase.ENDED ? StopReason.QUEUE_ENDED : StopReason.NONE,
                null, System.currentTimeMillis());
    }

    private static PlaybackPhase phase(int playbackState) {
        switch (playbackState) {
            case Player.STATE_BUFFERING:
                return PlaybackPhase.BUFFERING;
            case Player.STATE_READY:
                return PlaybackPhase.READY;
            case Player.STATE_ENDED:
                return PlaybackPhase.ENDED;
            default:
                return PlaybackPhase.IDLE;
        }
    }

    @Override
    public void onEvents(Player player, Player.Events events) {
        boolean refreshRows = events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)
                || events.contains(Player.EVENT_IS_PLAYING_CHANGED)
                || events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED);
        synchronizeUi(refreshRows);
    }
}
