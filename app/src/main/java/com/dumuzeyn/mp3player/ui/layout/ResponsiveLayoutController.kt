package com.dumuzeyn.mp3player.ui.layout

import android.app.Activity
import android.view.Gravity
import android.widget.FrameLayout
import kotlin.math.roundToInt

/** Supplies phone and tablet dimensions while keeping screen behavior identical. */
class ResponsiveLayoutController(private val activity: Activity) {
    fun isTablet(): Boolean {
        var smallestWidthDp = activity.resources.configuration.smallestScreenWidthDp
        if (smallestWidthDp <= 0) {
            val metrics = activity.resources.displayMetrics
            smallestWidthDp =
                (minOf(metrics.widthPixels, metrics.heightPixels) / metrics.density).roundToInt()
        }
        return isTabletWidth(smallestWidthDp)
    }

    fun pageHorizontalPadding(): Int = dp(if (isTablet()) 20 else 8)

    fun pageTopPadding(): Int = dp(if (isTablet()) 18 else 14)

    fun contentScrollbarClearance(): Int = dp(6)

    fun mainPageParams(): FrameLayout.LayoutParams = if (!isTablet()) {
        FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        )
    } else {
        FrameLayout.LayoutParams(
            boundedWidth(960, 32),
            FrameLayout.LayoutParams.MATCH_PARENT,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL,
        )
    }

    fun fullPlayerContentParams(): FrameLayout.LayoutParams = if (!isTablet()) {
        FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        )
    } else {
        FrameLayout.LayoutParams(
            boundedWidth(640, 40),
            FrameLayout.LayoutParams.MATCH_PARENT,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL,
        )
    }

    fun miniPlayerParams(): FrameLayout.LayoutParams = if (!isTablet()) {
        FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            dp(74),
            Gravity.BOTTOM,
        ).apply { setMargins(dp(10), 0, dp(10), dp(10)) }
    } else {
        FrameLayout.LayoutParams(
            boundedWidth(720, 40),
            dp(78),
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
        ).apply { setMargins(0, 0, 0, dp(18)) }
    }

    fun centeredPanelParams(requestedWidth: Int, requestedHeight: Int, availableHeight: Int = screenHeight()): FrameLayout.LayoutParams {
        var width = boundedPanelWidth(requestedWidth, screenWidth(), dp(28))
        val height = boundedPanelHeight(requestedHeight,
            if (availableHeight > 0) availableHeight else screenHeight(), dp(28))
        if (isTablet()) {
            width = boundedPanelWidth(requestedWidth.coerceAtLeast(dp(440)), screenWidth(), dp(48))
        }
        return FrameLayout.LayoutParams(width, height, Gravity.CENTER).apply {
            setMargins(dp(14), dp(14), dp(14), dp(14))
        }
    }

    fun bottomPanelParams(): FrameLayout.LayoutParams = if (!isTablet()) {
        FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            (screenHeight() * 0.78f).roundToInt(),
            Gravity.BOTTOM,
        )
    } else {
        FrameLayout.LayoutParams(
            boundedWidth(720, 40),
            minOf(dp(760), (screenHeight() * 0.88f).roundToInt()),
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
        ).apply { setMargins(0, 0, 0, dp(20)) }
    }

    fun fullPlayerCoverSizeDp(screenHeightDp: Int): Int {
        if (!isTablet()) return if (screenHeightDp < 760) 225 else 260
        if (screenHeightDp < 700) return 230
        return if (screenHeightDp < 900) 280 else 320
    }

    private fun boundedWidth(maximumDp: Int, totalMarginDp: Int): Int =
        minOf(dp(maximumDp), (screenWidth() - dp(totalMarginDp)).coerceAtLeast(dp(280)))

    private fun screenWidth(): Int = activity.resources.displayMetrics.widthPixels

    private fun screenHeight(): Int = activity.resources.displayMetrics.heightPixels

    private fun dp(value: Int): Int =
        (value * activity.resources.displayMetrics.density).roundToInt()

    companion object {
        const val TABLET_MIN_WIDTH_DP = 600

        @JvmStatic
        fun isTabletWidth(smallestWidthDp: Int): Boolean =
            smallestWidthDp >= TABLET_MIN_WIDTH_DP

        @JvmStatic
        fun boundedPanelWidth(requestedWidth: Int, screenWidth: Int, totalMargin: Int): Int =
            minOf(requestedWidth, (screenWidth - totalMargin).coerceAtLeast(1))

        @JvmStatic
        fun boundedPanelHeight(requestedHeight: Int, availableHeight: Int, totalMargin: Int): Int =
            if (requestedHeight > 0) minOf(requestedHeight, (availableHeight - totalMargin).coerceAtLeast(1))
            else requestedHeight
    }
}
