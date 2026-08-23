package com.dumuzeyn.mp3player;

import android.net.Uri;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;

public final class MediaItemMapper {
    public MediaItem toMediaItem(Track track) {
        MediaMetadata metadata = new MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artist)
                .setAlbumTitle(track.album)
                .setArtworkUri(track.asUri())
                .build();
        return new MediaItem.Builder()
                .setMediaId(mediaId(track))
                .setUri(Uri.parse(track.uri))
                .setMediaMetadata(metadata)
                .build();
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
}
