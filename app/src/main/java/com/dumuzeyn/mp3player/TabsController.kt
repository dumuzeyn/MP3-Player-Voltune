package com.dumuzeyn.mp3player

import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal class TabsController(private val host: MainActivityCore) {
    private var scrollAnimator: ValueAnimator? = null
    private var tabTrack: FrameLayout? = null
    private var indicator: View? = null
    private var transitionFrom: Button? = null
    private var transitionTo: Button? = null
    private var transitionFromX = 0f
    private var transitionToX = 0f
    private var infiniteLoopAttached = false
    private var lastViewportWidth = 0
    private var editorModeBounds: EditorModeBoundsView? = null

    fun buildTabs(page: LinearLayout) {
        cancelScrollAnimation()
        infiniteLoopAttached = false
        lastViewportWidth = 0
        val container = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL }
        container.addView(host.uiFactory.lineView(), LinearLayout.LayoutParams(-1, 1))

        val scrollView = HorizontalScrollView(host).apply {
            isHorizontalScrollBarEnabled = false
        }
        host.tabsScroll = scrollView
        val track = FrameLayout(host)
        tabTrack = track
        host.tabRow = LinearLayout(host).apply { orientation = LinearLayout.HORIZONTAL }
        indicator = View(host).apply {
            background = GradientDrawable().apply {
                setColor(host.purple)
                cornerRadius = host.dp(14).toFloat()
            }
        }
        track.addView(indicator, FrameLayout.LayoutParams(host.dp(132), host.dp(48)))
        track.addView(host.tabRow, FrameLayout.LayoutParams(-2, host.dp(48)))
        scrollView.addView(track, FrameLayout.LayoutParams(-2, host.dp(48)))
        container.addView(scrollView, LinearLayout.LayoutParams(-1, host.dp(48)))
        container.addView(host.uiFactory.lineView(), LinearLayout.LayoutParams(-1, 1))
        val tabsFrame = FrameLayout(host)
        tabsFrame.addView(container, FrameLayout.LayoutParams(-1, host.dp(50)))
        editorModeBounds = EditorModeBoundsView(host).apply {
            id = R.id.editor_mode_bounds
            visibility = View.GONE
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        tabsFrame.addView(editorModeBounds, FrameLayout.LayoutParams(-1, host.dp(50)))
        page.addView(tabsFrame, LinearLayout.LayoutParams(-1, host.dp(50)))
        scrollView.setOnTouchListener { _, _ -> host.isEditorNavigationLocked() }

        addTabButtons()
        refreshEditorModeIndicator()
        scrollView.addOnLayoutChangeListener { _, left, _, right, _, _, _, _, _ ->
            if (right <= left || host.tabRow.width <= 0) return@addOnLayoutChangeListener
            val viewportWidth = right - left
            if (viewportWidth == lastViewportWidth && infiniteLoopAttached) {
                return@addOnLayoutChangeListener
            }
            lastViewportWidth = viewportWidth
            scrollView.post {
                if (host.tabsScroll !== scrollView) return@post
                if (!infiniteLoopAttached) {
                    infiniteLoopAttached = true
                    attachInfiniteScrollLoop()
                } else {
                    scrollToActiveNow(false, host.navigationState.tabIndex)
                    positionIndicatorToActive()
                }
            }
        }
    }

    fun refreshTabs() {
        val row = host.tabRow ?: return
        for (index in 0 until row.childCount) {
            val child = row.getChildAt(index)
            val tabIndex = child.tag as? Int
            if (child is Button && tabIndex != null) styleTab(child, tabIndex)
        }
        refreshEditorModeIndicator()
        if (transitionFrom == null) positionIndicatorToActive()
    }

    fun refreshEditorModeIndicator() {
        val locked = host.isEditorNavigationLocked()
        editorModeBounds?.visibility = if (locked) View.VISIBLE else View.GONE
        editorModeBounds?.invalidate()
        val row = host.tabRow ?: return
        for (index in 0 until row.childCount) {
            val button = row.getChildAt(index) as? Button ?: continue
            val tabIndex = button.tag as? Int ?: continue
            button.isEnabled = !locked || tabIndex == LibraryTabs.EDITOR
            button.alpha = if (button.isEnabled) 1f else 0.42f
        }
    }

    fun rebuildTabs() {
        cancelScrollAnimation()
        transitionFrom = null
        transitionTo = null
        host.tabRow.removeAllViews()
        addTabButtons()
        host.tabRow.requestLayout()
        host.tabsScroll.post {
            attachInfiniteScrollLoop()
            scrollToActiveNow(false, host.navigationState.tabIndex)
            positionIndicatorToActive()
        }
    }

    fun beginTransition(fromIndex: Int, targetIndex: Int, direction: Int) {
        transitionFrom = findNearestButton(fromIndex)
        transitionTo = findDirectionalButton(targetIndex, transitionFrom, direction)
        val from = transitionFrom
        val to = transitionTo
        val activeIndicator = indicator
        if (from == null || to == null || activeIndicator == null) return
        transitionFromX = from.left.toFloat()
        transitionToX = to.left.toFloat()
        activeIndicator.translationX = transitionFromX
        setTransitionProgress(0f)
    }

    fun setTransitionProgress(progress: Float) {
        val bounded = progress.coerceIn(0f, 1f)
        val from = transitionFrom
        val to = transitionTo
        if (indicator != null && from != null && to != null) {
            indicator?.translationX = transitionFromX + (transitionToX - transitionFromX) * bounded
            from.setTextColor(ThemeManager.mixColor(host.secondaryText, Color.WHITE, bounded))
            to.setTextColor(ThemeManager.mixColor(Color.WHITE, host.secondaryText, bounded))
        }
    }

    fun finishTransition(targetIndex: Int) {
        transitionFrom = null
        transitionTo = null
        refreshTabs()
        scrollToActive(true, targetIndex)
        host.tabsScroll?.post(::positionIndicatorToActive)
    }

    fun cancelTransition() {
        setTransitionProgress(0f)
        transitionFrom = null
        transitionTo = null
        refreshTabs()
    }

    fun directionTo(targetIndex: Int): Int {
        return host.menuConfigurationController.direction(
            host.navigationState.tabIndex,
            targetIndex,
        )
    }

    fun isInsideTabs(event: MotionEvent): Boolean {
        val scroll = host.tabsScroll ?: return false
        val location = IntArray(2)
        scroll.getLocationOnScreen(location)
        return event.rawX >= location[0] && event.rawX <= location[0] + scroll.width &&
            event.rawY >= location[1] && event.rawY <= location[1] + scroll.height
    }

    fun scrollToActive(smooth: Boolean, targetIndex: Int) {
        val scroll = host.tabsScroll
        val row = host.tabRow
        val tabs = host.tabs
        if (scroll == null || row == null || tabs == null || tabs.isEmpty()) return
        scroll.post { scrollToActiveNow(smooth, targetIndex) }
    }

    fun cancelScrollAnimation() {
        scrollAnimator?.cancel()
    }

    private fun addTabButtons() {
        val visibleTabs = host.menuConfigurationController.visibleTabs()
        for (cycle in 0 until MainActivityCore.TAB_CYCLES) {
            for (tabId in visibleTabs) {
                val button = host.uiFactory.button(host.tabs[tabId]).apply {
                    tag = tabId
                }
                styleTab(button, tabId)
                button.setOnClickListener {
                    host.switchTabAnimated(tabId, directionTo(tabId))
                }
                host.tabRow.addView(
                    button,
                    LinearLayout.LayoutParams(host.dp(132), host.dp(48)),
                )
            }
        }
    }

    private fun attachInfiniteScrollLoop() {
        val cycleWidth = max(1, host.tabRow.width / MainActivityCore.TAB_CYCLES)
        scrollToActiveNow(false, host.navigationState.tabIndex)
        positionIndicatorToActive()
        host.tabsScroll.setOnScrollChangeListener { _, scrollX, _, _, _ ->
            keepScrollInsideMiddleCycles(cycleWidth, scrollX)
            if (transitionFrom == null) positionIndicatorToActive()
        }
    }

    private fun keepScrollInsideMiddleCycles(cycleWidth: Int, scrollX: Int) {
        val middleCycle = MainActivityCore.TAB_CYCLES / 2
        val leftBoundary = cycleWidth * max(0, middleCycle - 1)
        val rightBoundary = cycleWidth * min(MainActivityCore.TAB_CYCLES - 1, middleCycle + 1)
        when {
            scrollX < leftBoundary -> host.tabsScroll.scrollTo(scrollX + cycleWidth, 0)
            scrollX > rightBoundary -> host.tabsScroll.scrollTo(scrollX - cycleWidth, 0)
        }
    }

    private fun styleTab(button: Button, index: Int) {
        button.textSize = 15f
        button.setSingleLine(false)
        button.maxLines = 2
        button.ellipsize = null
        androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
            button, 10, 15, 1, android.util.TypedValue.COMPLEX_UNIT_SP,
        )
        button.gravity = Gravity.CENTER
        button.includeFontPadding = false
        button.setPadding(host.dp(14), 0, host.dp(14), 0)
        button.translationY = if (index == LibraryTabs.SOUND) -host.dp(7).toFloat() else 0f
        (button as? OutlinedButton)?.setTextOutline(false, Color.TRANSPARENT, 1f)
        button.setBackgroundColor(Color.TRANSPARENT)
        button.setTextColor(
            if (index == host.navigationState.tabIndex) Color.WHITE else host.secondaryText,
        )
        val locked = host.isEditorNavigationLocked()
        button.isEnabled = !locked || index == LibraryTabs.EDITOR
        button.alpha = if (button.isEnabled) 1f else 0.42f
    }

    private fun positionIndicatorToActive() {
        val active = findNearestButton(host.navigationState.tabIndex)
        if (active != null) indicator?.translationX = active.left.toFloat()
    }

    private fun findNearestButton(tabIndex: Int): Button? {
        val scroll = host.tabsScroll ?: return null
        val row = host.tabRow ?: return null
        val center = scroll.scrollX + scroll.width / 2
        var closest: Button? = null
        var distance = Int.MAX_VALUE
        for (index in 0 until row.childCount) {
            val child = row.getChildAt(index)
            if (child !is Button || child.tag as? Int != tabIndex) continue
            val candidateDistance = abs(child.left + child.width / 2 - center)
            if (candidateDistance < distance) {
                distance = candidateDistance
                closest = child
            }
        }
        return closest
    }

    private fun findDirectionalButton(tabIndex: Int, from: Button?, direction: Int): Button? {
        if (from == null) return findNearestButton(tabIndex)
        val row = host.tabRow
        var closest: Button? = null
        var distance = Int.MAX_VALUE
        for (index in 0 until row.childCount) {
            val child = row.getChildAt(index)
            if (child !is Button || child.tag as? Int != tabIndex) continue
            val delta = child.left - from.left
            if (direction > 0 && delta <= 0 || direction < 0 && delta >= 0) continue
            if (abs(delta) < distance) {
                distance = abs(delta)
                closest = child
            }
        }
        return closest ?: findNearestButton(tabIndex)
    }

    private fun scrollToActiveNow(smooth: Boolean, targetIndex: Int) {
        val visibleTarget = host.menuConfigurationController.visibleOrFirst(targetIndex)
        var left = if (smooth) findSmoothTargetLeft(visibleTarget) else -1
        if (left < 0) left = centeredCycleTargetLeft(visibleTarget)
        if (left < 0) return
        if (smooth) animateScrollTo(left) else host.tabsScroll.scrollTo(left, 0)
    }

    private fun findSmoothTargetLeft(targetIndex: Int): Int {
        val scrollCenter = host.tabsScroll.scrollX + host.tabsScroll.width / 2
        var closestDistance = Int.MAX_VALUE
        var left = -1
        for (index in 0 until host.tabRow.childCount) {
            val child = host.tabRow.getChildAt(index)
            if (child.tag as? Int != targetIndex) continue
            val childCenter = child.left + child.width / 2
            val childLeft = child.left - max(0, (host.tabsScroll.width - child.width) / 2)
            if (
                host.navigationState.preferredTabDirection > 0 && childCenter < scrollCenter ||
                host.navigationState.preferredTabDirection < 0 && childCenter > scrollCenter
            ) {
                continue
            }
            val distance = abs(childCenter - scrollCenter)
            if (distance < closestDistance) {
                closestDistance = distance
                left = childLeft
            }
        }
        return left
    }

    private fun centeredCycleTargetLeft(targetIndex: Int): Int {
        val visibleTabs = host.menuConfigurationController.visibleTabs()
        val targetPosition = visibleTabs.indexOf(targetIndex)
        if (targetPosition < 0) return -1
        val childIndex = visibleTabs.size * (MainActivityCore.TAB_CYCLES / 2) + targetPosition
        if (childIndex >= host.tabRow.childCount) return -1
        val child = host.tabRow.getChildAt(childIndex)
        return child.left - max(0, (host.tabsScroll.width - child.width) / 2)
    }

    private fun animateScrollTo(left: Int) {
        val scroll = host.tabsScroll ?: return
        cancelScrollAnimation()
        val scrollX = scroll.scrollX
        if (abs(left - scrollX) < 2 || !host.appearanceState.animations) {
            scroll.scrollTo(left, 0)
            return
        }
        scrollAnimator = ValueAnimator.ofInt(scrollX, left).apply {
            duration = 96L
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                host.tabsScroll?.scrollTo(animator.animatedValue as Int, 0)
            }
            start()
        }
    }
}
