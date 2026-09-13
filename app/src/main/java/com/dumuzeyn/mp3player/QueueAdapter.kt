package com.dumuzeyn.mp3player

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

internal class QueueAdapter(
    private val host: MainActivityCore,
    tracks: List<Track>,
    private val listener: Listener,
) : ListAdapter<Track, QueueAdapter.Holder>(diff) {
    interface Listener {
        fun remove(index: Int)
        fun play(index: Int)
        fun move(from: Int, to: Int)
    }

    private var currentTrackId = ""

    init {
        setHasStableIds(true)
        captureCurrentTrack()
        submitTracks(tracks)
    }

    override fun getItemId(position: Int): Long = getItem(position).trackId.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val container = FrameLayout(host).apply {
            layoutParams = RecyclerView.LayoutParams(-1, -2)
        }
        return Holder(container)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.container.removeAllViews()
        val track = getItem(position)
        holder.container.addView(
            host.songsRenderer.queueRow(
                track,
                { remove(holder.bindingAdapterPosition) },
                {
                    listener.play(holder.bindingAdapterPosition)
                    host.uiHandler.postDelayed(::refreshPlayback, 80L)
                },
            ),
            FrameLayout.LayoutParams(-1, -2),
        )
    }

    fun submitTracks(tracks: List<Track>) {
        submitList(ArrayList(tracks), ::refreshPlayback)
    }

    fun refreshPlayback() {
        val previous = currentTrackId
        captureCurrentTrack()
        notifyTrack(previous)
        if (previous != currentTrackId) notifyTrack(currentTrackId)
    }

    fun move(from: Int, to: Int): Boolean {
        if (from < 0 || to < 0 || from >= itemCount || to >= itemCount) return false
        val tracks = ArrayList(currentList)
        val moved = tracks.removeAt(from)
        tracks.add(to, moved)
        submitList(tracks)
        listener.move(from, to)
        return true
    }

    fun remove(position: Int) {
        if (position < 0 || position >= itemCount) return
        val tracks = ArrayList(currentList)
        tracks.removeAt(position)
        submitList(tracks)
        listener.remove(position)
    }

    private fun captureCurrentTrack() {
        currentTrackId = host.playbackStateProvider.currentTrack()?.trackId.orEmpty()
    }

    private fun notifyTrack(trackId: String) {
        if (trackId.isEmpty()) return
        currentList.forEachIndexed { index, track ->
            if (trackId == track.trackId) {
                notifyItemChanged(index)
                return
            }
        }
    }

    class Holder(val container: FrameLayout) : RecyclerView.ViewHolder(container)

    private companion object {
        val diff = object : DiffUtil.ItemCallback<Track>() {
            override fun areItemsTheSame(oldItem: Track, newItem: Track): Boolean =
                oldItem.trackId == newItem.trackId

            override fun areContentsTheSame(oldItem: Track, newItem: Track): Boolean =
                oldItem.uri == newItem.uri && oldItem.title == newItem.title
        }
    }
}
