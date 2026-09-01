package com.dumuzeyn.mp3player

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

class MediaItemMapper {
    fun toMediaItem(track: Track): MediaItem {
        val extras = Bundle().apply {
            putInt(EXTRA_DURATION, track.durationMs)
            putLong(EXTRA_FILE_SIZE, track.fileSize)
            putString(EXTRA_FINGERPRINT, track.fingerprint)
            putString(EXTRA_GENRE, track.genre)
            putLong(EXTRA_LAST_MODIFIED, track.lastModified)
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
            .setArtworkUri(track.asUri())
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setExtras(extras)
            .build()
        return MediaItem.Builder()
            .setMediaId(mediaId(track))
            .setUri(Uri.parse(track.uri))
            .setMediaMetadata(metadata)
            .build()
    }

    fun fromMediaItem(item: MediaItem?): Track? {
        val configuration = item?.localConfiguration ?: return null
        val metadata = item.mediaMetadata
        val extras = metadata.extras ?: Bundle.EMPTY
        return Track(
            item.mediaId,
            configuration.uri.toString(),
            metadata.title.textOr("Song"),
            metadata.artist.textOr("Unknown artist"),
            metadata.albumTitle.textOr("Unknown album"),
            extras.getString(EXTRA_GENRE, "Unknown genre"),
            extras.getInt(EXTRA_DURATION, 0),
            extras.getLong(EXTRA_FILE_SIZE, -1L),
            extras.getLong(EXTRA_LAST_MODIFIED, 0L),
            extras.getString(EXTRA_FINGERPRINT, ""),
        )
    }

    fun mediaId(track: Track?): String = track?.trackId.orEmpty()

    companion object {
        private const val EXTRA_DURATION = "voltune.duration"
        private const val EXTRA_FILE_SIZE = "voltune.fileSize"
        private const val EXTRA_FINGERPRINT = "voltune.fingerprint"
        private const val EXTRA_GENRE = "voltune.genre"
        private const val EXTRA_LAST_MODIFIED = "voltune.lastModified"

        @JvmStatic
        fun matchesMediaId(track: Track?, mediaId: String?): Boolean =
            track != null && !mediaId.isNullOrEmpty() &&
                (mediaId == track.trackId || mediaId == stableHash(track.uri))

        /** Compatibility helper for state created before stable track IDs were introduced. */
        @JvmStatic
        fun stableHash(value: String): String = TrackIdentity.fromLegacyUri(value)

        private fun CharSequence?.textOr(fallback: String): String =
            if (isNullOrEmpty()) fallback else toString()
    }
}
