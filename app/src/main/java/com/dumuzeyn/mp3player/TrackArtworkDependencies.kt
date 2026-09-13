package com.dumuzeyn.mp3player

/** Narrow functional binding used by artwork UI without exposing the Activity. */
internal class TrackArtworkDependencies(
    private val preview: BooleanValueProvider,
    private val rows: ValueProvider<SongRowStateRegistry>,
    private val trackFinder: TrackFinder,
    private val currentTrack: TrackPredicate,
    private val playing: BooleanValueProvider,
    private val activeColor: IntValueProvider,
    private val secondaryColor: IntValueProvider,
    private val inactiveColor: IntValueProvider,
    private val animations: BooleanValueProvider,
) : TrackArtworkUi.Dependencies {
    override fun renderingPreview(): Boolean = preview.get()

    override fun activeRows(): SongRowStateRegistry = rows.get()

    override fun findTrack(uri: String): Track? = trackFinder.find(uri)

    override fun isCurrent(track: Track): Boolean = currentTrack.test(track)

    override fun isPlaying(): Boolean = playing.get()

    override fun activeColor(): Int = activeColor.get()

    override fun secondaryActiveColor(): Int = secondaryColor.get()

    override fun inactiveColor(): Int = inactiveColor.get()

    override fun animationsEnabled(): Boolean = animations.get()
}
