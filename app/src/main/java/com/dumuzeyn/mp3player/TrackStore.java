package com.dumuzeyn.mp3player;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.io.InputStream;
import java.security.MessageDigest;

public final class TrackStore {
    private static final int MAX_TEXT_LENGTH = 160;

    private TrackStore() {
    }

    public static ArrayList<Track> load(Context context) {
        LibraryDatabase.migrateLegacyIfNeeded(context);
        LibraryDatabase database = new LibraryDatabase(context);
        try {
            return database.loadTracks();
        } finally {
            database.close();
        }
    }

    static ArrayList<Track> loadFromJson(String raw) {
        ArrayList<Track> tracks = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(raw == null ? "[]" : raw);
            for (int index = 0; index < array.length(); index++) {
                JSONObject item = array.getJSONObject(index);
                tracks.add(new Track(
                        item.getString("uri"),
                        item.optString("title", "Song"),
                        item.optString("artist", "Unknown artist"),
                        item.optString("album", "Unknown album"),
                        item.optString("genre", "Unknown genre"),
                        item.optInt("durationMs", 0)
                ));
            }
        } catch (Exception e) {
            VoltuneLog.failure("track_load_failed", e);
            tracks.clear();
        }
        sort(tracks);
        return tracks;
    }

    public static void save(Context context, List<Track> tracks) {
        LibraryDatabase database = new LibraryDatabase(context);
        try {
            database.saveTracks(tracks);
        } finally {
            database.close();
        }
    }

    public static void upsert(Context context, Track track) {
        LibraryDatabase database = new LibraryDatabase(context);
        try {
            database.upsertTrack(track);
        } finally {
            database.close();
        }
    }

    public static void delete(Context context, Track track) {
        if (track == null) {
            return;
        }
        LibraryDatabase database = new LibraryDatabase(context);
        try {
            database.deleteTrack(track.trackId);
        } finally {
            database.close();
        }
    }

    public static void updateMetadata(Context context, Track track) {
        LibraryDatabase database = new LibraryDatabase(context);
        try {
            database.updateTrackMetadata(track);
        } finally {
            database.close();
        }
    }

    public static void updateMetadata(Context context, List<Track> tracks) {
        LibraryDatabase database = new LibraryDatabase(context);
        try {
            database.updateTrackMetadata(tracks);
        } finally {
            database.close();
        }
    }

    public static Track fromUri(Context context, Uri uri) {
        boolean canOpen = canOpenForRead(context, uri);
        if (!canOpen) {
            VoltuneLog.warning("add_track_failed reason=unreadable");
            return null;
        }

        Metadata metadata = readMetadata(context, uri);
        String title = metadata.title;
        if (title == null || title.trim().isEmpty()) {
            String path = uri.getLastPathSegment();
            title = path == null ? "Song" : path.substring(path.lastIndexOf('/') + 1);
        }
        String artist = isBlank(metadata.artist) ? "Unknown artist" : metadata.artist;
        String album = isBlank(metadata.album) ? "Unknown album" : metadata.album;
        String albumArtist = isBlank(metadata.albumArtist) ? artist : metadata.albumArtist;
        String genre = isBlank(metadata.genre) ? "Unknown genre" : metadata.genre;

        FileIdentity file = readFileIdentity(context, uri);
        Track track = new Track(TrackIdentity.create(), uri.toString(), cleanText(title),
                cleanText(artist), cleanText(album), cleanText(albumArtist), cleanText(genre),
                metadata.year, metadata.trackNumber, metadata.discNumber, metadata.durationMs,
                file.size, file.lastModified, file.fingerprint, 0, 0,
                System.currentTimeMillis(), 0L, 0L);
        VoltuneLog.info("add_track_success duration_known=" + (track.durationMs > 0));
        return track;
    }

    public static Track refreshMetadata(Context context, Track oldTrack) {
        Track fresh = fromUri(context, Uri.parse(oldTrack.uri));
        if (fresh == null) {
            return oldTrack;
        }
        // A metadata repair must never rename an already imported track. Some providers
        // expose unstable or incorrectly decoded title tags between reads.
        String title = oldTrack.title;
        String artist = isBlank(fresh.artist) ? oldTrack.artist : fresh.artist;
        String album = isBlank(fresh.album) ? oldTrack.album : fresh.album;
        String genre = isBlank(fresh.genre) ? oldTrack.genre : fresh.genre;
        int durationMs = fresh.durationMs > 0 ? fresh.durationMs : oldTrack.durationMs;
        String albumArtist = isBlank(fresh.albumArtist)
                ? oldTrack.albumArtist : fresh.albumArtist;
        return new Track(oldTrack.trackId, oldTrack.uri, title, artist, album, albumArtist,
                genre, fresh.year > 0 ? fresh.year : oldTrack.year,
                fresh.trackNumber > 0 ? fresh.trackNumber : oldTrack.trackNumber,
                fresh.discNumber > 0 ? fresh.discNumber : oldTrack.discNumber,
                durationMs, fresh.fileSize, fresh.lastModified, fresh.fingerprint,
                oldTrack.playCount, oldTrack.skipCount, oldTrack.dateAdded,
                oldTrack.lastPlayedAt, oldTrack.lastCompletedAt);
    }

    public static boolean canOpenForRead(Context context, Uri uri) {
        AssetFileDescriptor descriptor = null;
        try {
            descriptor = context.getContentResolver().openAssetFileDescriptor(uri, "r");
            return descriptor != null;
        } catch (Exception e) {
            VoltuneLog.failure("read_check_failed", e);
            return false;
        } finally {
            closeQuietly(descriptor);
        }
    }

    public static void updateDuration(Context context, String uri, int durationMs) {
        if (durationMs <= 0 || uri == null || uri.isEmpty()) {
            return;
        }
        LibraryDatabase database = new LibraryDatabase(context);
        try {
            database.updateDuration(uri, durationMs);
        } finally {
            database.close();
        }
        VoltuneLog.info("duration_updated");
    }

    public static void updateLocation(Context context, Track track) {
        LibraryDatabase database = new LibraryDatabase(context);
        try {
            database.updateTrackLocation(track);
        } finally {
            database.close();
        }
    }

    public static void updateAvailability(Context context, String trackId, String reason) {
        LibraryDatabase database = new LibraryDatabase(context);
        try {
            database.updateAvailability(trackId, reason);
        } finally {
            database.close();
        }
    }

    public static void sort(List<Track> tracks) {
        Collections.sort(tracks, new Comparator<Track>() {
            @Override
            public int compare(Track left, Track right) {
                return left.title.toLowerCase(Locale.ROOT).compareTo(right.title.toLowerCase(Locale.ROOT));
            }
        });
    }

    private static Metadata readMetadata(Context context, Uri uri) {
        Metadata metadata = readMetadataDirect(context, uri);
        if (metadata.durationMs <= 0) {
            Metadata fallback = readMetadataWithFileDescriptor(context, uri);
            metadata.mergeMissing(fallback);
        }
        if (metadata.durationMs <= 0) {
            VoltuneLog.warning("duration_missing");
        }
        return metadata;
    }

    static FileIdentity readFileIdentity(Context context, Uri uri) {
        long size = -1L;
        long lastModified = 0L;
        android.database.Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri,
                    new String[]{OpenableColumns.SIZE,
                            DocumentsContract.Document.COLUMN_LAST_MODIFIED},
                    null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE);
                int modifiedColumn = cursor.getColumnIndex(
                        DocumentsContract.Document.COLUMN_LAST_MODIFIED);
                if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) {
                    size = cursor.getLong(sizeColumn);
                }
                if (modifiedColumn >= 0 && !cursor.isNull(modifiedColumn)) {
                    lastModified = cursor.getLong(modifiedColumn);
                }
            }
        } catch (Exception ignored) {
            // Some providers expose neither size nor modification time.
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return new FileIdentity(size, lastModified, fingerprint(context, uri));
    }

    private static String fingerprint(Context context, Uri uri) {
        InputStream input = null;
        try {
            input = context.getContentResolver().openInputStream(uri);
            if (input == null) {
                return "";
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int remaining = 64 * 1024;
            while (remaining > 0) {
                int count = input.read(buffer, 0, Math.min(buffer.length, remaining));
                if (count < 0) {
                    break;
                }
                digest.update(buffer, 0, count);
                remaining -= count;
            }
            StringBuilder result = new StringBuilder();
            for (byte value : digest.digest()) {
                result.append(String.format(Locale.ROOT, "%02x", value));
            }
            return result.toString();
        } catch (Exception error) {
            VoltuneLog.failure("fingerprint_failed", error);
            return "";
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static Metadata readMetadataDirect(Context context, Uri uri) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, uri);
            return Metadata.from(retriever);
        } catch (Throwable e) {
            VoltuneLog.failure("metadata_direct_failed", e);
            return new Metadata();
        } finally {
            releaseQuietly(retriever);
        }
    }

    private static Metadata readMetadataWithFileDescriptor(Context context, Uri uri) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        AssetFileDescriptor descriptor = null;
        try {
            descriptor = context.getContentResolver().openAssetFileDescriptor(uri, "r");
            if (descriptor == null) {
                return new Metadata();
            }
            long declaredLength = descriptor.getDeclaredLength();
            if (declaredLength >= 0) {
                retriever.setDataSource(descriptor.getFileDescriptor(), descriptor.getStartOffset(), declaredLength);
            } else {
                retriever.setDataSource(descriptor.getFileDescriptor());
            }
            return Metadata.from(retriever);
        } catch (Throwable e) {
            VoltuneLog.failure("metadata_fd_failed", e);
            return new Metadata();
        } finally {
            releaseQuietly(retriever);
            closeQuietly(descriptor);
        }
    }

    private static String cleanText(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.replace('\u0000', ' ').replace('\n', ' ').replace('\r', ' ').trim();
        if (cleaned.length() > MAX_TEXT_LENGTH) {
            cleaned = cleaned.substring(0, MAX_TEXT_LENGTH).trim();
        }
        return cleaned;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static int parseDurationMs(String raw) {
        if (isBlank(raw)) {
            return 0;
        }
        try {
            long value = Long.parseLong(raw.trim());
            return value <= 0L ? 0 : (int) Math.min(Integer.MAX_VALUE, value);
        } catch (Exception e) {
            VoltuneLog.failure("duration_parse_failed", e);
            return 0;
        }
    }

    private static void releaseQuietly(MediaMetadataRetriever retriever) {
        try {
            retriever.release();
        } catch (Exception ignored) {
        }
    }

    private static void closeQuietly(AssetFileDescriptor descriptor) {
        if (descriptor == null) {
            return;
        }
        try {
            descriptor.close();
        } catch (Exception ignored) {
        }
    }

    private static final class Metadata {
        String album;
        String albumArtist;
        String artist;
        int discNumber;
        int durationMs;
        String genre;
        String title;
        int trackNumber;
        int year;

        static Metadata from(MediaMetadataRetriever retriever) {
            Metadata metadata = new Metadata();
            metadata.title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            metadata.artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
            metadata.album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
            metadata.albumArtist = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST);
            metadata.genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE);
            metadata.year = MetadataValidator.year(retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_YEAR));
            metadata.trackNumber = MetadataValidator.trackNumber(retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER));
            metadata.discNumber = MetadataValidator.trackNumber(retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER));
            metadata.durationMs = parseDurationMs(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            return metadata;
        }

        void mergeMissing(Metadata fallback) {
            if (fallback == null) {
                return;
            }
            if (isBlank(this.title)) {
                this.title = fallback.title;
            }
            if (isBlank(this.artist)) {
                this.artist = fallback.artist;
            }
            if (isBlank(this.album)) {
                this.album = fallback.album;
            }
            if (isBlank(this.albumArtist)) {
                this.albumArtist = fallback.albumArtist;
            }
            if (isBlank(this.genre)) {
                this.genre = fallback.genre;
            }
            if (this.durationMs <= 0) {
                this.durationMs = fallback.durationMs;
            }
            if (this.year <= 0) {
                this.year = fallback.year;
            }
            if (this.trackNumber <= 0) {
                this.trackNumber = fallback.trackNumber;
            }
            if (this.discNumber <= 0) {
                this.discNumber = fallback.discNumber;
            }
        }
    }

    static final class FileIdentity {
        final long size;
        final long lastModified;
        final String fingerprint;

        FileIdentity(long size, long lastModified, String fingerprint) {
            this.size = size;
            this.lastModified = lastModified;
            this.fingerprint = fingerprint == null ? "" : fingerprint;
        }
    }
}
