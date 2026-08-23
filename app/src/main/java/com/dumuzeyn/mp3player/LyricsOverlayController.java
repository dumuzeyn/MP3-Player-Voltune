package com.dumuzeyn.mp3player;

import android.graphics.Typeface;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;

final class LyricsOverlayController {
    private static final long MANUAL_SCROLL_GRACE_MS = 4_000L;
    private final MainActivityCore host;
    private final ArrayList<TextView> lineViews = new ArrayList<>();
    private final Runnable ticker = this::tick;
    private FrameLayout shade;
    private ScrollView scroll;
    private LrcDocument document;
    private int activeLine = -1;
    private long manualScrollUntil;

    LyricsOverlayController(MainActivityCore host) {
        this.host = host;
    }

    void open(Track track) {
        close();
        shade = host.uiFactory.shade();
        LinearLayout panel = host.uiFactory.panelCard();
        panel.addView(header(track));
        scroll = new ScrollView(host);
        scroll.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN
                    || event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                manualScrollUntil = System.currentTimeMillis() + MANUAL_SCROLL_GRACE_MS;
            }
            return false;
        });
        LinearLayout rows = new LinearLayout(host);
        rows.setOrientation(LinearLayout.VERTICAL);
        TextView loading = host.uiFactory.text(
                host.tr("Looking for local lyrics…", "Ищем локальный текст…"), 16, false);
        rows.addView(loading);
        scroll.addView(rows);
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1.0f));
        shade.addView(panel, host.bottomParams());
        host.overlayHost.addView(shade);
        host.lyricsRepository.load(track, value -> render(rows, value));
    }

    void close() {
        host.uiHandler.removeCallbacks(ticker);
        if (shade != null && shade.getParent() != null) {
            host.overlayHost.removeView(shade);
        }
        shade = null;
        scroll = null;
        document = null;
        lineViews.clear();
        activeLine = -1;
    }

    private LinearLayout header(Track track) {
        LinearLayout row = host.uiFactory.row();
        TextView title = host.uiFactory.text(host.tr("Lyrics", "Текст") + " · "
                + track.title, 19, true);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(title, new LinearLayout.LayoutParams(0, host.dp(58), 1.0f));
        Button queue = host.uiFactory.icon("☰");
        queue.setContentDescription(host.tr("Queue", "Очередь"));
        queue.setOnClickListener(view -> {
            close();
            host.overlayController.openQueue();
        });
        row.addView(queue, host.uiFactory.square(50));
        Button close = host.uiFactory.icon("×");
        close.setContentDescription(host.tr("Close", "Закрыть"));
        close.setOnClickListener(view -> close());
        row.addView(close, host.uiFactory.square(50));
        return row;
    }

    private void render(LinearLayout rows, LrcDocument value) {
        if (shade == null || shade.getParent() == null) {
            return;
        }
        document = value;
        rows.removeAllViews();
        lineViews.clear();
        if (value.synchronizedLyrics) {
            for (int index = 0; index < value.lines.size(); index++) {
                LrcLine line = value.lines.get(index);
                TextView view = host.uiFactory.text(line.text, 17, false);
                view.setPadding(host.dp(6), host.dp(10), host.dp(6), host.dp(10));
                view.setOnClickListener(clicked -> host.playbackActions.seekTo(
                        (int) Math.min(Integer.MAX_VALUE, line.timeMs)));
                rows.addView(view, new LinearLayout.LayoutParams(-1, -2));
                lineViews.add(view);
            }
            host.uiHandler.post(ticker);
        } else if (!value.plainText.isEmpty()) {
            TextView text = host.uiFactory.text(value.plainText, 17, false);
            text.setPadding(host.dp(6), host.dp(10), host.dp(6), host.dp(24));
            rows.addView(text);
        } else {
            rows.addView(host.uiFactory.text(host.tr(
                    "No local .lrc or .txt lyrics found next to this song.",
                    "Рядом с песней не найден локальный .lrc или .txt."), 16, false));
        }
    }

    private void tick() {
        if (shade == null || shade.getParent() == null || document == null
                || !document.synchronizedLyrics) {
            return;
        }
        int next = document.lineAt(host.playbackPosition());
        if (next != activeLine && next >= 0 && next < lineViews.size()) {
            if (activeLine >= 0) {
                styleLine(lineViews.get(activeLine), false);
            }
            activeLine = next;
            styleLine(lineViews.get(activeLine), true);
            if (System.currentTimeMillis() >= manualScrollUntil && scroll != null) {
                TextView active = lineViews.get(activeLine);
                scroll.smoothScrollTo(0, Math.max(0, active.getTop() - scroll.getHeight() / 3));
            }
        }
        host.uiHandler.postDelayed(ticker, 250L);
    }

    private void styleLine(TextView line, boolean active) {
        line.setTextColor(active ? host.purple : host.secondaryText);
        line.setTypeface(Typeface.DEFAULT, active ? Typeface.BOLD : Typeface.NORMAL);
    }
}
