package com.dumuzeyn.mp3player;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.view.View;
import java.util.ArrayList;

final class SmoothPlaylistTicker extends View {
    private static final int VISIBLE_LINES = 3;

    private final MainActivityCore host;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ArrayList<String> titles = new ArrayList<>();
    private final Rect visibleBounds = new Rect();
    private float scrollOffset;
    private long lastFrameNanos;
    private int lineHeight;
    private String contentKey = "";

    SmoothPlaylistTicker(MainActivityCore host) {
        super(host);
        this.host = host;
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextSize(sp(16));
        paint.setColor(host.primaryText);
    }

    void bindTracks(ArrayList<Track> tracks) {
        String key = buildKey(tracks);
        if (key.equals(contentKey)) {
            return;
        }
        contentKey = key;
        titles.clear();
        for (Track track : tracks) {
            titles.add(track.title == null || track.title.trim().isEmpty()
                    ? host.tr("Unknown track", "Неизвестная песня")
                    : track.title.trim());
        }
        if (titles.isEmpty()) {
            titles.add(host.tr("No songs in this playlist yet.", "В плейлисте пока нет песен."));
        }
        scrollOffset = 0.0f;
        lastFrameNanos = 0L;
        requestLayout();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        Paint.FontMetricsInt metrics = paint.getFontMetricsInt();
        lineHeight = Math.max(1, (metrics.descent - metrics.ascent) + host.dp(3));
        int visibleLines = PlaylistTickerLayoutPolicy.visibleLineCount(
                titles.size(), VISIBLE_LINES);
        int desiredHeight = (lineHeight * visibleLines)
                + getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec),
                resolveSize(desiredHeight, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (titles.isEmpty() || lineHeight <= 0) {
            return;
        }
        paint.setColor(host.primaryText);
        boolean scrolling = titles.size() > VISIBLE_LINES
                && host.appearanceState.animations
                && host.appearanceState.playlistTickerSpeed > 0
                && isVisibleToUser();
        if (scrolling) {
            advanceScroll();
        } else {
            lastFrameNanos = 0L;
            scrollOffset = 0.0f;
        }

        int firstLine = scrolling ? (int) (scrollOffset / lineHeight) : 0;
        float remainder = scrolling ? scrollOffset % lineHeight : 0.0f;
        float top = getPaddingTop() - remainder;
        Paint.FontMetrics metrics = paint.getFontMetrics();
        int linesToDraw = scrolling
                ? VISIBLE_LINES + 2
                : PlaylistTickerLayoutPolicy.staticLinesToDraw(
                        titles.size(), VISIBLE_LINES);
        for (int line = 0; line < linesToDraw; line++) {
            if (top >= getHeight() - getPaddingBottom()) {
                break;
            }
            int index = (firstLine + line) % titles.size();
            float baseline = top - metrics.ascent;
            if (baseline + metrics.descent > 0.0f) {
                canvas.drawText(ellipsize(titles.get(index)), getPaddingLeft(), baseline, paint);
            }
            top += lineHeight;
        }
        if (scrolling && isAttachedToWindow()) {
            postInvalidateOnAnimation();
        }
    }

    boolean isVisibleToUser() {
        return isShown() && getGlobalVisibleRect(visibleBounds)
                && visibleBounds.width() > 0 && visibleBounds.height() > 0;
    }

    private void advanceScroll() {
        long now = System.nanoTime();
        if (lastFrameNanos != 0L) {
            float seconds = Math.min(0.05f, (now - lastFrameNanos) / 1_000_000_000.0f);
            scrollOffset += host.dp(13) * seconds * (host.appearanceState.playlistTickerSpeed / 100.0f);
            float loopHeight = lineHeight * titles.size();
            if (scrollOffset >= loopHeight) {
                scrollOffset %= loopHeight;
            }
        }
        lastFrameNanos = now;
    }

    private String ellipsize(String value) {
        float available = Math.max(1.0f, getWidth() - getPaddingLeft() - getPaddingRight());
        if (paint.measureText(value) <= available) {
            return value;
        }
        String suffix = "…";
        float suffixWidth = paint.measureText(suffix);
        int count = paint.breakText(value, true, Math.max(1.0f, available - suffixWidth), null);
        return value.substring(0, Math.max(0, count)).trim() + suffix;
    }

    private String buildKey(ArrayList<Track> tracks) {
        StringBuilder builder = new StringBuilder();
        for (Track track : tracks) {
            builder.append(track.uri).append('|').append(track.title).append(';');
        }
        return builder.toString();
    }

    private float sp(int value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }
}
