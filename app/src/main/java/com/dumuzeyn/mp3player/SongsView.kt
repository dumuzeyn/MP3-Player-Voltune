package com.dumuzeyn.mp3player

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.view.Gravity
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.roundToInt

/** Persistent, recyclable surface for the main Songs tab. */
internal class SongsView(private val host: MainActivityCore) : FrameLayout(host), AutoCloseable {
    private val recyclerView = RecyclerView(host)
    private val songAdapter = SongAdapter(host)
    private val headerAdapter = HeaderAdapter()
    private val emptyAdapter = EmptyAdapter()
    private val alphabetRail = AlphabetRailView(host)
    private val searchOwner = "songs-" + Integer.toHexString(System.identityHashCode(this))
    private val progressTicker = object : Runnable {
        override fun run() {
            if (!hostVisible || visibility != View.VISIBLE) return
            if (host.isPlaybackPlaying()) {
                val currentIndex = host.currentTrackIndex()
                val current = host.libraryState.tracks.getOrNull(currentIndex)
                songAdapter.refreshPosition(
                    host.playbackPosition().toLong(),
                    current?.let(host::playbackDurationFor)?.toLong() ?: 0L,
                )
                host.uiHandler.postDelayed(this, PROGRESS_INTERVAL_MS)
            }
        }
    }

    private var sourceSnapshot = ArrayList<Track>()
    private var query = ""
    private var closed = false
    private var hostVisible = true

    init {
        recyclerView.layoutManager = LinearLayoutManager(host).apply {
            recycleChildrenOnDetach = true
        }
        recyclerView.setItemViewCacheSize(6)
        recyclerView.clipToPadding = false
        recyclerView.setPadding(0, 0, host.dp(24), host.dp(88))
        recyclerView.itemAnimator = null
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(view: RecyclerView, dx: Int, dy: Int) {
                syncAlphabetToList()
            }
        })
        val config = ConcatAdapter.Config.Builder()
            .setStableIdMode(ConcatAdapter.Config.StableIdMode.ISOLATED_STABLE_IDS)
            .build()
        recyclerView.adapter = ConcatAdapter(config, headerAdapter, songAdapter, emptyAdapter)
        addView(recyclerView, LayoutParams(-1, -1))
        addView(alphabetRail, LayoutParams(host.dp(30), -1, Gravity.END).apply {
            marginEnd = host.dp(4)
        })
        visibility = View.GONE
    }

    fun show(source: List<Track>, searchQuery: String) {
        visibility = View.VISIBLE
        submit(source, searchQuery, false)
        headerAdapter.refresh()
        updateProgressTicker()
    }

    fun prepareForTransition(source: List<Track>, searchQuery: String) {
        visibility = View.VISIBLE
        submit(source, searchQuery, false)
        headerAdapter.refresh()
    }

    fun hide() {
        translationX = 0f
        visibility = View.GONE
        host.uiHandler.removeCallbacks(progressTicker)
    }

    fun refreshPlayback() {
        songAdapter.refreshPlayback()
        headerAdapter.refresh()
        updateProgressTicker()
    }

    fun refreshMetadata(track: Track) {
        songAdapter.replaceTrack(track)
    }

    fun setHostVisible(visible: Boolean) {
        hostVisible = visible
        updateProgressTicker()
    }

    fun refreshFilteredSource(source: List<Track>) {
        submit(source, query, true)
    }

    fun visibleTracks(): List<Track> = songAdapter.visibleTracks()

    fun recyclerView(): RecyclerView = recyclerView

    fun query(): String = query

    override fun close() {
        if (closed) return
        closed = true
        host.uiHandler.removeCallbacks(progressTicker)
        host.trackSearchController.cancel(searchOwner)
        recyclerView.adapter = null
    }

    private fun submit(source: List<Track>, searchQuery: String, force: Boolean) {
        if (closed) return
        val normalizedQuery = Track.normalizeSearchText(searchQuery)
        if (!force && normalizedQuery == query && sameSnapshot(sourceSnapshot, source)) return
        query = normalizedQuery
        sourceSnapshot = ArrayList(source)
        host.trackSearchController.filter(searchOwner, sourceSnapshot, normalizedQuery) { filtered ->
            if (closed) return@filter
            songAdapter.submitList(ArrayList(filtered)) {
                emptyAdapter.setEmpty(filtered.isEmpty())
                headerAdapter.refresh()
                updateAlphabet(filtered)
            }
        }
    }

    private fun updateAlphabet(tracks: List<Track>) {
        val entries = AlphabetIndex.build(tracks.map(Track::title))
        alphabetRail.configure(
            entries,
            tracks.size,
            host.primaryText,
        ) { progress ->
            recyclerView.stopScroll()
            scrollToProgress(progress)
        }
        syncAlphabetToList()
    }

    private fun syncAlphabetToList() {
        val count = songAdapter.itemCount
        if (count == 0) return
        val maximum = (recyclerView.computeVerticalScrollRange() -
            recyclerView.computeVerticalScrollExtent()).coerceAtLeast(0)
        val offset = recyclerView.computeVerticalScrollOffset().coerceIn(0, maximum)
        val progress = if (maximum == 0) 0f else offset.toFloat() / maximum
        val position = (progress * (count - 1)).roundToInt().coerceIn(0, count - 1)
        alphabetRail.syncToList(progress, position)
    }

    private fun scrollToProgress(progress: Float) {
        val count = songAdapter.itemCount
        if (count == 0) return
        val manager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        when {
            progress <= 0f -> manager.scrollToPositionWithOffset(0, 0)
            progress >= 1f -> manager.scrollToPositionWithOffset(
                headerAdapter.itemCount + count - 1,
                recyclerView.height - recyclerView.paddingBottom - host.dp(66),
            )
            else -> {
                val maximum = (recyclerView.computeVerticalScrollRange() -
                    recyclerView.computeVerticalScrollExtent()).coerceAtLeast(0)
                val target = (progress * maximum).roundToInt()
                recyclerView.scrollBy(0, target - recyclerView.computeVerticalScrollOffset())
            }
        }
        recyclerView.post(::syncAlphabetToList)
    }

    private fun updateProgressTicker() {
        host.uiHandler.removeCallbacks(progressTicker)
        if (hostVisible && visibility == View.VISIBLE && host.isPlaybackPlaying()) {
            host.uiHandler.post(progressTicker)
        }
    }

    private inner class HeaderAdapter : RecyclerView.Adapter<HeaderHolder>() {
        init {
            setHasStableIds(true)
        }

        override fun getItemId(position: Int): Long = HEADER_ID

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HeaderHolder =
            HeaderHolder(host.headerController.createSongsSectionHeader())

        override fun onBindViewHolder(holder: HeaderHolder, position: Int) {
            host.headerController.refreshSongsSectionHeader(holder.itemView)
        }

        override fun getItemCount(): Int = 1

        fun refresh() {
            notifyItemChanged(0)
        }
    }

    private class HeaderHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    private inner class EmptyAdapter : RecyclerView.Adapter<EmptyHolder>() {
        private var empty = true

        init {
            setHasStableIds(true)
        }

        override fun getItemId(position: Int): Long = EMPTY_ID

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EmptyHolder {
            val text = host.uiFactory.text("", 18, true).apply {
                setPadding(host.dp(12), host.dp(24), host.dp(12), host.dp(24))
            }
            return EmptyHolder(text)
        }

        override fun onBindViewHolder(holder: EmptyHolder, position: Int) {
            holder.text.text = if (host.navigationState.search.trim().isEmpty()) {
                host.tr("Add MP3 or another audio file", "Добавьте MP3 или другой аудиофайл")
            } else {
                host.tr("Nothing found", "Ничего не найдено")
            }
        }

        override fun getItemCount(): Int = if (empty) 1 else 0

        fun setEmpty(value: Boolean) {
            if (empty == value) {
                if (empty) notifyItemChanged(0)
                return
            }
            empty = value
            if (empty) notifyItemInserted(0) else notifyItemRemoved(0)
        }
    }

    private class EmptyHolder(val text: TextView) : RecyclerView.ViewHolder(text)

    private companion object {
        const val HEADER_ID = 1L
        const val EMPTY_ID = 2L
        const val PROGRESS_INTERVAL_MS = 500L

        fun sameSnapshot(left: List<Track>, right: List<Track>): Boolean {
            if (left.size != right.size) return false
            for (index in left.indices) {
                val first = left[index]
                val second = right[index]
                if (
                    first.trackId != second.trackId ||
                    first !== second && (
                        first.uri != second.uri ||
                            first.title != second.title ||
                            first.durationMs != second.durationMs
                        )
                ) {
                    return false
                }
            }
            return true
        }
    }
}
