package com.dumuzeyn.mp3player

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import java.util.Random
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin

/** Lightweight decorative particles that never participate in touch dispatch. */
internal class ParticleEffectsView(private val host: MainActivityCore) : View(host) {
    private val particles = ArrayList<Particle>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val random = Random()
    private var lastFrameTime = 0L
    private var lastMoveEmitTime = 0L
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var attached = false
    private var windowVisible = false
    private var uiActive = true
    private val ambientEmitter = object : Runnable {
        override fun run() {
            if (!attached || !windowVisible) return
            if (host.appearanceState.particlesEnabled && width > 0 && height > 0) {
                addParticle(random.nextFloat() * width, random.nextFloat() * height, false)
            }
            postDelayed(this, ambientDelayMs())
        }
    }

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
    }

    fun observeTouch(event: MotionEvent) {
        if (!host.appearanceState.particlesEnabled || visibility != VISIBLE) return
        val x = event.x
        val y = event.y
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = x
                lastTouchY = y
                lastMoveEmitTime = SystemClock.uptimeMillis()
                emitBurst(x, y, 4)
            }
            MotionEvent.ACTION_MOVE -> {
                val now = SystemClock.uptimeMillis()
                val dx = x - lastTouchX
                val dy = y - lastTouchY
                val minimumDistance = host.dp(8)
                if (
                    now - lastMoveEmitTime >= MOVE_EMIT_INTERVAL_MS &&
                    dx * dx + dy * dy >= minimumDistance * minimumDistance
                ) {
                    lastTouchX = x
                    lastTouchY = y
                    lastMoveEmitTime = now
                    emitBurst(x, y, 1)
                }
            }
        }
    }

    fun settingsChanged() {
        updateEmitter()
        invalidate()
    }

    fun setUiActive(active: Boolean) {
        uiActive = active
        updateEmitter()
        if (active) invalidate()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean = false

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attached = true
        lastFrameTime = SystemClock.uptimeMillis()
        updateEmitter()
    }

    override fun onDetachedFromWindow() {
        attached = false
        removeCallbacks(ambientEmitter)
        particles.clear()
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        windowVisible = visibility == VISIBLE
        updateEmitter()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!uiActive || !host.appearanceState.particlesEnabled) {
            particles.clear()
            return
        }
        val now = SystemClock.uptimeMillis()
        val deltaSeconds = min(0.05f, max(0f, (now - lastFrameTime) / 1_000f))
        lastFrameTime = now
        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val particle = iterator.next()
            particle.ageMs += deltaSeconds * 1_000f
            if (particle.ageMs >= particle.lifeMs) {
                iterator.remove()
                continue
            }
            particle.x += particle.velocityX * deltaSeconds
            particle.y += particle.velocityY * deltaSeconds
            particle.velocityY += host.dp(2) * deltaSeconds
            particle.rotation += particle.rotationSpeed * deltaSeconds
            drawParticle(canvas, particle)
        }
        if (particles.isNotEmpty()) postInvalidateOnAnimation()
    }

    private fun emitBurst(x: Float, y: Float, count: Int) {
        repeat(count) {
            addParticle(
                x + signedRandom(host.dp(12).toFloat()),
                y + signedRandom(host.dp(12).toFloat()),
                true,
            )
        }
    }

    private fun updateEmitter() {
        removeCallbacks(ambientEmitter)
        if (attached && windowVisible && uiActive && host.appearanceState.particlesEnabled) {
            postDelayed(ambientEmitter, 350L)
        } else {
            particles.clear()
        }
    }

    private fun addParticle(x: Float, y: Float, touchParticle: Boolean) {
        if (particles.size >= MAX_PARTICLES) particles.removeAt(0)
        val sizeScale = host.appearanceState.particleSize / 100f
        val speed = host.dp(
            if (touchParticle) 22 + random.nextInt(38) else 8 + random.nextInt(18),
        ).toFloat()
        val angle = (random.nextDouble() * Math.PI * 2.0).toFloat()
        val lifetimeScale = host.appearanceState.particleLifetime / 100f
        val baseLifetime = if (touchParticle) {
            1_200L + random.nextInt(801)
        } else {
            2_600L + random.nextInt(1_801)
        }
        val primaryColor = host.appearanceState.particlePrimaryColor.takeIf { it != 0 }
            ?: host.purple
        val secondaryColor = host.appearanceState.particleSecondaryColor.takeIf { it != 0 }
            ?: host.yellow
        particles.add(
            Particle(
                x = x,
                y = y,
                velocityX = cos(angle) * speed,
                velocityY = sin(angle) * speed - host.dp(if (touchParticle) 12 else 5),
                rotation = random.nextInt(360).toFloat(),
                rotationSpeed = signedRandom(38f),
                size = host.dp(
                    if (touchParticle) 10 + random.nextInt(9) else 8 + random.nextInt(11),
                ) * sizeScale,
                lifeMs = (baseLifetime * lifetimeScale).roundToLong(),
                color = if (random.nextBoolean()) primaryColor else secondaryColor,
                maxAlpha = if (touchParticle) {
                    145 + random.nextInt(56)
                } else {
                    55 + random.nextInt(41)
                },
                filled = random.nextBoolean(),
            ),
        )
        lastFrameTime = SystemClock.uptimeMillis()
        postInvalidateOnAnimation()
    }

    private fun drawParticle(canvas: Canvas, particle: Particle) {
        val progress = particle.ageMs / particle.lifeMs
        val fade = if (progress < 0.18f) {
            progress / 0.18f
        } else {
            1f - (progress - 0.18f) / 0.82f
        }
        paint.color = particle.color
        paint.alpha = max(0, (particle.maxAlpha * fade).roundToInt())
        paint.style = if (particle.filled) Paint.Style.FILL else Paint.Style.STROKE
        paint.strokeWidth = max(host.dp(1).toFloat(), particle.size * 0.1f)
        path.reset()
        buildLightningPath(particle.size)
        canvas.save()
        canvas.translate(particle.x, particle.y)
        canvas.rotate(particle.rotation)
        canvas.drawPath(path, paint)
        canvas.restore()
    }

    private fun buildLightningPath(size: Float) {
        val half = size * 0.5f
        path.moveTo(size * 0.08f, -half)
        path.lineTo(-half, size * 0.08f)
        path.lineTo(-size * 0.08f, size * 0.08f)
        path.lineTo(-size * 0.28f, half)
        path.lineTo(half, -size * 0.12f)
        path.lineTo(size * 0.08f, -size * 0.12f)
        path.close()
    }

    private fun signedRandom(maximum: Float): Float =
        (random.nextFloat() * 2f - 1f) * maximum

    private fun ambientDelayMs(): Long {
        val frequency = host.appearanceState.particleFrequency.coerceIn(10, 100)
        val base = 2_600L - frequency * 22L
        return max(350L, base + random.nextInt(301))
    }

    private data class Particle(
        var x: Float,
        var y: Float,
        var velocityX: Float,
        var velocityY: Float,
        var rotation: Float,
        val rotationSpeed: Float,
        val size: Float,
        var ageMs: Float = 0f,
        val lifeMs: Long,
        val color: Int,
        val maxAlpha: Int,
        val filled: Boolean,
    )

    companion object {
        private const val MAX_PARTICLES = 28
        private const val MOVE_EMIT_INTERVAL_MS = 120L
    }
}
