package com.dumuzeyn.mp3player;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Trace;
import android.view.View;

public class WaveformView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int seed;
    private int color;
    private int accentColor;
    private boolean active;
    private boolean transitionPaused;
    private boolean frameScheduled;
    private float progress;
    private long startedAt;
    private final Runnable nextFrame = () -> {
        frameScheduled = false;
        if (shouldAnimate()) invalidate();
    };

    public WaveformView(Context context, String key, int color, int accentColor, boolean active) {
        super(context);
        this.seed = Math.abs(key.hashCode());
        this.active = active;
        this.startedAt = System.currentTimeMillis();
        this.color = color;
        this.accentColor = accentColor;
        paint.setColor(color);
        paint.setStrokeCap(Paint.Cap.ROUND);
    }

    public void setActive(boolean active) {
        if (this.active == active) return;
        this.active = active;
        startedAt = System.currentTimeMillis();
        updateAnimationState();
    }

    public void setTrackKey(String key) {
        int nextSeed = Math.abs((key == null ? "" : key).hashCode());
        if (seed != nextSeed) {
            seed = nextSeed;
            startedAt = System.currentTimeMillis();
            progress = 0.0f;
            invalidate();
        }
    }

    public void setProgress(long positionMs, long durationMs) {
        float next = durationMs <= 0L ? 0.0f
                : Math.max(0.0f, Math.min(1.0f, (float) positionMs / durationMs));
        if (Math.abs(progress - next) >= 0.002f) {
            progress = next;
            if (active) {
                invalidate();
            }
        }
    }

    public void setState(int color, int accentColor, boolean active) {
        boolean changed = this.color != color || this.accentColor != accentColor
                || this.active != active;
        if (!changed) return;
        this.color = color;
        this.accentColor = accentColor;
        if (this.active != active) {
            this.startedAt = System.currentTimeMillis();
        }
        this.active = active;
        updateAnimationState();
    }

    void setTransitionPaused(boolean paused) {
        if (transitionPaused == paused) return;
        transitionPaused = paused;
        updateAnimationState();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (active) Trace.beginSection("Voltune/Home.activeWaveformDraw");
        try {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return;

        int bars = 24;
        float gap = width / (bars * 1.55f);
        float barWidth = Math.max(3f, gap * 0.34f);
        paint.setStrokeWidth(barWidth);
        float time = (System.currentTimeMillis() - startedAt) / (active ? 180f : 520f);

        for (int i = 0; i < bars; i++) {
            boolean played = active && i <= Math.round(progress * (bars - 1));
            paint.setColor(played ? accentColor : color);
            float x = gap + i * gap * 1.48f;
            float base = 0.24f + ((seed >> (i % 12)) & 15) / 22f;
            float pulse = active ? (float) Math.sin(time + i * 0.7f) * 0.22f : 0f;
            float bar = Math.max(0.18f, Math.min(0.92f, base + pulse));
            float center = height * 0.5f;
            float half = height * bar * 0.38f;
            canvas.drawLine(x, center - half, x, center + half, paint);
        }

        scheduleNextFrame();
        } finally {
            if (active) Trace.endSection();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateAnimationState();
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelNextFrame();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        updateAnimationState();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        updateAnimationState();
    }

    private boolean shouldAnimate() {
        return active && !transitionPaused && isAttachedToWindow()
                && getWindowVisibility() == VISIBLE && isShown();
    }

    private void updateAnimationState() {
        if (!shouldAnimate()) {
            cancelNextFrame();
            invalidate();
            return;
        }
        invalidate();
        scheduleNextFrame();
    }

    private void scheduleNextFrame() {
        if (!shouldAnimate() || frameScheduled) return;
        frameScheduled = true;
        postDelayed(nextFrame, 48L);
    }

    private void cancelNextFrame() {
        if (!frameScheduled) return;
        removeCallbacks(nextFrame);
        frameScheduled = false;
    }
}
