package com.dumuzeyn.mp3player;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.util.LruCache;
import androidx.media3.common.util.BitmapLoader;
import androidx.media3.common.util.UnstableApi;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@UnstableApi
final class MediaArtworkProvider implements BitmapLoader {
    private static final int MAX_ARTWORK_SIZE = 768;

    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final LruCache<String, Bitmap> cache = new LruCache<>(8);

    MediaArtworkProvider(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public ListenableFuture<Bitmap> decodeBitmap(byte[] data) {
        return submit(() -> decode(data));
    }

    @Override
    public ListenableFuture<Bitmap> loadBitmap(Uri uri) {
        Bitmap cached = cache.get(uri.toString());
        if (cached != null) {
            SettableFuture<Bitmap> ready = SettableFuture.create();
            ready.set(cached);
            return ready;
        }
        return submit(() -> {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            try {
                retriever.setDataSource(context, uri);
                Bitmap bitmap = decode(retriever.getEmbeddedPicture());
                cache.put(uri.toString(), bitmap);
                return bitmap;
            } finally {
                retriever.release();
            }
        });
    }

    @Override
    public boolean supportsMimeType(String mimeType) {
        return mimeType != null && mimeType.startsWith("image/");
    }

    void close() {
        executor.shutdownNow();
        cache.evictAll();
    }

    private Bitmap decode(byte[] data) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Artwork is unavailable");
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
        int sample = 1;
        while (bounds.outWidth / sample > MAX_ARTWORK_SIZE * 2
                || bounds.outHeight / sample > MAX_ARTWORK_SIZE * 2) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = Math.max(1, sample);
        Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);
        if (bitmap == null) {
            throw new IllegalArgumentException("Artwork cannot be decoded");
        }
        return bitmap;
    }

    private ListenableFuture<Bitmap> submit(BitmapTask task) {
        SettableFuture<Bitmap> future = SettableFuture.create();
        executor.execute(() -> {
            try {
                future.set(task.run());
            } catch (Exception error) {
                future.setException(error);
            }
        });
        return future;
    }

    private interface BitmapTask {
        Bitmap run() throws Exception;
    }
}
