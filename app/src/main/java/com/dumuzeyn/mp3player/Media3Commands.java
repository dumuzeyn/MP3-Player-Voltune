package com.dumuzeyn.mp3player;

import android.os.Bundle;
import androidx.media3.session.SessionCommand;

final class Media3Commands {
    static final String TIMER_START = "com.dumuzeyn.mp3player.media3.TIMER_START";
    static final String TIMER_CANCEL = "com.dumuzeyn.mp3player.media3.TIMER_CANCEL";
    static final String AUDIO_EFFECTS = "com.dumuzeyn.mp3player.media3.AUDIO_EFFECTS";
    static final String CLEAR_QUEUE = "com.dumuzeyn.mp3player.media3.CLEAR_QUEUE";
    static final String DIAGNOSTIC_SNAPSHOT =
            "com.dumuzeyn.mp3player.media3.DIAGNOSTIC_SNAPSHOT";
    static final String ARG_TIMER_MS = "timerMs";

    static final SessionCommand TIMER_START_COMMAND = new SessionCommand(TIMER_START, Bundle.EMPTY);
    static final SessionCommand TIMER_CANCEL_COMMAND = new SessionCommand(TIMER_CANCEL, Bundle.EMPTY);
    static final SessionCommand AUDIO_EFFECTS_COMMAND = new SessionCommand(AUDIO_EFFECTS, Bundle.EMPTY);
    static final SessionCommand CLEAR_QUEUE_COMMAND = new SessionCommand(CLEAR_QUEUE, Bundle.EMPTY);
    static final SessionCommand DIAGNOSTIC_SNAPSHOT_COMMAND =
            new SessionCommand(DIAGNOSTIC_SNAPSHOT, Bundle.EMPTY);

    private Media3Commands() {
    }
}
