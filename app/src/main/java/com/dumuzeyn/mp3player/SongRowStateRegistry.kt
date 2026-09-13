package com.dumuzeyn.mp3player

import android.os.Trace
import android.view.View
import android.widget.Button
import android.widget.TextView
import kotlin.math.roundToInt

internal class SongRowStateRegistry {
    interface StateResolver {
        fun currentTrack(): Track?
        fun isPlaying(): Boolean
        fun activeColor(): Int
        fun secondaryActiveColor(): Int
        fun inactiveColor(): Int
    }

    fun interface CoverConsumer {
        fun accept(uri: String, cover: RotatingCoverImageView)
    }

    private val playButtons = HashMap<String, Button>()
    private val currentMarkers = HashMap<String, View>()
    private val waveforms = HashMap<String, WaveformView>()
    private val covers = HashMap<String, ArrayList<RotatingCoverImageView>>()
    private val titles = HashMap<String, TextView>()
    private val durations = HashMap<String, TextView>()

    fun clear() {
        playButtons.clear()
        currentMarkers.clear()
        waveforms.clear()
        covers.clear()
        titles.clear()
        durations.clear()
    }

    fun replaceWith(source: SongRowStateRegistry) {
        clear()
        playButtons.putAll(source.playButtons)
        currentMarkers.putAll(source.currentMarkers)
        waveforms.putAll(source.waveforms)
        titles.putAll(source.titles)
        durations.putAll(source.durations)
        source.covers.forEach { (uri, registered) -> covers[uri] = ArrayList(registered) }
    }

    fun forEachCover(consumer: CoverConsumer) {
        covers.forEach { (uri, registered) ->
            registered.forEach { cover -> consumer.accept(uri, cover) }
        }
    }

    fun registerPlayButton(uri: String, button: Button) {
        playButtons[uri] = button
    }

    fun registerCurrentMarker(uri: String, marker: View) {
        currentMarkers[uri] = marker
    }

    fun registerWaveform(uri: String, waveform: WaveformView) {
        waveforms[uri] = waveform
    }

    fun registerCover(uri: String, cover: RotatingCoverImageView) {
        val registered = covers.getOrPut(uri, ::ArrayList)
        if (!registered.contains(cover)) registered.add(cover)
    }

    fun registerMetadata(uri: String, title: TextView, duration: TextView) {
        titles[uri] = title
        durations[uri] = duration
    }

    fun refreshMetadata(uri: String, track: Track, durationText: String) {
        titles[uri]?.text = track.title
        durations[uri]?.text = durationText
    }

    fun refresh(resolver: StateResolver) {
        Trace.beginSection("Voltune/Home.refreshSongRows")
        try {
            val current = resolver.currentTrack()
            val currentUri = current?.uri.orEmpty()
            val playing = resolver.isPlaying()
            playButtons.forEach { (uri, button) ->
                applyPlayState(button, uri == currentUri && playing)
            }
            currentMarkers.forEach { (uri, marker) ->
                val visibility = if (uri == currentUri) View.VISIBLE else View.INVISIBLE
                if (marker.visibility != visibility) marker.visibility = visibility
            }
            waveforms.forEach { (uri, waveform) ->
                val rowCurrent = uri == currentUri
                waveform.setState(
                    if (rowCurrent) resolver.activeColor() else resolver.inactiveColor(),
                    resolver.secondaryActiveColor(),
                    rowCurrent && playing,
                )
            }
            covers.values.forEach { registered ->
                registered.forEach { cover ->
                    cover.updatePlaybackState(current, playing)
                }
            }
        } finally {
            Trace.endSection()
        }
    }

    fun setWaveformsTransitionPaused(paused: Boolean) {
        waveforms.values.forEach { it.setTransitionPaused(paused) }
    }

    companion object {
        @JvmStatic
        fun applyPlayState(button: Button, playing: Boolean) {
            val symbol = if (playing) "Ⅱ" else "▶"
            if (symbol.contentEquals(button.text)) return
            button.text = symbol
            val opticalOffset = if (playing) {
                0
            } else {
                (button.resources.displayMetrics.density * 2f).roundToInt()
            }
            button.setPadding(opticalOffset, 0, 0, 0)
        }
    }
}
