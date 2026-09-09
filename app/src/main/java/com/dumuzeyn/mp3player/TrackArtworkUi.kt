package com.dumuzeyn.mp3player

import android.content.Context
import android.graphics.Rect
import android.os.Handler
import android.os.Trace
import android.widget.ImageView
import android.widget.LinearLayout
import kotlin.math.roundToInt

/** Owns artwork loading, visible artwork bindings, waveform creation, and memory trimming. */
internal class TrackArtworkUi(
    private val context: Context,
    private val mainHandler: Handler,
    private val dependencies: Dependencies,
) : AutoCloseable {
    interface Dependencies {
        fun renderingPreview(): Boolean
        fun activeRows(): SongRowStateRegistry
        fun findTrack(uri: String): Track?
        fun isCurrent(track: Track): Boolean
        fun isPlaying(): Boolean
        fun activeColor(): Int
        fun secondaryActiveColor(): Int
        fun inactiveColor(): Int
        fun animationsEnabled(): Boolean
    }

    private val coverLoader = CoverLoader(context, mainHandler)
    private val promoteVisible = Runnable(::promoteVisibleArtwork)

    fun loadCover(view: ImageView, track: Track, fallbackColor: Int) {
        loadCover(view, track, fallbackColor, CoverLoader.THUMB_SIZE)
    }

    fun loadCover(view: ImageView, track: Track, fallbackColor: Int, maxSize: Int) {
        registerCover(view, track)
        if (dependencies.renderingPreview()) {
            coverLoader.loadCachedOnly(view, track, fallbackColor, maxSize)
        } else {
            coverLoader.load(view, track, fallbackColor, maxSize)
        }
    }

    fun loadCoverSmooth(view: ImageView, track: Track, fallbackColor: Int) {
        registerCover(view, track)
        if (dependencies.renderingPreview()) {
            coverLoader.loadCachedOnly(view, track, fallbackColor, CoverLoader.THUMB_SIZE)
        } else {
            coverLoader.loadSmooth(
                view,
                track,
                fallbackColor,
                CoverLoader.THUMB_SIZE,
                if (dependencies.animationsEnabled()) 320 else 0,
            )
        }
    }

    fun loadUnregisteredCover(view: ImageView, track: Track, fallbackColor: Int, maxSize: Int) {
        if (view is RotatingCoverImageView) view.bindTrack(track)
        coverLoader.load(view, track, fallbackColor, maxSize)
    }

    fun prefetch(tracks: List<Track>) {
        coverLoader.prefetch(tracks)
    }

    fun prefetchBeforeRender(tracks: List<Track>, onComplete: () -> Unit) {
        coverLoader.prefetchBeforeRender(tracks, onComplete)
    }

    fun promoteVisibleArtwork() {
        mainHandler.removeCallbacks(promoteVisible)
        Trace.beginSection("Voltune/Home.promoteArtwork")
        try {
            dependencies.activeRows().forEachCover { uri, cover ->
                val visibleBounds = Rect()
                if (!cover.isAttachedToWindow || !cover.isShown ||
                    !cover.getGlobalVisibleRect(visibleBounds) ||
                    visibleBounds.width() <= 0 || visibleBounds.height() <= 0
                ) {
                    return@forEachCover
                }
                dependencies.findTrack(uri)?.let { track ->
                    coverLoader.loadSmooth(
                        cover,
                        track,
                        dependencies.inactiveColor(),
                        CoverLoader.THUMB_SIZE,
                        if (dependencies.animationsEnabled()) 180 else 0,
                    )
                }
            }
        } finally {
            Trace.endSection()
        }
    }

    fun scheduleVisibleArtworkPromotion() {
        mainHandler.removeCallbacks(promoteVisible)
        mainHandler.postDelayed(promoteVisible, 72L)
    }

    fun seedFromView(view: ImageView, track: Track) {
        coverLoader.seedFromView(view, track)
    }

    fun clearCover(view: ImageView, fallbackColor: Int) {
        coverLoader.clear(view, fallbackColor)
        if (view is RotatingCoverImageView) view.bindTrack(null)
    }

    fun createWaveform(track: Track, active: Boolean): WaveformView =
        WaveformView(
            context,
            track.title + track.uri,
            if (active) dependencies.activeColor() else dependencies.inactiveColor(),
            dependencies.secondaryActiveColor(),
            active && dependencies.isPlaying(),
        ).apply {
            minimumHeight = dp(28)
            val verticalPadding = dp(3)
            setPadding(0, verticalPadding, 0, verticalPadding)
            layoutParams = LinearLayout.LayoutParams(dp(190), dp(30))
        }

    fun onTrimMemory(level: Int) {
        coverLoader.trimMemory(level)
    }

    override fun close() {
        mainHandler.removeCallbacks(promoteVisible)
        coverLoader.close()
    }

    private fun registerCover(view: ImageView, track: Track?) {
        if (view !is RotatingCoverImageView) return
        view.bindTrack(track)
        if (track != null) dependencies.activeRows().registerCover(track.uri, view)
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).roundToInt().coerceAtLeast(1)
}
