package com.dumuzeyn.mp3player;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

/** Lightweight decorative particles that never participate in touch dispatch. */
final class ParticleEffectsView extends View {
    private static final int MAX_PARTICLES = 28;
    private static final long MOVE_EMIT_INTERVAL_MS = 120L;
    private final MainActivityCore host;
    private final ArrayList<Particle> particles = new ArrayList<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Random random = new Random();
    private long lastFrameTime;
    private long lastMoveEmitTime;
    private float lastTouchX;
    private float lastTouchY;
    private boolean attached;
    private boolean windowVisible;

    private final Runnable ambientEmitter = new Runnable() {
        @Override
        public void run() {
            if (!attached || !windowVisible) {
                return;
            }
            if (host.appearanceState.particlesEnabled && getWidth() > 0 && getHeight() > 0) {
                addParticle(random.nextFloat() * getWidth(), random.nextFloat() * getHeight(), false);
            }
            postDelayed(this, ambientDelayMs());
        }
    };

    ParticleEffectsView(MainActivityCore host) {
        super(host);
        this.host = host;
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }

    void observeTouch(MotionEvent event) {
        if (!host.appearanceState.particlesEnabled || getVisibility() != VISIBLE) {
            return;
        }
        int action = event.getActionMasked();
        float x = event.getX();
        float y = event.getY();
        if (action == MotionEvent.ACTION_DOWN) {
            lastTouchX = x;
            lastTouchY = y;
            lastMoveEmitTime = SystemClock.uptimeMillis();
            emitBurst(x, y, 4);
        } else if (action == MotionEvent.ACTION_MOVE) {
            long now = SystemClock.uptimeMillis();
            float dx = x - lastTouchX;
            float dy = y - lastTouchY;
            if (now - lastMoveEmitTime >= MOVE_EMIT_INTERVAL_MS && dx * dx + dy * dy >= host.dp(8) * host.dp(8)) {
                lastTouchX = x;
                lastTouchY = y;
                lastMoveEmitTime = now;
                emitBurst(x, y, 1);
            }
        }
    }

    void settingsChanged() {
        updateEmitter();
        invalidate();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        return false;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        lastFrameTime = SystemClock.uptimeMillis();
        updateEmitter();
    }

    @Override
    protected void onDetachedFromWindow() {
        attached = false;
        removeCallbacks(ambientEmitter);
        particles.clear();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        windowVisible = visibility == VISIBLE;
        updateEmitter();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!host.appearanceState.particlesEnabled) {
            particles.clear();
            return;
        }
        long now = SystemClock.uptimeMillis();
        float deltaSeconds = Math.min(0.05f, Math.max(0.0f, (now - lastFrameTime) / 1000.0f));
        lastFrameTime = now;
        Iterator<Particle> iterator = particles.iterator();
        while (iterator.hasNext()) {
            Particle particle = iterator.next();
            particle.ageMs += deltaSeconds * 1000.0f;
            if (particle.ageMs >= particle.lifeMs) {
                iterator.remove();
                continue;
            }
            particle.x += particle.velocityX * deltaSeconds;
            particle.y += particle.velocityY * deltaSeconds;
            particle.velocityY += host.dp(2) * deltaSeconds;
            particle.rotation += particle.rotationSpeed * deltaSeconds;
            drawParticle(canvas, particle);
        }
        if (!particles.isEmpty()) {
            postInvalidateOnAnimation();
        }
    }

    private void emitBurst(float x, float y, int count) {
        for (int i = 0; i < count; i++) {
            addParticle(x + signedRandom(host.dp(12)), y + signedRandom(host.dp(12)), true);
        }
    }

    private void updateEmitter() {
        removeCallbacks(ambientEmitter);
        if (attached && windowVisible && host.appearanceState.particlesEnabled) {
            postDelayed(ambientEmitter, 350L);
        } else {
            particles.clear();
        }
    }

    private void addParticle(float x, float y, boolean touchParticle) {
        if (particles.size() >= MAX_PARTICLES) {
            particles.remove(0);
        }
        Particle particle = new Particle();
        particle.x = x;
        particle.y = y;
        float sizeScale = host.appearanceState.particleSize / 100.0f;
        particle.size = host.dp(touchParticle ? 10 + random.nextInt(9) : 8 + random.nextInt(11)) * sizeScale;
        float speed = host.dp(touchParticle ? 22 + random.nextInt(38) : 8 + random.nextInt(18));
        float angle = (float) (random.nextDouble() * Math.PI * 2.0);
        particle.velocityX = (float) Math.cos(angle) * speed;
        particle.velocityY = (float) Math.sin(angle) * speed - host.dp(touchParticle ? 12 : 5);
        particle.rotation = random.nextInt(360);
        particle.rotationSpeed = signedRandom(38.0f);
        float lifetimeScale = host.appearanceState.particleLifetime / 100.0f;
        long baseLifetime = touchParticle ? 1200L + random.nextInt(801) : 2600L + random.nextInt(1801);
        particle.lifeMs = Math.round(baseLifetime * lifetimeScale);
        int primaryColor = host.appearanceState.particlePrimaryColor != 0
                ? host.appearanceState.particlePrimaryColor : host.purple;
        int secondaryColor = host.appearanceState.particleSecondaryColor != 0
                ? host.appearanceState.particleSecondaryColor : host.yellow;
        particle.color = random.nextBoolean() ? primaryColor : secondaryColor;
        particle.maxAlpha = touchParticle ? 145 + random.nextInt(56) : 55 + random.nextInt(41);
        particle.lightning = random.nextBoolean();
        particle.filled = random.nextBoolean();
        particles.add(particle);
        lastFrameTime = SystemClock.uptimeMillis();
        postInvalidateOnAnimation();
    }

    private void drawParticle(Canvas canvas, Particle particle) {
        float progress = particle.ageMs / particle.lifeMs;
        float fade = progress < 0.18f ? progress / 0.18f : 1.0f - ((progress - 0.18f) / 0.82f);
        paint.setColor(particle.color);
        paint.setAlpha(Math.max(0, Math.round(particle.maxAlpha * fade)));
        paint.setStyle(particle.filled ? Paint.Style.FILL : Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(host.dp(1), particle.size * 0.1f));
        path.reset();
        if (particle.lightning) {
            buildLightningPath(particle.size);
        } else {
            buildTrianglePath(particle.size);
        }
        canvas.save();
        canvas.translate(particle.x, particle.y);
        canvas.rotate(particle.rotation);
        canvas.drawPath(path, paint);
        canvas.restore();
    }

    private void buildTrianglePath(float size) {
        float half = size * 0.5f;
        path.moveTo(0.0f, -half);
        path.lineTo(half, half);
        path.lineTo(-half, half);
        path.close();
    }

    private void buildLightningPath(float size) {
        float half = size * 0.5f;
        path.moveTo(size * 0.08f, -half);
        path.lineTo(-half, size * 0.08f);
        path.lineTo(-size * 0.08f, size * 0.08f);
        path.lineTo(-size * 0.28f, half);
        path.lineTo(half, -size * 0.12f);
        path.lineTo(size * 0.08f, -size * 0.12f);
        path.close();
    }

    private float signedRandom(float maximum) {
        return (random.nextFloat() * 2.0f - 1.0f) * maximum;
    }

    private long ambientDelayMs() {
        int frequency = Math.max(10, Math.min(100, host.appearanceState.particleFrequency));
        long base = 2600L - frequency * 22L;
        return Math.max(350L, base + random.nextInt(301));
    }

    private static final class Particle {
        float x;
        float y;
        float velocityX;
        float velocityY;
        float rotation;
        float rotationSpeed;
        float size;
        float ageMs;
        long lifeMs;
        int color;
        int maxAlpha;
        boolean lightning;
        boolean filled;
    }
}
