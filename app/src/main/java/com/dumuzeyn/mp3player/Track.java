package com.dumuzeyn.mp3player;

import android.net.Uri;
import java.util.Locale;

public class Track {
    public final String album;
    public final String albumArtist;
    public final String artist;
    public final long dateAdded;
    public final int discNumber;
    public final int durationMs;
    public final String genre;
    public final String title;
    public final String trackId;
    public final String uri;
    public final long fileSize;
    public final long lastModified;
    public final long lastCompletedAt;
    public final long lastPlayedAt;
    public final int playCount;
    public final int skipCount;
    public final int trackNumber;
    public final int year;
    public final String fingerprint;
    public final String normalizedSearchText;

    public Track(String uri, String title, String artist) {
        this(uri, title, artist, "Unknown album", "Unknown genre", 0);
    }

    public Track(String uri, String title, String artist, String album, String genre) {
        this(uri, title, artist, album, genre, 0);
    }

    public Track(String uri, String title, String artist, String album, String genre, int durationMs) {
        this(TrackIdentity.fromLegacyUri(uri), uri, title, artist, album, genre, durationMs,
                -1L, 0L, "");
    }

    public Track(String trackId, String uri, String title, String artist, String album,
            String genre, int durationMs, long fileSize, long lastModified, String fingerprint) {
        this(trackId, uri, title, artist, album, artist, genre, 0, 0, 0, durationMs,
                fileSize, lastModified, fingerprint, 0, 0, System.currentTimeMillis(), 0L, 0L);
    }

    public Track(String trackId, String uri, String title, String artist, String album,
            String albumArtist, String genre, int year, int trackNumber, int discNumber,
            int durationMs, long fileSize, long lastModified, String fingerprint,
            int playCount, int skipCount, long dateAdded, long lastPlayedAt,
            long lastCompletedAt) {
        this.trackId = trackId == null || trackId.trim().isEmpty()
                ? TrackIdentity.create() : trackId;
        this.uri = uri;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.albumArtist = albumArtist == null || albumArtist.trim().isEmpty()
                ? artist : albumArtist;
        this.genre = GenreNormalizer.normalize(genre);
        this.year = Math.max(0, year);
        this.trackNumber = Math.max(0, trackNumber);
        this.discNumber = Math.max(0, discNumber);
        this.durationMs = Math.max(0, durationMs);
        this.fileSize = fileSize;
        this.lastModified = Math.max(0L, lastModified);
        this.fingerprint = fingerprint == null ? "" : fingerprint;
        this.playCount = Math.max(0, playCount);
        this.skipCount = Math.max(0, skipCount);
        this.dateAdded = Math.max(0L, dateAdded);
        this.lastPlayedAt = Math.max(0L, lastPlayedAt);
        this.lastCompletedAt = Math.max(0L, lastCompletedAt);
        this.normalizedSearchText = buildSearchText();
    }

    public Track withLocation(String newUri, long newSize, long newLastModified,
            String newFingerprint) {
        return new Track(trackId, newUri, title, artist, album, genre, durationMs,
                newSize, newLastModified, newFingerprint).withDetails(this);
    }

    public Track withMetadata(String newTitle, String newArtist, String newAlbum,
            String newAlbumArtist, String newGenre, int newYear, int newTrackNumber,
            int newDiscNumber) {
        return new Track(trackId, uri, newTitle, newArtist, newAlbum, newAlbumArtist,
                newGenre, newYear, newTrackNumber, newDiscNumber, durationMs, fileSize,
                lastModified, fingerprint, playCount, skipCount, dateAdded, lastPlayedAt,
                lastCompletedAt);
    }

    public Track withPlaybackStats(int newPlayCount, int newSkipCount, long newLastPlayedAt,
            long newLastCompletedAt) {
        return new Track(trackId, uri, title, artist, album, albumArtist, genre, year,
                trackNumber, discNumber, durationMs, fileSize, lastModified, fingerprint,
                newPlayCount, newSkipCount, dateAdded, newLastPlayedAt, newLastCompletedAt);
    }

    private Track withDetails(Track source) {
        return new Track(trackId, uri, title, artist, album, source.albumArtist, genre,
                source.year, source.trackNumber, source.discNumber, durationMs, fileSize,
                lastModified, fingerprint, source.playCount, source.skipCount,
                source.dateAdded, source.lastPlayedAt, source.lastCompletedAt);
    }

    public Uri asUri() {
        return Uri.parse(this.uri);
    }

    static String normalizeSearchText(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private String buildSearchText() {
        StringBuilder value = new StringBuilder()
                .append(title).append(' ').append(artist).append(' ')
                .append(album).append(' ').append(genre);
        if (!normalizeSearchText(albumArtist).equals(normalizeSearchText(artist))) {
            value.append(' ').append(albumArtist);
        }
        if (year > 0) {
            value.append(' ').append(year);
        }
        return normalizeSearchText(value.toString());
    }
}
