package com.dumuzeyn.mp3player

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout

/** Creates the shared current-track marker used by every library surface. */
object NowPlayingIndicator {
    @JvmStatic
    fun create(host: MainActivityCore): View = View(host).also { style(it, host.yellow) }

    @JvmStatic
    fun style(indicator: View, color: Int) {
        indicator.background = GradientDrawable().apply {
            setColor(color)
            cornerRadius = indicator.resources.getDimension(R.dimen.now_playing_indicator_width)
        }
    }

    @JvmStatic
    fun layoutParams(context: Context): FrameLayout.LayoutParams {
        val width = size(context, R.dimen.now_playing_indicator_width)
        val height = size(context, R.dimen.now_playing_indicator_height)
        val inset = size(context, R.dimen.now_playing_indicator_inset)
        return FrameLayout.LayoutParams(width, height).apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setMargins(inset, 0, 0, 0)
        }
    }

    private fun size(context: Context, resource: Int): Int =
        context.resources.getDimensionPixelSize(resource)
}
