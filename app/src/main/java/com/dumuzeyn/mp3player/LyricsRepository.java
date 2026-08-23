package com.dumuzeyn.mp3player;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.provider.DocumentsContract;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Reads bounded local sidecar and embedded lyrics through ContentResolver only. */
final class LyricsRepository implements AutoCloseable {
    interface Callback {
        void loaded(LrcDocument document);
    }

    private static final int MAX_LYRICS_BYTES = 1024 * 1024;
    private final Context context;
    private final android.os.Handler mainHandler;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean closed;
    private final Map<String, LrcDocument> cache = new LinkedHashMap<String, LrcDocument>(
            8, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, LrcDocument> eldest) {
            return size() > 12;
        }
    };

    LyricsRepository(Context context, android.os.Handler mainHandler) {
        this.context = context;
        this.mainHandler = mainHandler;
    }

    void load(Track track, Callback callback) {
        synchronized (cache) {
            LrcDocument cached = cache.get(track.uri);
            if (cached != null) {
                mainHandler.post(() -> callback.loaded(cached));
                return;
            }
        }
        try {
            executor.execute(() -> {
                LrcDocument result = find(track);
                synchronized (cache) {
                    cache.put(track.uri, result);
                }
                mainHandler.post(() -> {
                    if (!closed) {
                        callback.loaded(result);
                    }
                });
            });
        } catch (RejectedExecutionException ignored) {
            // Activity is already closing.
        }
    }

    @Override
    public void close() {
        closed = true;
        synchronized (cache) {
            cache.clear();
        }
        executor.shutdownNow();
    }

    private LrcDocument find(Track track) {
        for (Uri candidate : sidecars(track)) {
            String value = read(candidate);
            if (!value.trim().isEmpty()) {
                return new LrcParser().parse(value);
            }
        }
        try {
            String embedded = new EmbeddedLyricsReader().read(context, Uri.parse(track.uri));
            if (!embedded.trim().isEmpty()) {
                return new LrcParser().parse(embedded);
            }
        } catch (RuntimeException error) {
            VoltuneLog.failure("embedded_lyrics_uri_failed", error);
        }
        return new LrcDocument(new ArrayList<>(), "", false);
    }

    private List<Uri> sidecars(Track track) {
        ArrayList<Uri> result = new ArrayList<>();
        try {
            Uri source = Uri.parse(track.uri);
            if (!"content".equalsIgnoreCase(source.getScheme())
                    || !DocumentsContract.isDocumentUri(context, source)) {
                return result;
            }
            String id = DocumentsContract.getDocumentId(source);
            int slash = id.lastIndexOf('/');
            String parent = slash < 0 ? "" : id.substring(0, slash + 1);
            String filename = slash < 0 ? id : id.substring(slash + 1);
            int extension = filename.lastIndexOf('.');
            String base = extension > 0 ? filename.substring(0, extension) : filename;
            addCandidate(result, source, parent + base + ".lrc");
            addCandidate(result, source, parent + base + ".txt");
        } catch (RuntimeException ignored) {
            // A malformed or unsupported provider URI simply has no sidecar lyrics.
        }
        return result;
    }

    private void addCandidate(List<Uri> target, Uri source, String documentId) {
        try {
            Uri candidate = source.toString().contains("/tree/")
                    ? DocumentsContract.buildDocumentUriUsingTree(source, documentId)
                    : DocumentsContract.buildDocumentUri(source.getAuthority(), documentId);
            target.add(candidate);
        } catch (RuntimeException ignored) {
            // The provider does not support a sibling document URI.
        }
    }

    private String read(Uri uri) {
        ContentResolver resolver = context.getContentResolver();
        try (InputStream input = resolver.openInputStream(uri)) {
            if (input == null) {
                return "";
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int total = 0;
            int count;
            while ((count = input.read(buffer)) >= 0) {
                total += count;
                if (total > MAX_LYRICS_BYTES) {
                    return "";
                }
                output.write(buffer, 0, count);
            }
            String value = new String(output.toByteArray(), StandardCharsets.UTF_8);
            return value.startsWith("\ufeff") ? value.substring(1) : value;
        } catch (Exception ignored) {
            return "";
        }
    }
}
