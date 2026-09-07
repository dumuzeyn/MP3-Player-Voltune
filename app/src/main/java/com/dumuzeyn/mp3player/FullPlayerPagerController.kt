package com.dumuzeyn.mp3player

import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2

/** Caches the player, lyrics, and queue pages in their visual swipe order. */
internal class FullPlayerPagerController(
    private val host: MainActivityCore,
    actions: PlaybackActions,
    playbackState: PlaybackStateProvider,
) : AutoCloseable {
    private val playerPage = FullPlayerPlaybackPage(host, actions, playbackState)
    private val lyricsPage = LyricsPageController(host, playbackState)
    private val queuePage = QueuePageController(host, playbackState) {
        showPage(FullPlayerPageOrder.LYRICS)
    }
    private val segments = arrayOfNulls<View>(3)
    private var pager: ViewPager2? = null
    private var selected = FullPlayerPageOrder.PLAYER
    private var hostVisible = true

    fun createPager(): View {
        val created = ViewPager2(host).apply {
            orientation = ViewPager2.ORIENTATION_HORIZONTAL
            offscreenPageLimit = 1
            adapter = PagesAdapter()
            registerOnPageChangeCallback(
                object : ViewPager2.OnPageChangeCallback() {
                    override fun onPageSelected(position: Int) {
                        selected = position
                        updateActivePage()
                        updateIndicator()
                    }
                },
            )
            setCurrentItem(FullPlayerPageOrder.PLAYER, false)
        }
        pager = created
        return created
    }

    fun createIndicator(): View {
        val row = LinearLayout(host).apply { gravity = Gravity.CENTER }
        for (position in segments.indices) {
            val segment = View(host)
            segments[position] = segment
            val target = FrameLayout(host).apply {
                isClickable = true
                isFocusable = true
                contentDescription = pageDescription(position)
                setOnClickListener { showPage(position) }
                addView(
                    segment,
                    FrameLayout.LayoutParams(host.dp(22), host.dp(4), Gravity.CENTER),
                )
            }
            row.addView(target, LinearLayout.LayoutParams(host.dp(48), host.dp(18)))
        }
        updateIndicator()
        return row
    }

    fun refresh() {
        playerPage.refresh(true)
        when (selected) {
            FullPlayerPageOrder.LYRICS -> lyricsPage.refresh()
            FullPlayerPageOrder.QUEUE -> queuePage.refresh()
        }
    }

    fun setHostVisible(visible: Boolean) {
        hostVisible = visible
        updateActivePage()
    }

    fun selectedPage(): Int = selected

    private fun updateActivePage() {
        playerPage.setActive(hostVisible && selected == FullPlayerPageOrder.PLAYER)
        lyricsPage.setActive(hostVisible && selected == FullPlayerPageOrder.LYRICS)
        queuePage.setActive(hostVisible && selected == FullPlayerPageOrder.QUEUE)
    }

    private fun updateIndicator() {
        segments.forEachIndexed { position, segment ->
            if (segment == null) return@forEachIndexed
            segment.background = GradientDrawable().apply {
                cornerRadius = host.dp(3).toFloat()
                setColor(if (position == selected) host.purple else host.cardStroke)
            }
        }
    }

    private fun pageDescription(position: Int): String = when (position) {
        FullPlayerPageOrder.PLAYER -> host.tr("Open player", "Открыть плеер")
        FullPlayerPageOrder.LYRICS -> host.tr("Open lyrics", "Открыть текст")
        else -> host.tr("Open queue", "Открыть очередь")
    }

    private fun showPage(position: Int) {
        pager?.setCurrentItem(position, host.appearanceState.animations)
    }

    override fun close() {
        playerPage.close()
        lyricsPage.close()
        queuePage.close()
        pager?.adapter = null
        pager = null
    }

    private inner class PagesAdapter : RecyclerView.Adapter<PageHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder {
            val container = FrameLayout(host).apply {
                layoutParams = RecyclerView.LayoutParams(-1, -1)
            }
            return PageHolder(container)
        }

        override fun onBindViewHolder(holder: PageHolder, position: Int) {
            holder.container.removeAllViews()
            val page = when (position) {
                FullPlayerPageOrder.PLAYER -> playerPage.createView()
                FullPlayerPageOrder.LYRICS -> lyricsPage.createView()
                else -> queuePage.createView()
            }
            (page.parent as? ViewGroup)?.removeView(page)
            holder.container.addView(page, FrameLayout.LayoutParams(-1, -1))
        }

        override fun getItemCount(): Int = 3
    }

    class PageHolder(val container: FrameLayout) : RecyclerView.ViewHolder(container)
}
