package com.dumuzeyn.mp3player;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

final class FullPlayerController {
    private final MainActivityCore host;
    private final PlaybackActions playbackActions;
    private final PlaybackStateProvider playbackState;
    private FrameLayout currentSheet;
    private ImageView coverView;
    private TextView titleView;
    private TextView subtitleView;
    private Button timerButton;
    private Button likeButton;
    private Button repeatButton;
    private Button playButton;
    private Track boundTrack;
    private Handler progressHandler;
    private Runnable progressUpdater;
    private boolean seekTracking;

    FullPlayerController(MainActivityCore host, PlaybackActions playbackActions,
            PlaybackStateProvider playbackState) {
        this.host = host;
        this.playbackActions = playbackActions;
        this.playbackState = playbackState;
    }

    void open() {
        Track track = playbackState.currentTrack();
        if (track == null) {
            return;
        }
        if (this.currentSheet != null && this.currentSheet.getParent() != null) {
            refreshFromState(true);
            return;
        }
        if (host.miniPlayer != null) {
            host.miniPlayer.setVisibility(View.GONE);
        }
        this.boundTrack = track;
        FrameLayout sheet = createSheet();
        this.currentSheet = sheet;
        sheet.setBackgroundColor(host.appearanceState.playerSolidBackground == 0 ? host.bg : host.appearanceState.playerSolidBackground);
        if (host.appearanceState.playerBackgroundMode == BackgroundSettingsController.MODE_GRADIENT) {
            sheet.addView(new PlayerGradientBackground(host,
                            new PlayerGradientBackground.Config() {
                                @Override
                                public boolean animationsEnabled() {
                                    return host.appearanceState.animations;
                                }

                                @Override
                                public boolean darkTheme() {
                                    return host.appearanceState.dark;
                                }

                                @Override
                                public int baseColor() {
                                    return host.bg;
                                }
                            },
                            host.appearanceState.playerGradientStart, host.appearanceState.playerGradientEnd),
                    new FrameLayout.LayoutParams(-1, -1));
        } else if (host.appearanceState.playerBackgroundMode == BackgroundSettingsController.MODE_MEDIA
                && !host.appearanceState.playerBackgroundMediaUri.isEmpty()) {
            sheet.addView(new BackgroundMediaView(host, host.appearanceState.playerBackgroundMediaUri,
                            host.appearanceState.playerBackgroundBlur, host.bg),
                    new FrameLayout.LayoutParams(-1, -1));
        }

        LinearLayout content = new LinearLayout(host);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(host.dp(16), host.dp(18), host.dp(16), host.dp(20));
        sheet.addView(content, host.responsiveLayoutController.fullPlayerContentParams());

        addHeader(content, sheet);
        addCoverAndTitle(content, track);
        addActionRow(content, sheet, track);
        addAudioToolsRow(content);
        addSeekBar(content, sheet, track);
        content.addView(new View(host), new LinearLayout.LayoutParams(-1, 0, 1.0f));
        addTransportRow(content, sheet);

        boolean animateOpen = host.appearanceState.animations && host.navigationState.fullPlayerOpening;
        host.navigationState.fullPlayerOpening = false;
        host.overlayHost.addView(sheet, new FrameLayout.LayoutParams(-1, -1));
        if (animateOpen) {
            sheet.setTranslationY(host.getResources().getDisplayMetrics().heightPixels);
            sheet.animate().translationY(0.0f).setDuration(145L).setInterpolator(new DecelerateInterpolator()).start();
        }
    }

    void refresh() {
        refreshFromState(true);
    }

    boolean isOpen() {
        return this.currentSheet != null && this.currentSheet.getParent() != null;
    }

    void onHostDestroyed() {
        stopProgressUpdates();
        this.currentSheet = null;
        this.coverView = null;
        this.titleView = null;
        this.subtitleView = null;
        this.timerButton = null;
        this.likeButton = null;
        this.repeatButton = null;
        this.playButton = null;
        this.boundTrack = null;
        this.seekTracking = false;
    }

    boolean closeIfTop(View top) {
        if (top != this.currentSheet || !isOpen()) {
            return false;
        }
        close(this.currentSheet, true);
        return true;
    }

    private FrameLayout createSheet() {
        return new FullPlayerSheet(host, sheet -> close(sheet, true));
    }

    private void addHeader(LinearLayout content, FrameLayout sheet) {
        LinearLayout row = host.uiFactory.row();
        Button back = host.uiFactory.icon("←");
        back.setTextSize(34.0f);
        back.setTypeface(Typeface.DEFAULT_BOLD);
        back.setOnClickListener(view -> close(sheet, false));
        row.addView(back, host.uiFactory.square(58));
        row.addView(new View(host), new LinearLayout.LayoutParams(0, 1, 1.0f));
        Button lyrics = host.uiFactory.icon("≋");
        lyrics.setContentDescription(host.tr("Lyrics", "Текст"));
        lyrics.setOnClickListener(view -> {
            Track current = currentTrack();
            if (current != null) {
                host.lyricsOverlayController.open(current);
            }
        });
        row.addView(lyrics, host.uiFactory.square(58));
        Button queue = host.uiFactory.icon("☰");
        queue.setContentDescription(host.tr("Queue", "Очередь"));
        queue.setOnClickListener(view -> host.overlayController.openQueue());
        row.addView(queue, host.uiFactory.square(58));
        content.addView(row, new LinearLayout.LayoutParams(-1, host.dp(72)));
    }

    private void addCoverAndTitle(LinearLayout content, Track track) {
        ImageView cover = host.uiFactory.coverView();
        this.coverView = cover;
        if (cover instanceof RotatingCoverImageView) {
            ((RotatingCoverImageView) cover).setRotationSpeedPercent(
                    host.appearanceState.fullPlayerRotationSpeed);
        }
        host.artworkUi.loadCover(cover, track,
                host.appearanceState.dark ? Color.rgb(28, 28, 28) : Color.rgb(235, 235, 235),
                MainActivityCore.COVER_FULL_SIZE);
        float density = host.getResources().getDisplayMetrics().density;
        int screenHeightDp = Math.round(host.getResources().getDisplayMetrics().heightPixels / density);
        int coverSizeDp = host.responsiveLayoutController.fullPlayerCoverSizeDp(screenHeightDp);
        LinearLayout.LayoutParams coverParams = new LinearLayout.LayoutParams(host.dp(coverSizeDp), host.dp(coverSizeDp));
        coverParams.gravity = 1;
        content.addView(cover, coverParams);

        TextView title = host.uiFactory.text(track.title, 24, true);
        this.titleView = title;
        title.setGravity(17);
        content.addView(title, new LinearLayout.LayoutParams(-1, host.dp(54)));
        int queueSize = playbackState.activeQueue().size();
        TextView subtitle = host.uiFactory.text(track.artist + " · "
                + (playbackState.queueIndex(track) + 1) + " "
                + host.tr3("of", "из", "/") + " " + queueSize, 15, false);
        this.subtitleView = subtitle;
        subtitle.setGravity(17);
        content.addView(subtitle, new LinearLayout.LayoutParams(-1, host.dp(34)));
    }

    private void addActionRow(LinearLayout content, FrameLayout sheet, Track track) {
        LinearLayout row = host.uiFactory.row();
        Button timer = host.uiFactory.button(host.timerButtonText());
        this.timerButton = timer;
        host.uiFactory.applyPlayerToolStyle(timer, host.playbackUiState.sleepTimerEndsAt > 0);
        timer.setSingleLine(true);
        timer.setOnClickListener(view -> host.sleepTimerController.openDialog());
        row.addView(timer, toolButtonParams());

        Button like = host.uiFactory.button(saveButtonText(track));
        this.likeButton = like;
        like.setSingleLine(true);
        like.setTextSize(14.0f);
        host.uiFactory.applyPlayerToolStyle(like, host.libraryState.favorites.contains(track.uri));
        like.setOnClickListener(view -> {
            Track current = currentTrack();
            if (current != null) {
                host.overlayController.chooseCollection(current);
            }
        });
        row.addView(like, toolButtonParams());

        Button repeat = host.uiFactory.button(host.loopLabel());
        this.repeatButton = repeat;
        styleRepeatButton(repeat);
        host.uiFactory.applyPlayerToolStyle(repeat, playbackState.repeatMode() != 0);
        repeat.setSingleLine(true);
        repeat.setOnClickListener(view -> {
            playbackActions.cycleRepeatMode();
            refreshFromState(false);
        });
        row.addView(repeat, toolButtonParams());
        content.addView(row);
    }

    private LinearLayout.LayoutParams toolButtonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, host.dp(52), 1.0f);
        params.setMargins(host.dp(3), host.dp(3), host.dp(3), host.dp(3));
        return params;
    }

    private void addAudioToolsRow(LinearLayout content) {
        LinearLayout row = host.uiFactory.row();
        Button equalizer = host.equalizerController.createPlayerButton();
        Button leveling = host.volumeLevelingController.createPlayerButton();
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, host.dp(52), 1.0f);
        left.setMargins(0, host.dp(3), host.dp(4), host.dp(3));
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, host.dp(52), 1.0f);
        right.setMargins(host.dp(4), host.dp(3), 0, host.dp(3));
        row.addView(equalizer, left);
        row.addView(leveling, right);
        content.addView(row);
    }

    private void addSeekBar(LinearLayout content, FrameLayout sheet, Track track) {
        SeekBar seek = new SeekBar(host);
        final boolean[] seekDragged = {false};
        final float[] seekTouchX = {0.0f};
        seek.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                seekTouchX[0] = event.getX();
                seekDragged[0] = false;
            } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE
                    && Math.abs(event.getX() - seekTouchX[0]) > host.dp(8)) {
                seekDragged[0] = true;
            }
            return false;
        });
        host.uiFactory.applySeekBarColors(seek);
        int displayDuration = host.playbackDurationFor(track);
        seek.setMax(Math.max(1, displayDuration));
        seek.setProgress(Math.max(0, host.playbackPosition()));
        content.addView(seek, new LinearLayout.LayoutParams(-1, host.dp(42)));

        LinearLayout row = host.uiFactory.row();
        TextView elapsed = host.uiFactory.text(host.formatMs(host.playbackPosition()), 13, false);
        TextView remain = host.uiFactory.text("-" + host.formatMs(
                Math.max(0, displayDuration - host.playbackPosition())), 13, false);
        remain.setGravity(android.view.Gravity.END | android.view.Gravity.CENTER_VERTICAL);
        row.addView(elapsed, new LinearLayout.LayoutParams(0, host.dp(28), 1.0f));
        row.addView(remain, new LinearLayout.LayoutParams(0, host.dp(28), 1.0f));
        content.addView(row);

        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    RotatingCoverImageView rotatingCover = rotatingCover();
                    if (rotatingCover != null && seekDragged[0]) {
                        rotatingCover.updateSeekSpin(progress);
                    }
                    elapsed.setText(host.formatMs(progress));
                    Track current = currentTrack();
                    int duration = current == null ? seekBar.getMax() : host.playbackDurationFor(current);
                    remain.setText("-" + host.formatMs(Math.max(0, duration - progress)));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                seekTracking = true;
                RotatingCoverImageView rotatingCover = rotatingCover();
                if (rotatingCover != null) {
                    rotatingCover.beginSeekSpin(Math.max(0, host.playbackPosition()));
                }
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                RotatingCoverImageView rotatingCover = rotatingCover();
                if (rotatingCover != null) {
                    rotatingCover.endSeekSpin(seekBar.getProgress(), !seekDragged[0]);
                }
                playbackActions.seekTo(seekBar.getProgress());
                seekTracking = false;
            }
        });

        stopProgressUpdates();
        this.progressHandler = new Handler(Looper.getMainLooper());
        this.progressUpdater = new ProgressUpdater(
                sheet, track, seek, elapsed, remain, this.progressHandler);
        this.progressHandler.postDelayed(this.progressUpdater, 700L);
    }

    private void addTransportRow(LinearLayout content, FrameLayout sheet) {
        LinearLayout row = host.uiFactory.row();
        row.setGravity(17);
        Button previous = host.uiFactory.icon("⏮");
        previous.setOnClickListener(view -> {
            playbackActions.previous();
            refreshFromState(true);
        });
        row.addView(previous, host.uiFactory.square(68));

        Button play = host.uiFactory.icon(playbackState.isPlaying() ? "Ⅱ" : "▶");
        this.playButton = play;
        play.setOnClickListener(view -> {
            playbackActions.togglePlayPause();
            refreshFromState(false);
        });
        row.addView(play, host.uiFactory.square(84));

        Button next = host.uiFactory.icon("⏭");
        next.setOnClickListener(view -> {
            playbackActions.next();
            refreshFromState(true);
        });
        row.addView(next, host.uiFactory.square(68));
        content.addView(row, new LinearLayout.LayoutParams(-1, host.dp(112)));
    }

    private void close(FrameLayout sheet, boolean animate) {
        if (sheet == this.currentSheet) {
            stopProgressUpdates();
        }
        if (sheet == null || sheet.getParent() == null) {
            host.playerUiController.updateMini();
        } else if (animate && host.appearanceState.animations) {
            sheet.animate()
                    .translationY(host.getResources().getDisplayMetrics().heightPixels)
                    .alpha(0.0f)
                    .setDuration(135L)
                    .setInterpolator(new DecelerateInterpolator())
                    .withEndAction(() -> {
                        if (sheet.getParent() != null) {
                            host.overlayHost.removeView(sheet);
                        }
                        host.playerUiController.updateMini();
                    })
                    .start();
        } else {
            host.overlayHost.removeView(sheet);
            host.playerUiController.updateMini();
        }
        if (sheet == this.currentSheet) {
            this.currentSheet = null;
            this.boundTrack = null;
        }
    }

    private void stopProgressUpdates() {
        if (this.progressHandler != null && this.progressUpdater != null) {
            this.progressHandler.removeCallbacks(this.progressUpdater);
        }
        this.progressUpdater = null;
        this.progressHandler = null;
        this.seekTracking = false;
    }

    private void refreshFromState(boolean allowTrackChange) {
        Track track = playbackState.currentTrack();
        if (track == null) {
            return;
        }
        if (allowTrackChange && (this.boundTrack == null || !this.boundTrack.uri.equals(track.uri))) {
            this.boundTrack = track;
            if (this.coverView != null) {
                host.artworkUi.loadCover(this.coverView, track,
                        host.appearanceState.dark ? Color.rgb(28, 28, 28) : Color.rgb(235, 235, 235),
                        MainActivityCore.COVER_FULL_SIZE);
            }
        }
        if (this.titleView != null) {
            this.titleView.setText(track.title);
        }
        if (this.subtitleView != null) {
            this.subtitleView.setText(track.artist + " · "
                    + (playbackState.queueIndex(track) + 1) + " "
                    + host.tr3("of", "из", "/") + " "
                    + playbackState.activeQueue().size());
        }
        if (this.timerButton != null) {
            this.timerButton.setText(host.timerButtonText());
            host.uiFactory.applyPlayerToolStyle(this.timerButton, host.playbackUiState.sleepTimerEndsAt > 0);
        }
        if (this.likeButton != null) {
            this.likeButton.setText(saveButtonText(track));
            host.uiFactory.applyPlayerToolStyle(this.likeButton, host.libraryState.favorites.contains(track.uri));
        }
        if (this.repeatButton != null) {
            this.repeatButton.setText(host.loopLabel());
            styleRepeatButton(this.repeatButton);
            host.uiFactory.applyPlayerToolStyle(
                    this.repeatButton, playbackState.repeatMode() != 0);
        }
        if (this.playButton != null) {
            this.playButton.setText(playbackState.isPlaying() ? "Ⅱ" : "▶");
        }
        RotatingCoverImageView rotatingCover = rotatingCover();
        if (rotatingCover != null) {
            rotatingCover.updatePlaybackState();
        }
    }

    private void styleRepeatButton(Button button) {
        button.setTextSize(14.0f);
    }

    private String saveButtonText(Track track) {
        return host.libraryState.favorites.contains(track.uri)
                ? host.tr("Saved ♥︎", "Добавлено ♥︎")
                : host.tr("Save ♡︎", "Добавить ♡︎");
    }

    private Track currentTrack() {
        return playbackState.currentTrack();
    }

    private RotatingCoverImageView rotatingCover() {
        return this.coverView instanceof RotatingCoverImageView
                ? (RotatingCoverImageView) this.coverView
                : null;
    }

    private final class ProgressUpdater implements Runnable {
        private final FrameLayout sheet;
        private Track track;
        private final SeekBar seek;
        private final TextView elapsed;
        private final TextView remain;
        private final Handler handler;

        ProgressUpdater(FrameLayout sheet, Track track, SeekBar seek, TextView elapsed, TextView remain, Handler handler) {
            this.sheet = sheet;
            this.track = track;
            this.seek = seek;
            this.elapsed = elapsed;
            this.remain = remain;
            this.handler = handler;
        }

        @Override
        public void run() {
            if (sheet.getParent() == null) {
                return;
            }
            if (playbackState.currentTrack() == null) {
                host.overlayHost.removeView(sheet);
                host.playerUiController.updateMini();
                host.render();
                return;
            }
            Track resolvedTrack = currentTrack();
            if (resolvedTrack != null && !resolvedTrack.uri.equals(track.uri)) {
                this.track = resolvedTrack;
                refreshFromState(true);
                host.render();
            }
            int displayDuration = host.playbackDurationFor(track);
            int position = host.playbackPosition();
            if (!seekTracking) {
                seek.setMax(Math.max(1, displayDuration));
                seek.setProgress(Math.max(0, position));
                elapsed.setText(host.formatMs(position));
                remain.setText("-" + host.formatMs(Math.max(0, displayDuration - position)));
            }
            if (timerButton != null) {
                timerButton.setText(host.timerButtonText());
            }
            if (handler == progressHandler && this == progressUpdater) {
                handler.postDelayed(this, 250L);
            }
        }
    }
}
