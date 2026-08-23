package com.dumuzeyn.mp3player;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import com.dumuzeyn.mp3player.ui.permissions.DeviceAudioPermissionController;

final class AudioImportController {
    private static final int PICK_AUDIO = 2001;
    private static final int PICK_AUDIO_FOLDER = 2002;
    private static final int MAX_FOLDER_IMPORT = 3000;
    private static final long MAX_AUDIO_BYTES = 220L * 1024L * 1024L;

    private final MainActivityCore host;
    private final ExecutorService importExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean automaticScanStarted = new AtomicBoolean();
    private volatile boolean closed;
    private volatile boolean libraryReady;

    AudioImportController(MainActivityCore host) {
        this.host = host;
    }

    void openFiles() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        host.startActivityForResult(Intent.createChooser(intent,
                host.tr("Choose music", "Выберите музыку")), PICK_AUDIO);
    }

    void openFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        host.startActivityForResult(Intent.createChooser(intent,
                host.tr("Choose music folder", "Выберите папку с музыкой")), PICK_AUDIO_FOLDER);
    }

    boolean handleActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK || data == null) {
            return false;
        }
        final ArrayList<Uri> selectedUris = new ArrayList<>();
        final Uri selectedTree;
        if (requestCode == PICK_AUDIO) {
            if (data.getClipData() != null) {
                for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                    selectedUris.add(data.getClipData().getItemAt(i).getUri());
                }
            } else if (data.getData() != null) {
                selectedUris.add(data.getData());
            }
            selectedTree = null;
        } else if (requestCode == PICK_AUDIO_FOLDER && data.getData() != null) {
            selectedTree = data.getData();
        } else {
            return false;
        }
        final int permissionFlags = data.getFlags();
        final HashSet<String> knownUris = new HashSet<>();
        final ArrayList<Track> existingTracks = new ArrayList<>(host.libraryState.tracks);
        for (Track track : host.libraryState.tracks) {
            knownUris.add(track.uri);
        }
        try {
            importExecutor.execute(() -> processImport(
                    selectedUris, selectedTree, permissionFlags, knownUris, existingTracks));
        } catch (RejectedExecutionException ignored) {
            return false;
        }
        return true;
    }

    void close() {
        closed = true;
        importExecutor.shutdown();
    }

    void onLibraryReady() {
        libraryReady = true;
        autoImportDeviceMusicIfAllowed();
    }

    void onAudioPermissionChanged() {
        autoImportDeviceMusicIfAllowed();
    }

    private void autoImportDeviceMusicIfAllowed() {
        if (closed || !libraryReady
                || host.getIntent().getIntExtra(BenchmarkLibrarySeeder.EXTRA_TRACK_COUNT, 0) > 0
                || !DeviceAudioPermissionController.hasPermission(host)
                || !automaticScanStarted.compareAndSet(false, true)) {
            return;
        }
        HashSet<String> knownUris = new HashSet<>();
        for (Track track : host.libraryState.tracks) {
            knownUris.add(track.uri);
        }
        try {
            importExecutor.execute(() -> {
                LibraryImportStore store = new LibraryImportStore(host);
                ArrayList<Track> imported;
                try {
                    LibrarySourceStore sources = new LibrarySourceStore(host);
                    ExcludedTrackIndex exclusions;
                    try {
                        exclusions = new ExcludedTrackIndex(sources.exclusions(null));
                    } finally {
                        sources.close();
                    }
                    imported = store.commitStandalone(
                            DeviceMusicScanner.scan(host, knownUris, exclusions), false);
                } finally {
                    store.close();
                }
                publishImportedTracks(imported);
            });
        } catch (RejectedExecutionException ignored) {
            // Activity is already closing.
        }
    }

    private void processImport(ArrayList<Uri> selectedUris, Uri treeUri, int permissionFlags,
            HashSet<String> knownUris, ArrayList<Track> existingTracks) {
        ArrayList<Track> imported = new ArrayList<>();
        if (treeUri != null) {
            imported.addAll(importFolder(treeUri, permissionFlags, knownUris, existingTracks));
        } else {
            for (Uri uri : selectedUris) {
                Track track = readTrack(uri, permissionFlags, true, knownUris, existingTracks);
                if (track != null) {
                    imported.add(track);
                }
            }
            LibraryImportStore store = new LibraryImportStore(host);
            try {
                imported = store.commitStandalone(imported, true);
            } finally {
                store.close();
            }
        }
        publishImportedTracks(imported);
    }

    private void publishImportedTracks(ArrayList<Track> imported) {
        if (imported.isEmpty() || closed) {
            return;
        }
        host.uiHandler.post(() -> {
            if (closed) {
                return;
            }
            for (Track track : imported) {
                int existingIndex = indexOfTrackId(host.libraryState.tracks, track.trackId);
                if (existingIndex >= 0) {
                    host.libraryState.tracks.set(existingIndex, track);
                } else if (host.findTrack(track.uri) == null) {
                    host.libraryState.tracks.add(track);
                }
            }
            TrackStore.sort(host.libraryState.tracks);
            host.libraryRepository.reindex();
            host.librarySnapshotApplier.rebuildDerivedAndRender();
        });
    }

    private ArrayList<Track> importFolder(Uri treeUri, int flags, HashSet<String> knownUris,
            ArrayList<Track> existingTracks) {
        ArrayList<Track> importedTracks = new ArrayList<>();
        if (treeUri == null || !"content".equalsIgnoreCase(treeUri.getScheme())) {
            return importedTracks;
        }
        int takeFlags = flags & Intent.FLAG_GRANT_READ_URI_PERMISSION;
        try {
            host.getContentResolver().takePersistableUriPermission(treeUri, takeFlags);
        } catch (RuntimeException error) {
            VoltuneLog.failure("persist_folder_permission_failed", error);
        }
        LibrarySource source = PersistedFolderStore.remember(host, treeUri,
                queryDisplayName(treeUri), true);
        if (source == null) {
            return importedTracks;
        }
        LibraryImportStore store = new LibraryImportStore(host);
        SourceScanSession session;
        try {
            session = store.session(source);
        } finally {
            store.close();
        }
        ArrayList<DiscoveredTrack> discovered = new ArrayList<>();
        HashMap<String, Track> existingByUri = indexByUri(existingTracks);
        int[] imported = {0};
        try {
            scanDocumentTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri), imported,
                    knownUris, discovered, existingTracks, existingByUri, session);
        } catch (RuntimeException error) {
            VoltuneLog.failure("folder_import_failed", error);
        }
        store = new LibraryImportStore(host);
        try {
            importedTracks.addAll(store.commitSource(session, discovered));
        } finally {
            store.close();
        }
        return importedTracks;
    }

    private void scanDocumentTree(Uri treeUri, String documentId, int[] imported,
            HashSet<String> knownUris, ArrayList<DiscoveredTrack> discovered,
            ArrayList<Track> existingTracks, Map<String, Track> existingByUri,
            SourceScanSession session) {
        if (closed || imported[0] >= MAX_FOLDER_IMPORT) {
            return;
        }
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId);
        Cursor cursor = null;
        try {
            cursor = host.getContentResolver().query(childrenUri, new String[]{
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME
            }, null, null, null);
            while (cursor != null && cursor.moveToNext() && imported[0] < MAX_FOLDER_IMPORT) {
                String childId = cursor.getString(0);
                String mimeType = cursor.getString(1);
                String displayName = cursor.getString(2);
                Uri childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId);
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType)) {
                    scanDocumentTree(treeUri, childId, imported, knownUris, discovered,
                            existingTracks, existingByUri, session);
                } else if (isAudioDocument(mimeType, displayName)) {
                    String identity = TrackOrigin.identity(session.source.sourceId, childId);
                    if (session.exclusions.containsIdentity(identity)) {
                        continue;
                    }
                    Track existing = existingByUri.get(childUri.toString());
                    if (existing != null) {
                        discovered.add(new DiscoveredTrack(existing, session.source, childId));
                        imported[0]++;
                        continue;
                    }
                    Track track = readTrack(childUri, 0, false, knownUris, existingTracks);
                    if (track != null && !session.exclusions.contains(identity, track)) {
                        discovered.add(new DiscoveredTrack(track, session.source, childId));
                        imported[0]++;
                    }
                }
            }
        } catch (RuntimeException error) {
            VoltuneLog.failure("folder_scan_failed", error);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private boolean isAudioDocument(String mimeType, String displayName) {
        if (mimeType != null && mimeType.toLowerCase(Locale.ROOT).startsWith("audio/")) {
            return true;
        }
        return hasAudioExtension(displayName);
    }

    private Track readTrack(Uri uri, int permissionFlags, boolean persistPermission,
            Set<String> knownUris, List<Track> existingTracks) {
        if (!isSafeAudioUri(uri)) {
            VoltuneLog.warning("add_track_rejected reason=unsafe_uri");
            return null;
        }
        if (persistPermission) {
            int takeFlags = permissionFlags & Intent.FLAG_GRANT_READ_URI_PERMISSION;
            if (takeFlags == 0) {
                takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
            }
            try {
                host.getContentResolver().takePersistableUriPermission(uri, takeFlags);
            } catch (RuntimeException error) {
                VoltuneLog.failure("persist_permission_failed", error);
            }
        }
        String value = uri.toString();
        if (knownUris.contains(value)) {
            return null;
        }
        try {
            boolean canOpen = TrackStore.canOpenForRead(host, uri);
            VoltuneLog.info("add_track_candidate readable=" + canOpen);
            if (!canOpen) {
                return null;
            }
            Track track = TrackStore.fromUri(host, uri);
            if (track != null) {
                List<Track> matches = TrackRelinker.candidates(existingTracks, track);
                if (matches.size() == 1
                        && !TrackStore.canOpenForRead(host, matches.get(0).asUri())) {
                    Track old = matches.get(0);
                    track = new Track(old.trackId, track.uri, track.title, track.artist,
                            track.album, track.genre, track.durationMs, track.fileSize,
                            track.lastModified, track.fingerprint);
                } else if (matches.size() > 1) {
                    VoltuneLog.warning("relink_requires_confirmation candidates="
                            + matches.size());
                    Track ambiguous = track;
                    host.uiHandler.post(() -> confirmAmbiguousImport(ambiguous,
                            matches.size()));
                    return null;
                }
                knownUris.add(value);
                VoltuneLog.info("add_track_saved duration_known=" + (track.durationMs > 0));
            }
            return track;
        } catch (RuntimeException error) {
            VoltuneLog.failure("add_track_failed", error);
            return null;
        }
    }

    void rescanPersistedFolders() {
        ArrayList<LibrarySource> sources = new ArrayList<>();
        for (LibrarySource source : PersistedFolderStore.list(host)) {
            if (PersistedFolderStore.hasReadPermission(host, source.asUri())) {
                sources.add(source);
            }
        }
        if (sources.isEmpty()) {
            openFolder();
            return;
        }
        HashSet<String> knownUris = new HashSet<>();
        ArrayList<Track> existing = new ArrayList<>(host.libraryState.tracks);
        for (Track track : existing) {
            knownUris.add(track.uri);
        }
        importExecutor.execute(() -> {
            for (LibrarySource source : sources) {
                processRescan(source, knownUris, existing);
            }
        });
    }

    private void processRescan(LibrarySource source, HashSet<String> knownUris,
            ArrayList<Track> existingTracks) {
        LibraryImportStore store = new LibraryImportStore(host);
        SourceScanSession session;
        try {
            session = store.session(source);
        } finally {
            store.close();
        }
        ArrayList<DiscoveredTrack> discovered = new ArrayList<>();
        HashMap<String, Track> existingByUri = indexByUri(existingTracks);
        int[] imported = {0};
        try {
            scanDocumentTree(source.asUri(),
                    DocumentsContract.getTreeDocumentId(source.asUri()), imported,
                    knownUris, discovered, existingTracks, existingByUri, session);
        } catch (RuntimeException error) {
            VoltuneLog.failure("folder_rescan_failed", error);
        }
        store = new LibraryImportStore(host);
        ArrayList<Track> accepted;
        try {
            accepted = store.commitSource(session, discovered);
        } finally {
            store.close();
        }
        publishImportedTracks(accepted);
    }

    private static int indexOfTrackId(List<Track> tracks, String trackId) {
        for (int index = 0; index < tracks.size(); index++) {
            if (tracks.get(index).trackId.equals(trackId)) {
                return index;
            }
        }
        return -1;
    }

    private static HashMap<String, Track> indexByUri(List<Track> tracks) {
        HashMap<String, Track> result = new HashMap<>();
        for (Track track : tracks) {
            result.put(track.uri, track);
        }
        return result;
    }

    private void confirmAmbiguousImport(Track track, int candidateCount) {
        if (closed) {
            return;
        }
        host.showActionPanel(
                host.tr("Possible moved file", "Возможно, файл был перемещён"),
                host.tr("Voltune found several similar unavailable records. Import this file "
                                + "as a separate track? Candidates: ",
                        "Voltune нашёл несколько похожих недоступных записей. Импортировать "
                                + "этот файл как отдельный трек? Совпадений: ")
                        + candidateCount,
                host.tr("Cancel", "Отмена"),
                host.tr("Import separately", "Импортировать отдельно"),
                true,
                () -> {
                    LibraryImportStore store = new LibraryImportStore(host);
                    try {
                        store.commitStandalone(java.util.Collections.singletonList(track), true);
                    } finally {
                        store.close();
                    }
                    if (host.findTrack(track.uri) == null) {
                        host.libraryState.tracks.add(track);
                        TrackStore.sort(host.libraryState.tracks);
                    }
                    host.libraryRepository.reindex();
                    host.librarySnapshotApplier.rebuildDerivedAndRender();
                });
    }

    private boolean isSafeAudioUri(Uri uri) {
        if (uri == null || !"content".equalsIgnoreCase(uri.getScheme())) {
            return false;
        }
        try {
            String type = host.getContentResolver().getType(uri);
            boolean extensionMatches = hasAudioExtension(queryDisplayName(uri));
            if (type != null && !type.toLowerCase(Locale.ROOT).startsWith("audio/") && !extensionMatches) {
                return false;
            }
            if (type == null && !extensionMatches) {
                return false;
            }
            long size = querySize(uri);
            return size <= 0L || size <= MAX_AUDIO_BYTES;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean hasAudioExtension(String displayName) {
        if (displayName == null) {
            return false;
        }
        String lower = displayName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".mp3") || lower.endsWith(".m4a") || lower.endsWith(".aac")
                || lower.endsWith(".wav") || lower.endsWith(".ogg") || lower.endsWith(".flac");
    }

    private String queryDisplayName(Uri uri) {
        Cursor cursor = null;
        try {
            cursor = host.getContentResolver().query(uri,
                    new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            return cursor != null && cursor.moveToFirst() ? cursor.getString(0) : uri.getLastPathSegment();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private long querySize(Uri uri) {
        Cursor cursor = null;
        try {
            cursor = host.getContentResolver().query(uri,
                    new String[]{OpenableColumns.SIZE}, null, null, null);
            return cursor != null && cursor.moveToFirst() ? cursor.getLong(0) : -1L;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }
}
