package com.dumuzeyn.mp3player.playback.service;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.dumuzeyn.mp3player.VoltuneLog;

/** Owns sleep-timer persistence and scheduling for background playback. */
public final class PlaybackSleepTimer {
    public interface Listener {
        void onTimerExpired();
    }

    private static final String PREFS = "player_sleep_timer";
    private static final String ENDS_AT = "endsAt";

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Listener listener;
    private long endsAt;
    private final Runnable expiration = new Runnable() {
        @Override
        public void run() {
            if (endsAt <= 0L) {
                return;
            }
            if (System.currentTimeMillis() < endsAt) {
                schedule();
                return;
            }
            VoltuneLog.info("sleep_timer_expired");
            endsAt = 0L;
            persist();
            listener.onTimerExpired();
        }
    };

    public PlaybackSleepTimer(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    public void start(long delayMs) {
        long safeDelayMs = Math.max(1000L, delayMs);
        endsAt = System.currentTimeMillis() + safeDelayMs;
        persist();
        schedule();
        VoltuneLog.info("sleep_timer_started");
    }

    public void restore() {
        endsAt = readEndsAt(context);
        if (endsAt <= 0L) {
            return;
        }
        if (endsAt <= System.currentTimeMillis()) {
            endsAt = 0L;
            persist();
            return;
        }
        schedule();
        VoltuneLog.info("sleep_timer_restored");
    }

    public void cancel() {
        endsAt = 0L;
        handler.removeCallbacks(expiration);
        persist();
        VoltuneLog.info("sleep_timer_cancelled");
    }

    public long getEndsAt() {
        return endsAt;
    }

    public void close() {
        handler.removeCallbacks(expiration);
    }

    public static long readEndsAt(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong(ENDS_AT, 0L);
    }

    private void schedule() {
        handler.removeCallbacks(expiration);
        if (endsAt <= 0L) {
            return;
        }
        long remainingMs = endsAt - System.currentTimeMillis();
        handler.postDelayed(expiration, Math.max(1000L, remainingMs));
    }

    private void persist() {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putLong(ENDS_AT, endsAt)
                .commit();
    }
}
