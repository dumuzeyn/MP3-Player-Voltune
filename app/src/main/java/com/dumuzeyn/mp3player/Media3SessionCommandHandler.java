package com.dumuzeyn.mp3player;

import android.annotation.SuppressLint;
import android.os.Bundle;
import androidx.media3.common.Player;
import androidx.media3.session.SessionCommand;
import androidx.media3.session.SessionError;
import androidx.media3.session.SessionResult;
import com.dumuzeyn.mp3player.data.playback.PlaybackStateManager;
import com.dumuzeyn.mp3player.playback.service.PlaybackSleepTimer;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

/** Handles Voltune-specific commands which are not part of the standard Player API. */
@SuppressLint("UnsafeOptInUsageError")
final class Media3SessionCommandHandler {
    interface SnapshotProvider {
        Bundle snapshot();
    }

    private final Player player;
    private final PlaybackSleepTimer sleepTimer;
    private final PlaybackStateManager stateManager;
    private final Runnable applyAudioEffects;
    private final Runnable onQueueCleared;
    private final SnapshotProvider snapshotProvider;

    Media3SessionCommandHandler(Player player, PlaybackSleepTimer sleepTimer,
            PlaybackStateManager stateManager, Runnable applyAudioEffects,
            Runnable onQueueCleared, SnapshotProvider snapshotProvider) {
        this.player = player;
        this.sleepTimer = sleepTimer;
        this.stateManager = stateManager;
        this.applyAudioEffects = applyAudioEffects;
        this.onQueueCleared = onQueueCleared;
        this.snapshotProvider = snapshotProvider;
    }

    ListenableFuture<SessionResult> handle(SessionCommand command, Bundle args) {
        String action = command.customAction;
        if (Media3Commands.TIMER_START.equals(action)) {
            sleepTimer.start(args.getLong(Media3Commands.ARG_TIMER_MS, 0L));
            return success();
        }
        if (Media3Commands.TIMER_CANCEL.equals(action)) {
            sleepTimer.cancel();
            return success();
        }
        if (Media3Commands.AUDIO_EFFECTS.equals(action)) {
            applyAudioEffects.run();
            return success();
        }
        if (Media3Commands.CLEAR_QUEUE.equals(action)) {
            player.stop();
            player.clearMediaItems();
            stateManager.clear();
            onQueueCleared.run();
            return success();
        }
        if (Media3Commands.DIAGNOSTIC_SNAPSHOT.equals(action)) {
            return Futures.immediateFuture(new SessionResult(
                    SessionResult.RESULT_SUCCESS, snapshotProvider.snapshot()));
        }
        return Futures.immediateFuture(new SessionResult(SessionError.ERROR_NOT_SUPPORTED));
    }

    private static ListenableFuture<SessionResult> success() {
        return Futures.immediateFuture(new SessionResult(SessionResult.RESULT_SUCCESS));
    }
}
