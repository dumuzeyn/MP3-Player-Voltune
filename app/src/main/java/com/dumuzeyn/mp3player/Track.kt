package com.dumuzeyn.mp3player

import android.net.Uri
import java.util.Locale

class Track(
    trackId: String?,
    @JvmField val uri: String,
    @JvmField val title: String,
    @JvmField val artist: String,
    @JvmField val album: String,
    albumArtist: String?,
    genre: String?,
    year: Int,
    trackNumber: Int,
    discNumber: Int,
    durationMs: Int,
    @JvmField val fileSize: Long,
    lastModified: Long,
    fingerprint: String?,
    playCount: Int,
    skipCount: Int,
    dateAdded: Long,
    lastPlayedAt: Long,
    lastCompletedAt: Long,
) {
    @JvmField val trackId: String = trackId?.takeIf { it.isNotBlank() } ?: TrackIdentity.create()
    @JvmField val albumArtist: String = albumArtist?.takeIf { it.isNotBlank() } ?: artist
    @JvmField val genre: String = GenreNormalizer.normalize(genre)
    @JvmField val year: Int = year.coerceAtLeast(0)
    @JvmField val trackNumber: Int = trackNumber.coerceAtLeast(0)
    @JvmField val discNumber: Int = discNumber.coerceAtLeast(0)
    @JvmField val durationMs: Int = durationMs.coerceAtLeast(0)
    @JvmField val lastModified: Long = lastModified.coerceAtLeast(0L)
    @JvmField val fingerprint: String = fingerprint.orEmpty()
    @JvmField val playCount: Int = playCount.coerceAtLeast(0)
    @JvmField val skipCount: Int = skipCount.coerceAtLeast(0)
    @JvmField val dateAdded: Long = dateAdded.coerceAtLeast(0L)
    @JvmField val lastPlayedAt: Long = lastPlayedAt.coerceAtLeast(0L)
    @JvmField val lastCompletedAt: Long = lastCompletedAt.coerceAtLeast(0L)
    @JvmField val normalizedSearchText: String = buildSearchText()

    constructor(uri: String, title: String, artist: String) :
        this(uri, title, artist, "Unknown album", "Unknown genre", 0)

    constructor(uri: String, title: String, artist: String, album: String, genre: String?) :
        this(uri, title, artist, album, genre, 0)

    constructor(
        uri: String,
        title: String,
        artist: String,
        album: String,
        genre: String?,
        durationMs: Int,
    ) : this(
        TrackIdentity.fromLegacyUri(uri),
        uri,
        title,
        artist,
        album,
        genre,
        durationMs,
        -1L,
        0L,
        "",
    )

    constructor(
        trackId: String?,
        uri: String,
        title: String,
        artist: String,
        album: String,
        genre: String?,
        durationMs: Int,
        fileSize: Long,
        lastModified: Long,
        fingerprint: String?,
    ) : this(
        trackId,
        uri,
        title,
        artist,
        album,
        artist,
        genre,
        0,
        0,
        0,
        durationMs,
        fileSize,
        lastModified,
        fingerprint,
        0,
        0,
        System.currentTimeMillis(),
        0L,
        0L,
    )

    fun withLocation(
        newUri: String,
        newSize: Long,
        newLastModified: Long,
        newFingerprint: String?,
    ): Track = Track(
        trackId,
        newUri,
        title,
        artist,
        album,
        genre,
        durationMs,
        newSize,
        newLastModified,
        newFingerprint,
    ).withDetails(this)

    fun withMetadata(
        newTitle: String,
        newArtist: String,
        newAlbum: String,
        newAlbumArtist: String?,
        newGenre: String?,
        newYear: Int,
        newTrackNumber: Int,
        newDiscNumber: Int,
    ): Track = Track(
        trackId,
        uri,
        newTitle,
        newArtist,
        newAlbum,
        newAlbumArtist,
        newGenre,
        newYear,
        newTrackNumber,
        newDiscNumber,
        durationMs,
        fileSize,
        lastModified,
        fingerprint,
        playCount,
        skipCount,
        dateAdded,
        lastPlayedAt,
        lastCompletedAt,
    )

    fun withPlaybackStats(
        newPlayCount: Int,
        newSkipCount: Int,
        newLastPlayedAt: Long,
        newLastCompletedAt: Long,
    ): Track = Track(
        trackId,
        uri,
        title,
        artist,
        album,
        albumArtist,
        genre,
        year,
        trackNumber,
        discNumber,
        durationMs,
        fileSize,
        lastModified,
        fingerprint,
        newPlayCount,
        newSkipCount,
        dateAdded,
        newLastPlayedAt,
        newLastCompletedAt,
    )

    fun asUri(): Uri = Uri.parse(uri)

    private fun withDetails(source: Track): Track = Track(
        trackId,
        uri,
        title,
        artist,
        album,
        source.albumArtist,
        genre,
        source.year,
        source.trackNumber,
        source.discNumber,
        durationMs,
        fileSize,
        lastModified,
        fingerprint,
        source.playCount,
        source.skipCount,
        source.dateAdded,
        source.lastPlayedAt,
        source.lastCompletedAt,
    )

    private fun buildSearchText(): String = buildString {
        append(title).append(' ').append(artist).append(' ').append(album).append(' ').append(genre)
        if (normalizeSearchText(albumArtist) != normalizeSearchText(artist)) {
            append(' ').append(albumArtist)
        }
        if (year > 0) append(' ').append(year)
    }.let(::normalizeSearchText)

    companion object {
        private val whitespace = Regex("\\s+")

        @JvmStatic
        fun normalizeSearchText(value: String?): String = value
            ?.lowercase(Locale.ROOT)
            ?.replace(whitespace, " ")
            ?.trim()
            .orEmpty()
    }
}
