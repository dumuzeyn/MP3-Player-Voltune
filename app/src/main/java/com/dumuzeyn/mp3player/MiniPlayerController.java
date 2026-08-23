package com.dumuzeyn.mp3player;

import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

final class MiniPlayerController {
    private final MainActivityCore host;
    private final PlaybackActions playbackActions;
    private final PlaybackStateProvider playbackState;
    private boolean draggingMiniPlayer = false;

    MiniPlayerController(MainActivityCore host, PlaybackActions playbackActions,
            PlaybackStateProvider playbackState) {
        this.host = host;
        this.playbackActions = playbackActions;
        this.playbackState = playbackState;
    }

    void build(FrameLayout root) {
        host.miniPlayer = new LinearLayout(host);
        host.miniPlayer.setOrientation(LinearLayout.HORIZONTAL);
        host.miniPlayer.setGravity(16);
        host.miniPlayer.setPadding(host.dp(14), 0, host.dp(10), 0);
        host.uiFactory.applyCardStyle(host.miniPlayer, host.appearanceState.miniPlayerCardOpacity);
        host.miniPlayer.setVisibility(View.GONE);
        host.miniPlayer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openFullPlayerFromMini(view);
            }
        });
        host.miniPlayer.setOnTouchListener(new MiniSwipeListener());

        LinearLayout textColumn = new LinearLayout(host);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        host.miniTitle = host.uiFactory.text(host.tr("Song", "Песня"), 16, true);
        host.miniSub = host.uiFactory.text(host.tr("Unknown artist", "Неизвестный исполнитель"), 12, false);
        host.miniTitle.setSingleLine(true);
        host.miniTitle.setEllipsize(TextUtils.TruncateAt.END);
        host.miniSub.setSingleLine(true);
        host.miniSub.setEllipsize(TextUtils.TruncateAt.END);
        textColumn.addView(host.miniTitle);
        textColumn.addView(host.miniSub);
        host.miniPlayer.addView(textColumn, new LinearLayout.LayoutParams(0, -2, 1.0f));

        host.miniButton = host.uiFactory.icon("▶");
        host.uiFactory.applyPrimaryButtonStyle(host.miniButton);
        host.miniButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                playbackActions.togglePlayPause();
            }
        });
        host.miniPlayer.addView(host.miniButton, host.uiFactory.square(52));

        root.addView(host.miniPlayer, host.responsiveLayoutController.miniPlayerParams());
    }

    void updateState() {
        if (!hasMiniPlayer()) {
            return;
        }
        Track track = currentTrack();
        if (track == null || isOverlayOpen()) {
            hideMiniPlayer();
            return;
        }
        bindMiniPlayer(track);
        showMiniPlayer();
    }

    boolean isInsideMiniPlayer(MotionEvent event) {
        if (!hasMiniPlayer() || host.miniPlayer.getVisibility() != View.VISIBLE) {
            return false;
        }
        int[] location = new int[2];
        host.miniPlayer.getLocationOnScreen(location);
        float rawX = event.getRawX();
        float rawY = event.getRawY();
        return rawX >= location[0]
                && rawX <= location[0] + host.miniPlayer.getWidth()
                && rawY >= location[1]
                && rawY <= location[1] + host.miniPlayer.getHeight();
    }

    private void openFullPlayerFromMini(final View view) {
        if (host.appearanceState.animations) {
            view.animate().scaleX(0.985f).scaleY(0.985f).setDuration(35L).withEndAction(new Runnable() {
                @Override
                public void run() {
                    view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(60L).start();
                    host.navigationState.fullPlayerOpening = true;
                    host.playerUiController.openFullPlayer();
                }
            }).start();
        } else {
            host.navigationState.fullPlayerOpening = true;
            host.playerUiController.openFullPlayer();
        }
    }

    private boolean hasMiniPlayer() {
        return host.miniPlayer != null;
    }

    private Track currentTrack() {
        return playbackState.currentTrack();
    }

    private boolean isOverlayOpen() {
        return host.overlayHost.getChildCount() > 0;
    }

    private void bindMiniPlayer(Track track) {
        host.miniTitle.setText(track.title);
        host.miniSub.setText(track.artist);
        host.miniButton.setText(playbackState.isPlaying() ? "Ⅱ" : "▶");
    }

    private void hideMiniPlayer() {
        host.miniPlayer.setVisibility(View.GONE);
    }

    private void showMiniPlayer() {
        if (draggingMiniPlayer) {
            host.miniPlayer.setVisibility(View.VISIBLE);
            return;
        }
        host.miniPlayer.setTranslationX(0.0f);
        host.miniPlayer.setAlpha(1.0f);
        host.miniPlayer.setVisibility(View.VISIBLE);
    }

    private final class MiniSwipeListener implements View.OnTouchListener {
        private float startX;
        private float startY;
        private boolean dragging;

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                startX = event.getRawX();
                startY = event.getRawY();
                dragging = false;
                view.animate().cancel();
                return true;
            }
            if (action == MotionEvent.ACTION_MOVE) {
                float dx = event.getRawX() - startX;
                float dy = event.getRawY() - startY;
                if (!dragging && Math.abs(dx) > host.dp(16) && Math.abs(dx) > Math.abs(dy) * 1.4f) {
                    dragging = true;
                    draggingMiniPlayer = true;
                    view.getParent().requestDisallowInterceptTouchEvent(true);
                }
                if (dragging) {
                    host.miniPlayer.setTranslationX(dx);
                    host.miniPlayer.setAlpha(Math.max(0.35f, 1.0f - (Math.abs(dx) / Math.max(1, host.miniPlayer.getWidth()))));
                    return true;
                }
                return false;
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                if (!dragging) {
                    view.performClick();
                    return true;
                }
                float dx = event.getRawX() - startX;
                dragging = false;
                if (Math.abs(dx) >= Math.max(host.dp(96), view.getWidth() * 0.28f)) {
                    dismissMiniPlayer(dx);
                } else {
                    draggingMiniPlayer = false;
                    host.miniPlayer.animate().translationX(0.0f).alpha(1.0f).setDuration(host.appearanceState.animations ? 120L : 0L).start();
                }
                return true;
            }
            return false;
        }
    }

    private void dismissMiniPlayer(float dx) {
        float target = dx < 0.0f ? -host.miniPlayer.getWidth() : host.miniPlayer.getWidth();
        if (host.appearanceState.animations) {
            host.miniPlayer.animate().translationX(target).alpha(0.0f).setDuration(130L).withEndAction(new Runnable() {
                @Override
                public void run() {
                    draggingMiniPlayer = false;
                    host.stopPlaybackAndClearQueue();
                    host.miniPlayer.setTranslationX(0.0f);
                    host.miniPlayer.setAlpha(1.0f);
                }
            }).start();
        } else {
            draggingMiniPlayer = false;
            host.stopPlaybackAndClearQueue();
            host.miniPlayer.setTranslationX(0.0f);
            host.miniPlayer.setAlpha(1.0f);
        }
    }
}
