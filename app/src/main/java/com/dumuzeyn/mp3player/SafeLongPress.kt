package com.dumuzeyn.mp3player

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration

/** Deliberate long press that is cancelled as soon as a scroll or swipe starts. */
internal object SafeLongPress {
    const val HOLD_MS = 1_100L

    fun bind(view: View, action: () -> Unit) {
        val slop = ViewConfiguration.get(view.context).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var downAt = 0L
        var active = false
        var performed = false
        val trigger = Runnable {
            if (!active || !view.isAttachedToWindow) return@Runnable
            active = false
            performed = true
            view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            action()
        }
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.removeCallbacks(trigger)
                    downX = event.x
                    downY = event.y
                    downAt = SystemClock.uptimeMillis()
                    active = true
                    performed = false
                    view.postDelayed(trigger, HOLD_MS)
                }
                MotionEvent.ACTION_MOVE -> if (
                    kotlin.math.abs(event.x - downX) > slop ||
                    kotlin.math.abs(event.y - downY) > slop
                ) {
                    active = false
                    view.removeCallbacks(trigger)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    active = false
                    view.removeCallbacks(trigger)
                    val consume = performed
                    performed = false
                    downAt = 0L
                    return@setOnTouchListener consume
                }
            }
            false
        }
        view.setOnLongClickListener {
            if (downAt != 0L) {
                if (!active || SystemClock.uptimeMillis() - downAt < HOLD_MS) {
                    return@setOnLongClickListener false
                }
            }
            action()
            true
        }
    }
}
