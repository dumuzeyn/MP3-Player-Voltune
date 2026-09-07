package com.dumuzeyn.mp3player

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.SeekBar
import kotlin.math.abs
import kotlin.math.roundToInt

/** Root for modal panels that can be dismissed with a deliberate horizontal swipe. */
internal class SwipeDismissFrameLayout(context: Context) : FrameLayout(context) {
    private val dismissThreshold =
        (48f * resources.displayMetrics.density).roundToInt()
    private var downX = 0f
    private var downY = 0f
    private var dismissing = false
    private var protectedGesture = false
    private var dismissAction: Runnable? = null

    init {
        isClickable = true
    }

    fun setDismissAction(action: Runnable?) {
        dismissAction = action
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                dismissing = false
                protectedGesture = isProtectedControlTouched(this, downX, downY)
                dismissTarget()?.animate()?.cancel()
                return false
            }

            MotionEvent.ACTION_MOVE -> if (!protectedGesture) {
                val dx = event.rawX - downX
                val dy = event.rawY - downY
                if (abs(dx) >= dismissThreshold && abs(dx) > abs(dy) * DIRECTION_BIAS) {
                    dismissing = true
                    return true
                }
            }
        }
        return super.onInterceptTouchEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!dismissing) return super.onTouchEvent(event)
        val dx = event.rawX - downX
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                dismissTarget()?.let { target ->
                    target.translationX = dx
                    target.alpha = (1f - abs(dx) / target.width.coerceAtLeast(1)).coerceAtLeast(0.45f)
                }
            }

            MotionEvent.ACTION_UP -> dismiss(dx)
            MotionEvent.ACTION_CANCEL -> resetPosition()
        }
        return true
    }

    private fun dismiss(dx: Float) {
        val direction = if (dx < 0f) -1f else 1f
        val action = dismissAction
        if (action == null) {
            resetPosition()
            return
        }
        val target = dismissTarget()
        if (target == null) {
            action.run()
            return
        }
        target.animate()
            .translationX(direction * width.coerceAtLeast(dismissThreshold * 2))
            .alpha(0f)
            .setDuration(120L)
            .withEndAction(action)
            .start()
    }

    private fun resetPosition() {
        dismissing = false
        dismissTarget()?.animate()
            ?.translationX(0f)
            ?.alpha(1f)
            ?.setDuration(100L)
            ?.start()
    }

    private fun dismissTarget(): View? {
        for (index in childCount - 1 downTo 0) {
            val child = getChildAt(index)
            if (child.visibility == View.VISIBLE) return child
        }
        return null
    }

    private fun isProtectedControlTouched(view: View, rawX: Float, rawY: Float): Boolean {
        if (view.visibility != View.VISIBLE || !isInside(view, rawX, rawY)) return false
        if (view is SeekBar || view is ThemeColorWheelView) return true
        if (view !is ViewGroup) return false
        for (index in view.childCount - 1 downTo 0) {
            if (isProtectedControlTouched(view.getChildAt(index), rawX, rawY)) return true
        }
        return false
    }

    private fun isInside(view: View, rawX: Float, rawY: Float): Boolean {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return rawX >= location[0] && rawX <= location[0] + view.width &&
            rawY >= location[1] && rawY <= location[1] + view.height
    }

    private companion object {
        const val DIRECTION_BIAS = 1.35f
    }
}
