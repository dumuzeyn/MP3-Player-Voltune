package com.dumuzeyn.mp3player;

import android.graphics.Typeface;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;

/** Lazy, cached lyrics page with a ticker active only while visible. */
final class LyricsPageController implements AutoCloseable {
    private static final long MANUAL_SCROLL_GRACE_MS = 4_000L;
    private final MainActivityCore host;
    private final PlaybackStateProvider state;
    private final ArrayList<TextView> lines = new ArrayList<>();
    private final Runnable ticker = this::tick;
    private FrameLayout root;
    private ScrollView scroll;
    private LinearLayout content;
    private LrcDocument document;
    private String loadedUri = "";
    private int activeLine = -1;
    private long manualScrollUntil;
    private boolean active;

    LyricsPageController(MainActivityCore host, PlaybackStateProvider state) {
        this.host = host;
        this.state = state;
    }

    View createView() {
        if (root != null) {
            return root;
        }
        root = new FrameLayout(host);
        scroll = new ScrollView(host);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(true);
        scroll.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN
                    || event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                manualScrollUntil = System.currentTimeMillis() + MANUAL_SCROLL_GRACE_MS;
            }
            return false;
        });
        content = new LinearLayout(host);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(host.dp(14), host.dp(10), host.dp(14), host.dp(24));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        showLoading();
        if (active) {
            refresh();
        }
        return root;
    }

    void setActive(boolean value) {
        active = value;
        host.uiHandler.removeCallbacks(ticker);
        if (value) {
            refresh();
            scheduleTicker();
        }
    }

    void refresh() {
        Track track = state.currentTrack();
        if (!active || track == null || content == null) {
            return;
        }
        if (track.uri.equals(loadedUri) && document != null) {
            scheduleTicker();
            return;
        }
        loadedUri = track.uri;
        document = null;
        activeLine = -1;
        showLoading();
        String requestedUri = track.uri;
        host.lyricsRepository.load(track, value -> {
            if (root != null && requestedUri.equals(loadedUri)) {
                render(value);
            }
        });
    }

    private void showLoading() {
        if (content == null) return;
        content.removeAllViews();
        TextView loading = host.uiFactory.text(
                host.tr("Looking for local lyrics…", "Ищем локальный текст…"), 17, true);
        loading.setGravity(Gravity.CENTER);
        content.addView(loading, new LinearLayout.LayoutParams(-1, host.dp(110)));
    }

    private void render(LrcDocument value) {
        document = value;
        content.removeAllViews();
        lines.clear();
        if (value.synchronizedLyrics) {
            for (LrcLine line : value.lines) {
                TextView text = host.uiFactory.text(line.text, 18, false);
                text.setGravity(Gravity.CENTER_VERTICAL);
                text.setPadding(host.dp(10), host.dp(12), host.dp(10), host.dp(12));
                text.setOnClickListener(view -> host.playbackActions.seekTo(
                        (int) Math.min(Integer.MAX_VALUE, line.timeMs)));
                content.addView(text, new LinearLayout.LayoutParams(-1, -2));
                lines.add(text);
            }
            scheduleTicker();
        } else if (!value.plainText.isEmpty()) {
            TextView text = host.uiFactory.text(value.plainText, 18, false);
            text.setPadding(host.dp(10), host.dp(12), host.dp(10), host.dp(24));
            content.addView(text, new LinearLayout.LayoutParams(-1, -2));
        } else {
            LinearLayout empty = host.uiFactory.panelCard();
            TextView title = host.uiFactory.text(
                    host.tr("Lyrics not available", "Текст не определён"), 20, true);
            title.setGravity(Gravity.CENTER);
            empty.addView(title, new LinearLayout.LayoutParams(-1, host.dp(110)));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.setMargins(0, host.dp(28), 0, 0);
            content.addView(empty, params);
        }
    }

    private void scheduleTicker() {
        host.uiHandler.removeCallbacks(ticker);
        if (active && document != null && document.synchronizedLyrics) {
            host.uiHandler.post(ticker);
        }
    }

    private void tick() {
        if (!active || document == null || !document.synchronizedLyrics) {
            return;
        }
        int next = document.lineAt(host.playbackPosition());
        if (next != activeLine && next >= 0 && next < lines.size()) {
            if (activeLine >= 0) styleLine(lines.get(activeLine), false);
            activeLine = next;
            styleLine(lines.get(activeLine), true);
            if (System.currentTimeMillis() >= manualScrollUntil) {
                TextView current = lines.get(activeLine);
                scroll.smoothScrollTo(0, Math.max(0,
                        current.getTop() - scroll.getHeight() / 3));
            }
        }
        host.uiHandler.postDelayed(ticker, 250L);
    }

    private void styleLine(TextView line, boolean selected) {
        line.setTextColor(selected ? host.purple : host.secondaryText);
        line.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
    }

    @Override public void close() {
        active = false;
        host.uiHandler.removeCallbacks(ticker);
        lines.clear();
        root = null;
        scroll = null;
        content = null;
        document = null;
        loadedUri = "";
    }
}
