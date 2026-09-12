package com.dumuzeyn.mp3player

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.os.Trace
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal class SwipeController(private val host: MainActivityCore) {
    private var startX = 0f
    private var startY = 0f
    private var currentOffset = 0f
    private var startedOnTabs = false
    private var startedOnMiniPlayer = false
    private var consuming = false
    private var previewScroll: ScrollView? = null
    private var previewList: LinearLayout? = null
    private var currentSurface: View? = null
    private var previewSurface: View? = null
    private var previewUsesSongsSurface = false
    private var previewState: MainRenderer.PreviewState? = null
    private var targetIndex = -1
    private var direction = 0
    private var width = 0
    private var transitionDistance = 0
    private var transitionAnimator: ValueAnimator? = null
    private var recordHistory = true
    private var targetSearch = ""

    fun handle(event: MotionEvent): Boolean {
        val tabs = host.tabs
        if (tabs == null || tabs.isEmpty() || host.navigationState.tabAnimating && !consuming) {
            return false
        }
        if ((host.overlayHost?.childCount ?: 0) > 0) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startedOnTabs = host.tabsController.isInsideTabs(event)
                startedOnMiniPlayer = host.playerUiController.isInsideMiniPlayer(event)
                consuming = false
                startX = event.x
                startY = event.y
                currentOffset = 0f
                host.tabsController.cancelScrollAnimation()
                return false
            }
            MotionEvent.ACTION_MOVE -> return handleMove(event)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> return handleEnd(event)
            else -> return false
        }
    }

    fun animateToTab(target: Int, requestedDirection: Int, saveHistory: Boolean, search: String?) {
        val tabs = host.tabs
        if (tabs == null || target !in tabs.indices) return
        if (target == host.navigationState.tabIndex) {
            host.navigationState.search = search.orEmpty()
            host.render()
            return
        }
        if (
            !host.appearanceState.animations ||
            host.contentHost == null ||
            host.contentHost.width <= 0
        ) {
            host.tabTransitionCoordinator.complete(
                TabTransitionRequest.withoutPreview(
                    target,
                    requestedDirection,
                    saveHistory,
                    search,
                ),
            )
            return
        }
        prepareTransition(target, requestedDirection, saveHistory, search)
        updateOffset(0f)
        animateOffset(0f, -direction * transitionDistance.toFloat(), true)
    }

    fun cancelForBackground() {
        transitionAnimator?.cancel()
        if (host.navigationState.tabAnimating) finishCancelledTransition()
    }

    private fun handleMove(event: MotionEvent): Boolean {
        if (startedOnTabs || startedOnMiniPlayer) return false
        val deltaX = event.x - startX
        val deltaY = event.y - startY
        if (!consuming && abs(deltaX) > host.dp(SWIPE_START_DP) && abs(deltaX) > abs(deltaY)) {
            consuming = true
            host.cancelActiveContentTouch(event)
            host.root?.parent?.requestDisallowInterceptTouchEvent(true)
            if (host.appearanceState.animations) {
                prepareAdjacentTransition(if (deltaX < 0f) 1 else -1)
            }
        }
        if (!consuming) return false
        if (host.appearanceState.animations) {
            val requestedDirection = if (deltaX < 0f) 1 else -1
            if (requestedDirection != direction && abs(deltaX) > host.dp(36)) {
                cleanupPreview()
                prepareAdjacentTransition(requestedDirection)
            }
            updateOffset(clampOffset(deltaX))
        }
        return true
    }

    private fun handleEnd(event: MotionEvent): Boolean {
        if (startedOnTabs || startedOnMiniPlayer) {
            startedOnTabs = false
            startedOnMiniPlayer = false
            return false
        }
        if (!consuming) return false
        consuming = false
        val deltaX = event.x - startX
        val deltaY = event.y - startY
        val commit = event.actionMasked == MotionEvent.ACTION_UP &&
            abs(deltaX) > host.dp(SWIPE_COMMIT_DP) && abs(deltaX) > abs(deltaY)
        if (!host.appearanceState.animations) {
            if (commit) {
                val swipeDirection = if (deltaX < 0f) 1 else -1
                host.tabTransitionCoordinator.complete(
                    TabTransitionRequest.withoutPreview(
                        adjacentIndex(swipeDirection),
                        swipeDirection,
                        true,
                        "",
                    ),
                )
            }
            return true
        }
        if (commit) {
            animateOffset(currentOffset, -direction * transitionDistance.toFloat(), true)
        } else {
            animateOffset(currentOffset, 0f, false)
        }
        return true
    }

    private fun prepareAdjacentTransition(requestedDirection: Int) {
        prepareTransition(adjacentIndex(requestedDirection), requestedDirection, true, "")
    }

    private fun prepareTransition(
        target: Int,
        requestedDirection: Int,
        saveHistory: Boolean,
        search: String?,
    ) {
        Trace.beginSection("Voltune/Home.prepareTransition")
        try {
            cleanupPreview()
            host.songRows.setWaveformsTransitionPaused(true)
            host.previewSongRows.setWaveformsTransitionPaused(true)
            host.mainRenderer.setHomeTransitionPaused(true)
            direction = if (requestedDirection >= 0) 1 else -1
            targetIndex = target
            recordHistory = saveHistory
            targetSearch = search.orEmpty()
            width = max(1, host.contentHost.width)
            transitionDistance = width + host.dp(12)
            currentSurface = if (
                host.navigationState.tabIndex == LibraryTabs.SONGS && host.songsView != null
            ) {
                host.songsView
            } else {
                host.contentScroll
            }
            host.navigationState.tabAnimating = true
            host.tabsController.beginTransition(
                host.navigationState.tabIndex,
                targetIndex,
                direction,
            )
            val songsView = host.songsView
            if (targetIndex == LibraryTabs.SONGS && songsView != null) {
                previewUsesSongsSurface = true
                songsView.prepareForTransition(host.libraryState.tracks, targetSearch)
                previewSurface = songsView
                previewSurface?.translationX = direction * transitionDistance.toFloat()
                prepareSurfaceLayers()
                return
            }
            previewUsesSongsSurface = false
            val list = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL }
            val scroll = ScrollView(host).apply {
                addView(list, FrameLayout.LayoutParams(-1, -2))
                translationX = direction * transitionDistance.toFloat()
            }
            previewList = list
            previewScroll = scroll
            host.contentHost.addView(scroll, 0, FrameLayout.LayoutParams(-1, -1))
            previewSurface = scroll
            val state = host.renderTabPreview(list, targetIndex, targetSearch)
            previewState = state
            host.previewSongRows.setWaveformsTransitionPaused(true)
            prepareSurfaceLayers()
            scroll.scrollTo(0, state.scrollY)
        } finally {
            Trace.endSection()
        }
    }

    private fun updateOffset(offset: Float) {
        currentOffset = offset
        currentSurface?.translationX = offset
        previewSurface?.translationX = offset + direction * transitionDistance
        host.tabsController.setTransitionProgress(
            min(1f, abs(offset) / max(1f, transitionDistance.toFloat())),
        )
    }

    private fun animateOffset(from: Float, to: Float, commit: Boolean) {
        transitionAnimator?.cancel()
        val distance = abs(to - from)
        val duration = max(
            90L,
            min(280L, (260L * distance / max(1, width)).toLong()),
        )
        transitionAnimator = ValueAnimator.ofFloat(from, to).apply {
            this.duration = duration
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator -> updateOffset(animator.animatedValue as Float) }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false

                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (cancelled) return
                    if (commit) finishCommittedTransition() else finishCancelledTransition()
                }
            })
            start()
        }
    }

    private fun finishCommittedTransition() {
        val completedTarget = targetIndex
        val completedDirection = direction
        val shouldRecord = recordHistory
        val completedSearch = targetSearch
        if (previewUsesSongsSurface && previewSurface != null) {
            previewSurface?.translationX = 0f
            previewSurface = null
            previewUsesSongsSurface = false
            previewScroll = null
            previewList = null
            previewState = null
            targetIndex = -1
            resetCurrentContent()
            host.tabTransitionCoordinator.complete(
                TabTransitionRequest.withoutPreview(
                    completedTarget,
                    completedDirection,
                    shouldRecord,
                    completedSearch,
                ),
            )
            resumeWaveforms()
            return
        }
        val committedScroll = previewScroll
        val committedList = previewList
        val committedState = previewState
        if (committedScroll != null && committedList != null && committedState != null) {
            previewScroll = null
            previewList = null
            previewState = null
            previewSurface = null
            targetIndex = -1
            resetCurrentContent()
            host.tabTransitionCoordinator.completeWithPreview(
                TabTransitionRequest(
                    completedTarget,
                    completedDirection,
                    shouldRecord,
                    completedSearch,
                    committedScroll,
                    committedList,
                    committedState,
                ),
            )
            resumeWaveforms()
            return
        }
        cleanupPreview()
        resetCurrentContent()
        host.tabTransitionCoordinator.complete(
            TabTransitionRequest.withoutPreview(
                completedTarget,
                completedDirection,
                shouldRecord,
                completedSearch,
            ),
        )
        resumeWaveforms()
    }

    private fun finishCancelledTransition() {
        cleanupPreview()
        resetCurrentContent()
        host.navigationState.tabAnimating = false
        host.tabsController.cancelTransition()
        resumeWaveforms()
    }

    private fun cleanupPreview() {
        previewSurface?.setLayerType(View.LAYER_TYPE_NONE, null)
        previewScroll?.let { scroll ->
            if (scroll.parent === host.contentHost) host.contentHost.removeView(scroll)
        }
        val songsView = host.songsView
        if (
            previewUsesSongsSurface && songsView != null &&
            host.navigationState.tabIndex != LibraryTabs.HOME
        ) {
            songsView.hide()
        }
        previewScroll = null
        previewList = null
        previewState = null
        previewSurface = null
        previewUsesSongsSurface = false
        host.discardTabPreview()
        targetIndex = -1
    }

    private fun resetCurrentContent() {
        currentOffset = 0f
        currentSurface?.let {
            it.translationX = 0f
            it.alpha = 1f
            it.setLayerType(View.LAYER_TYPE_NONE, null)
        }
        currentSurface = null
        host.contentScroll?.let {
            it.translationX = 0f
            it.alpha = 1f
        }
    }

    private fun prepareSurfaceLayers() {
        currentSurface?.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        previewSurface?.setLayerType(View.LAYER_TYPE_HARDWARE, null)
    }

    private fun resumeWaveforms() {
        host.songRows.setWaveformsTransitionPaused(false)
        host.previewSongRows.setWaveformsTransitionPaused(false)
        host.mainRenderer.setHomeTransitionPaused(false)
    }

    private fun clampOffset(value: Float): Float =
        value.coerceIn(-transitionDistance * 0.96f, transitionDistance * 0.96f)

    private fun adjacentIndex(requestedDirection: Int): Int {
        val count = host.tabs.size
        return if (requestedDirection > 0) {
            (host.navigationState.tabIndex + 1) % count
        } else {
            (host.navigationState.tabIndex - 1 + count) % count
        }
    }

    private companion object {
        const val SWIPE_START_DP = 18
        const val SWIPE_COMMIT_DP = 52
    }
}
