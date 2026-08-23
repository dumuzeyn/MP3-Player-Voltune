package com.dumuzeyn.mp3player;

public final class PlaybackTransitionPolicy {
    private boolean wasPlayingBeforeFocusLoss;
    private boolean userPausedAfterFocusLoss;

    public void onUserPause() {
        userPausedAfterFocusLoss = true;
    }

    public void onUserPlay() {
        userPausedAfterFocusLoss = false;
    }

    public PauseReason onTemporaryAudioFocusLoss(boolean wasPlaying) {
        wasPlayingBeforeFocusLoss = wasPlaying;
        userPausedAfterFocusLoss = false;
        return PauseReason.AUDIO_FOCUS;
    }

    public boolean shouldResumeAfterAudioFocusGain() {
        boolean resume = wasPlayingBeforeFocusLoss && !userPausedAfterFocusLoss;
        wasPlayingBeforeFocusLoss = false;
        return resume;
    }

    public PauseReason onAudioBecomingNoisy() {
        wasPlayingBeforeFocusLoss = false;
        return PauseReason.AUDIO_BECOMING_NOISY;
    }

    public boolean shouldSkipError(int consecutiveErrors, int queueSize, boolean recoverable) {
        return recoverable && queueSize > 1 && consecutiveErrors < queueSize;
    }

    public StopReason stopReasonForError(int consecutiveErrors, int queueSize,
            boolean recoverable) {
        if (recoverable && queueSize > 0 && consecutiveErrors >= queueSize) {
            return StopReason.ALL_ITEMS_UNAVAILABLE;
        }
        return StopReason.FATAL_ERROR;
    }
}
