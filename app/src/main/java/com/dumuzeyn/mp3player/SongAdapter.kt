package com.dumuzeyn.mp3player

import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.max

/** Recycles song rows and applies small state changes through payload binds. */
internal class SongAdapter(private val host: MainActivityCore) :
    ListAdapter<Track, SongAdapter.SongViewHolder>(trackDiff) {
    private val positionsByTrackId = HashMap<String, Int>()
    private var currentTrackId = ""
    private var playing = false
    private var playbackPositionMs = 0L
    private var playbackDurationMs = 0L

    init {
        setHasStableIds(true)
        capturePlaybackState()
    }

    override fun getItemId(position: Int): Long = stableLongId(getItem(position).trackId)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder =
        SongViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_song, parent, false),
        )

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onBindViewHolder(
        holder: SongViewHolder,
        position: Int,
        payloads: MutableList<Any>,
    ) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position)
            return
        }
        var payload = 0
        for (item in payloads) {
            if (item is Int) payload = payload or item
        }
        holder.bindPayload(getItem(position), payload)
    }

    override fun onViewRecycled(holder: SongViewHolder) {
        holder.recycle()
    }

    override fun onViewAttachedToWindow(holder: SongViewHolder) {
        holder.updatePlayback()
    }

    override fun onViewDetachedFromWindow(holder: SongViewHolder) {
        holder.pauseDetachedAnimations()
    }

    override fun onCurrentListChanged(previousList: MutableList<Track>, currentList: MutableList<Track>) {
        positionsByTrackId.clear()
        for (index in currentList.indices) {
            positionsByTrackId[currentList[index].trackId] = index
        }
    }

    fun refreshPlayback() {
        val previousTrackId = currentTrackId
        val previousPlaying = playing
        capturePlaybackState()
        if (previousTrackId != currentTrackId) {
            notifyTrack(previousTrackId, PAYLOAD_PLAYBACK or PAYLOAD_POSITION)
            notifyTrack(currentTrackId, PAYLOAD_PLAYBACK or PAYLOAD_POSITION)
        } else if (previousPlaying != playing) {
            notifyTrack(currentTrackId, PAYLOAD_PLAYBACK)
        }
    }

    fun replaceTrack(updated: Track?) {
        updated ?: return
        for (index in currentList.indices) {
            if (currentList[index].trackId == updated.trackId) {
                val replacement = ArrayList(currentList)
                replacement[index] = updated
                submitList(replacement)
                return
            }
        }
    }

    fun refreshPosition(positionMs: Long, durationMs: Long) {
        playbackPositionMs = max(0L, positionMs)
        playbackDurationMs = max(0L, durationMs)
        notifyTrack(currentTrackId, PAYLOAD_POSITION)
    }

    fun visibleTracks(): List<Track> = ArrayList(currentList)

    private fun capturePlaybackState() {
        val current = host.libraryState.tracks.getOrNull(host.currentTrackIndex())
        currentTrackId = current?.trackId.orEmpty()
        playing = host.isPlaybackPlaying()
        playbackPositionMs = host.playbackPosition().toLong()
        playbackDurationMs = current?.let(host::playbackDurationFor)?.toLong() ?: 0L
    }

    private fun notifyTrack(trackId: String, payload: Int) {
        positionsByTrackId[trackId]?.let { notifyItemChanged(it, payload) }
    }

    inner class SongViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val card: View = itemView.findViewById(R.id.song_card)
        private val cardBackground = GradientDrawable()
        private val coverContainer: FrameLayout = itemView.findViewById(R.id.song_cover_container)
        private val cover = RotatingCoverImageView(host)
        private val waveformContainer: FrameLayout =
            itemView.findViewById(R.id.song_waveform_container)
        private val waveform = WaveformView(host, "", host.purpleSoft, host.yellow, false)
        private val title: TextView = itemView.findViewById(R.id.song_title)
        private val duration: TextView = itemView.findViewById(R.id.song_duration)
        private val play: Button = itemView.findViewById(R.id.song_play)
        private val marker: View = itemView.findViewById(R.id.song_current_marker)
        private var boundTrack: Track? = null

        init {
            cardBackground.setColor(
                host.cardSurfaceColor(host.card, host.appearanceState.songCardOpacity),
            )
            cardBackground.cornerRadius = host.dp(14).toFloat()
            cardBackground.setStroke(host.dp(1), host.cardStroke)
            card.background = cardBackground
            TextOutlinePolicy.markCardSurface(card, true)

            cover.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            coverContainer.addView(cover, FrameLayout.LayoutParams(-1, -1))
            waveform.setPadding(0, host.dp(3), 0, host.dp(3))
            waveformContainer.addView(waveform, FrameLayout.LayoutParams(-1, -1))

            title.setTextColor(host.primaryText)
            title.ellipsize = TextUtils.TruncateAt.END
            duration.setTextColor(host.secondaryText)
            NowPlayingIndicator.style(marker, host.yellow)
            configureButton(play)
            host.uiFactory.applyPlainIconStyle(play, host.purple)

            val openOrPlay = View.OnClickListener {
                TrackTapController.handle(host, boundTrack, cover)
            }
            card.setOnClickListener(openOrPlay)
            cover.setOnClickListener(openOrPlay)
            val properties = View.OnLongClickListener {
                boundTrack?.let(host.overlayController::openSongActions)
                true
            }
            card.setOnLongClickListener(properties)
            cover.setOnLongClickListener(properties)
            play.setOnClickListener {
                val track = boundTrack ?: return@setOnClickListener
                if (host.isCurrent(track)) {
                    host.playbackQueueController.toggleOrStart()
                } else {
                    host.playbackQueueController.playTrack(track)
                }
            }
        }

        fun bind(track: Track) {
            boundTrack = track
            title.text = track.title
            duration.text = host.formatTrackDuration(track)
            val openDescription = host.tr(
                "Open or play track " + track.title,
                "Открыть или включить песню " + track.title,
            )
            card.contentDescription = openDescription
            cover.contentDescription = openDescription
            waveform.setTrackKey(track.trackId)
            updatePlayback()
            host.artworkUi.loadUnregisteredCover(
                cover,
                track,
                host.purpleSoft,
                CoverLoader.THUMB_SIZE,
            )
        }

        fun bindPayload(track: Track, payload: Int) {
            boundTrack = track
            if (payload and PAYLOAD_METADATA != 0) {
                title.text = track.title
                duration.text = host.formatTrackDuration(track)
            }
            if (payload and PAYLOAD_ARTWORK != 0) {
                host.artworkUi.loadUnregisteredCover(
                    cover,
                    track,
                    host.purpleSoft,
                    CoverLoader.THUMB_SIZE,
                )
            }
            if (payload and PAYLOAD_PLAYBACK != 0) updatePlayback()
            if (payload and PAYLOAD_POSITION != 0) updatePosition()
        }

        fun updatePlayback() {
            val track = boundTrack
            val current = track != null && track.trackId == currentTrackId
            marker.visibility = if (current) View.VISIBLE else View.INVISIBLE
            SongRowStateRegistry.applyPlayState(play, current && playing)
            waveform.setState(
                if (current) host.purple else host.purpleSoft,
                host.yellow,
                current && playing,
            )
            if (current) {
                updatePosition()
            } else {
                waveform.setProgress(0L, 0L)
            }
            cover.updatePlaybackState()
        }

        fun pauseDetachedAnimations() {
            waveform.setState(host.purpleSoft, host.yellow, false)
        }

        fun recycle() {
            boundTrack = null
            waveform.setState(host.purpleSoft, host.yellow, false)
            waveform.setProgress(0L, 0L)
            host.artworkUi.clearCover(cover, host.purpleSoft)
        }

        private fun updatePosition() {
            waveform.setProgress(playbackPositionMs, playbackDurationMs)
        }

        private fun configureButton(button: Button) {
            button.isAllCaps = false
            button.stateListAnimator = null
            button.elevation = 0f
            button.minWidth = 0
            button.minHeight = 0
            button.setPadding(0, 0, 0, 0)
        }
    }

    companion object {
        const val PAYLOAD_PLAYBACK = 1
        const val PAYLOAD_POSITION = 1 shl 1
        const val PAYLOAD_METADATA = 1 shl 2
        const val PAYLOAD_ARTWORK = 1 shl 3

        private val trackDiff = object : DiffUtil.ItemCallback<Track>() {
            override fun areItemsTheSame(oldItem: Track, newItem: Track): Boolean =
                oldItem.trackId == newItem.trackId

            override fun areContentsTheSame(oldItem: Track, newItem: Track): Boolean =
                contentPayload(oldItem, newItem) == 0

            override fun getChangePayload(oldItem: Track, newItem: Track): Any? =
                contentPayload(oldItem, newItem).takeIf { it != 0 }
        }

        private fun contentPayload(oldItem: Track, newItem: Track): Int {
            var payload = 0
            if (
                oldItem.title != newItem.title || oldItem.artist != newItem.artist ||
                oldItem.album != newItem.album || oldItem.genre != newItem.genre ||
                oldItem.durationMs != newItem.durationMs
            ) {
                payload = payload or PAYLOAD_METADATA
            }
            if (
                oldItem.uri != newItem.uri || oldItem.fileSize != newItem.fileSize ||
                oldItem.lastModified != newItem.lastModified ||
                oldItem.fingerprint != newItem.fingerprint
            ) {
                payload = payload or PAYLOAD_ARTWORK
            }
            return payload
        }

        private fun stableLongId(trackId: String): Long {
            var result = 0xcbf29ce484222325UL.toLong()
            for (character in trackId) {
                result = result xor character.code.toLong()
                result *= 0x100000001b3L
            }
            return result
        }
    }
}
