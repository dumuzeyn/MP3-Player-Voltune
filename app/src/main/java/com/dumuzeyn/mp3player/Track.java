package com.dumuzeyn.mp3player;

import android.net.Uri;
import java.util.Locale;

public class Track {
    public final String album;
    public final String artist;
    public final int durationMs;
    public final String genre;
    public final String title;
    public final String trackId;
    public final String uri;
    public final long fileSize;
    public final long lastModified;
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
        this.trackId = trackId == null || trackId.trim().isEmpty()
                ? TrackIdentity.create() : trackId;
        this.uri = uri;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.genre = genre;
        this.durationMs = Math.max(0, durationMs);
        this.fileSize = fileSize;
        this.lastModified = Math.max(0L, lastModified);
        this.fingerprint = fingerprint == null ? "" : fingerprint;
        this.normalizedSearchText = normalizeSearchText(
                this.title + " " + this.artist + " " + this.album + " " + this.genre);
    }

    public Track withLocation(String newUri, long newSize, long newLastModified,
            String newFingerprint) {
        return new Track(trackId, newUri, title, artist, album, genre, durationMs,
                newSize, newLastModified, newFingerprint);
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
}
