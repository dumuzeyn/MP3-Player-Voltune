package com.dumuzeyn.mp3player;

import android.graphics.Color;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

/** Builds playback controls once and binds them to the shared Media3 state. */
final class FullPlayerPlaybackPage implements AutoCloseable {
    private final MainActivityCore host;
    private final PlaybackActions actions;
    private final PlaybackStateProvider state;
    private final FullPlayerProgressController progress;
    private ScrollView root;
    private ImageView cover;
    private TextView title;
    private TextView subtitle;
    private Button timer;
    private Button save;
    private Button repeat;
    private Button play;
    private Track boundTrack;
    private boolean active;

    FullPlayerPlaybackPage(MainActivityCore host, PlaybackActions actions,
            PlaybackStateProvider state) {
        this.host = host;
        this.actions = actions;
        this.state = state;
        progress = new FullPlayerProgressController(host, state,
                () -> refresh(true));
    }

    View createView() {
        if (root != null) {
            return root;
        }
        root = new ScrollView(host);
        root.setFillViewport(true);
        LinearLayout content = new LinearLayout(host);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(host.dp(6), 0, host.dp(6), host.dp(8));
        root.addView(content, new ScrollView.LayoutParams(-1, -2));
        Track track = state.currentTrack();
        if (track == null) {
            return root;
        }
        addCoverAndTitle(content, track);
        addActionRow(content, track);
        addAudioTools(content);
        addSeek(content, track);
        addTransport(content);
        boundTrack = track;
        refresh(false);
        progress.setActive(active);
        return root;
    }

    void setActive(boolean value) {
        active = value;
        progress.setActive(value && root != null);
        if (value) {
            refresh(true);
        }
        RotatingCoverImageView rotating = rotatingCover();
        if (rotating != null) {
            rotating.setUiActive(value);
        }
    }

    void refresh(boolean allowTrackChange) {
        Track track = state.currentTrack();
        if (track == null || root == null) {
            return;
        }
        boolean changed = boundTrack == null || !boundTrack.uri.equals(track.uri);
        if (allowTrackChange && changed) {
            boundTrack = track;
            host.artworkUi.loadCover(cover, track, coverFallback(),
                    MainActivityCore.COVER_FULL_SIZE);
        }
        title.setText(track.title);
        subtitle.setText(track.artist + " · " + (state.queueIndex(track) + 1) + " "
                + host.tr3("of", "из", "/") + " " + state.activeQueue().size());
        timer.setText(host.timerButtonText());
        host.uiFactory.applyPlayerToolStyle(timer, host.playbackUiState.sleepTimerEndsAt > 0);
        save.setText(saveText(track));
        host.uiFactory.applyPlayerToolStyle(save,
                host.libraryState.favorites.contains(track.uri));
        repeat.setText(host.loopLabel());
        host.uiFactory.applyPlayerToolStyle(repeat, state.repeatMode() != 0);
        play.setText(state.isPlaying() ? "Ⅱ" : "▶");
        RotatingCoverImageView rotating = rotatingCover();
        if (rotating != null) {
            rotating.updatePlaybackState();
        }
    }

    private void addCoverAndTitle(LinearLayout content, Track track) {
        cover = host.uiFactory.coverView();
        if (cover instanceof RotatingCoverImageView) {
            ((RotatingCoverImageView) cover).setRotationSpeedPercent(
                    host.appearanceState.fullPlayerRotationSpeed);
        }
        host.artworkUi.loadCover(cover, track, coverFallback(),
                MainActivityCore.COVER_FULL_SIZE);
        float density = host.getResources().getDisplayMetrics().density;
        int screenDp = Math.round(host.getResources().getDisplayMetrics().heightPixels / density);
        int sizeDp = host.responsiveLayoutController.fullPlayerCoverSizeDp(screenDp);
        LinearLayout.LayoutParams coverParams = new LinearLayout.LayoutParams(
                host.dp(sizeDp), host.dp(sizeDp));
        coverParams.gravity = Gravity.CENTER_HORIZONTAL;
        content.addView(cover, coverParams);
        title = host.uiFactory.text(track.title, 24, true);
        title.setGravity(Gravity.CENTER);
        title.setMaxLines(2);
        content.addView(title, new LinearLayout.LayoutParams(-1, -2));
        subtitle = host.uiFactory.text("", 15, false);
        subtitle.setGravity(Gravity.CENTER);
        content.addView(subtitle, new LinearLayout.LayoutParams(-1, host.dp(34)));
    }

    private void addActionRow(LinearLayout content, Track track) {
        LinearLayout row = host.uiFactory.row();
        timer = host.uiFactory.button(host.timerButtonText());
        timer.setOnClickListener(view -> host.sleepTimerController.openDialog());
        row.addView(timer, toolParams());
        save = host.uiFactory.button(saveText(track));
        save.setOnClickListener(view -> {
            Track current = state.currentTrack();
            if (current != null) {
                host.overlayController.chooseCollection(current);
            }
        });
        row.addView(save, toolParams());
        repeat = host.uiFactory.button(host.loopLabel());
        repeat.setOnClickListener(view -> {
            actions.cycleRepeatMode();
            refresh(false);
        });
        row.addView(repeat, toolParams());
        content.addView(row);
    }

    private void addAudioTools(LinearLayout content) {
        LinearLayout row = host.uiFactory.row();
        row.addView(host.equalizerController.createPlayerButton(), halfParams(true));
        row.addView(host.volumeLevelingController.createPlayerButton(), halfParams(false));
        content.addView(row);
    }

    private void addSeek(LinearLayout content, Track track) {
        SeekBar seek = new SeekBar(host);
        host.uiFactory.applySeekBarColors(seek);
        TextView elapsed = host.uiFactory.text("0:00", 13, false);
        TextView remaining = host.uiFactory.text("-0:00", 13, false);
        final boolean[] dragged = {false};
        final float[] startX = {0.0f};
        seek.setOnTouchListener((view, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                startX[0] = event.getX();
                dragged[0] = false;
                view.getParent().requestDisallowInterceptTouchEvent(true);
            } else if (action == MotionEvent.ACTION_MOVE
                    && Math.abs(event.getX() - startX[0]) > host.dp(8)) {
                dragged[0] = true;
            } else if (action == MotionEvent.ACTION_UP
                    || action == MotionEvent.ACTION_CANCEL) {
                view.getParent().requestDisallowInterceptTouchEvent(false);
            }
            return false;
        });
        seek.setOnSeekBarChangeListener(seekListener(elapsed, remaining, dragged));
        content.addView(seek, new LinearLayout.LayoutParams(-1, host.dp(42)));
        LinearLayout times = host.uiFactory.row();
        remaining.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        times.addView(elapsed, new LinearLayout.LayoutParams(0, host.dp(28), 1.0f));
        times.addView(remaining, new LinearLayout.LayoutParams(0, host.dp(28), 1.0f));
        content.addView(times);
        progress.bind(root, track, seek, elapsed, remaining);
    }

    private SeekBar.OnSeekBarChangeListener seekListener(TextView elapsed,
            TextView remaining, boolean[] dragged) {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean user) {
                if (!user) return;
                RotatingCoverImageView rotating = rotatingCover();
                if (rotating != null && dragged[0]) rotating.updateSeekSpin(value);
                elapsed.setText(host.formatMs(value));
                Track current = state.currentTrack();
                int duration = current == null ? bar.getMax() : host.playbackDurationFor(current);
                remaining.setText("-" + host.formatMs(Math.max(0, duration - value)));
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {
                progress.setSeekTracking(true);
                RotatingCoverImageView rotating = rotatingCover();
                if (rotating != null) rotating.beginSeekSpin(host.playbackPosition());
            }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                RotatingCoverImageView rotating = rotatingCover();
                if (rotating != null) rotating.endSeekSpin(bar.getProgress(), !dragged[0]);
                actions.seekTo(bar.getProgress());
                progress.setSeekTracking(false);
            }
        };
    }

    private void addTransport(LinearLayout content) {
        LinearLayout row = host.uiFactory.row();
        row.setGravity(Gravity.CENTER);
        Button previous = host.uiFactory.icon("⏮");
        previous.setOnClickListener(view -> { actions.previous(); refresh(true); });
        row.addView(previous, host.uiFactory.square(68));
        play = host.uiFactory.icon(state.isPlaying() ? "Ⅱ" : "▶");
        play.setOnClickListener(view -> { actions.togglePlayPause(); refresh(false); });
        row.addView(play, host.uiFactory.square(84));
        Button next = host.uiFactory.icon("⏭");
        next.setOnClickListener(view -> { actions.next(); refresh(true); });
        row.addView(next, host.uiFactory.square(68));
        content.addView(row, new LinearLayout.LayoutParams(-1, host.dp(100)));
    }

    private LinearLayout.LayoutParams toolParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, host.dp(52), 1f);
        params.setMargins(host.dp(3), host.dp(3), host.dp(3), host.dp(3));
        return params;
    }

    private LinearLayout.LayoutParams halfParams(boolean left) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, host.dp(52), 1f);
        params.setMargins(left ? 0 : host.dp(4), host.dp(3),
                left ? host.dp(4) : 0, host.dp(3));
        return params;
    }

    private int coverFallback() {
        return host.appearanceState.dark ? Color.rgb(28, 28, 28) : Color.rgb(235, 235, 235);
    }

    private String saveText(Track track) {
        return host.libraryState.favorites.contains(track.uri)
                ? host.tr("Saved ♥︎", "Добавлено ♥︎")
                : host.tr("Save ♡︎", "Добавить ♡︎");
    }

    private RotatingCoverImageView rotatingCover() {
        return cover instanceof RotatingCoverImageView ? (RotatingCoverImageView) cover : null;
    }

    @Override public void close() {
        progress.close();
        if (cover != null) host.artworkUi.clearCover(cover, coverFallback());
        root = null;
        cover = null;
        boundTrack = null;
    }
}
