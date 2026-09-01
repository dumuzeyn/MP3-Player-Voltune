package com.dumuzeyn.mp3player;

import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;

public final class MediaItemMapper {
    private static final String EXTRA_DURATION = "voltune.duration";
    private static final String EXTRA_FILE_SIZE = "voltune.fileSize";
    private static final String EXTRA_FINGERPRINT = "voltune.fingerprint";
    private static final String EXTRA_GENRE = "voltune.genre";
    private static final String EXTRA_LAST_MODIFIED = "voltune.lastModified";

    public MediaItem toMediaItem(Track track) {
        Bundle extras = new Bundle();
        extras.putInt(EXTRA_DURATION, track.durationMs);
        extras.putLong(EXTRA_FILE_SIZE, track.fileSize);
        extras.putString(EXTRA_FINGERPRINT, track.fingerprint);
        extras.putString(EXTRA_GENRE, track.genre);
        extras.putLong(EXTRA_LAST_MODIFIED, track.lastModified);
        MediaMetadata metadata = new MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artist)
                .setAlbumTitle(track.album)
                .setArtworkUri(track.asUri())
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setExtras(extras)
                .build();
        return new MediaItem.Builder()
                .setMediaId(mediaId(track))
                .setUri(Uri.parse(track.uri))
                .setMediaMetadata(metadata)
                .build();
    }

    @Nullable
    Track fromMediaItem(@Nullable MediaItem item) {
        if (item == null || item.localConfiguration == null) {
            return null;
        }
        MediaMetadata metadata = item.mediaMetadata;
        Bundle extras = metadata.extras == null ? Bundle.EMPTY : metadata.extras;
        String uri = item.localConfiguration.uri.toString();
        return new Track(item.mediaId, uri,
                text(metadata.title, "Song"), text(metadata.artist, "Unknown artist"),
                text(metadata.albumTitle, "Unknown album"),
                extras.getString(EXTRA_GENRE, "Unknown genre"),
                extras.getInt(EXTRA_DURATION, 0), extras.getLong(EXTRA_FILE_SIZE, -1L),
                extras.getLong(EXTRA_LAST_MODIFIED, 0L),
                extras.getString(EXTRA_FINGERPRINT, ""));
    }

    public String mediaId(Track track) {
        return track == null ? "" : track.trackId;
    }

    static boolean matchesMediaId(Track track, String mediaId) {
        if (track == null || mediaId == null || mediaId.isEmpty()) {
            return false;
        }
        return mediaId.equals(track.trackId) || mediaId.equals(stableHash(track.uri));
    }

    /** Compatibility helper for state created before stable track IDs were introduced. */
    public static String stableHash(String value) {
        return TrackIdentity.fromLegacyUri(value);
    }

    private static String text(@Nullable CharSequence value, String fallback) {
        return value == null || value.length() == 0 ? fallback : value.toString();
    }
}
