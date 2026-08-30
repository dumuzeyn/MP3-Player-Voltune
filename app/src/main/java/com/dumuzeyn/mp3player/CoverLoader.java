package com.dumuzeyn.mp3player;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.media.MediaMetadataRetriever;
import android.content.Context;
import android.os.Handler;
import android.util.LruCache;
import android.widget.ImageView;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

final class CoverLoader {
    private static final int MAX_COVER_BYTES = 8 * 1024 * 1024;
    static final int THUMB_SIZE = 160;

    private final Context context;
    private final Handler mainHandler;
    private final LruCache<String, Bitmap> cache;
    private volatile ArtworkDiskCache diskCache;
    private final Map<String, ArrayList<PendingTarget>> pendingTargets = new LinkedHashMap<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private volatile boolean closed;

    CoverLoader(Context context, Handler mainHandler) {
        this.context = context;
        this.mainHandler = mainHandler;
        int maxKb = (int) Math.min(16L * 1024L,
                Math.max(6L * 1024L, Runtime.getRuntime().maxMemory() / 1024L / 16L));
        cache = new LruCache<String, Bitmap>(maxKb) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return Math.max(1, bitmap.getByteCount() / 1024);
            }
        };
    }

    void load(ImageView view, Track track, int fallbackColor) {
        load(view, track, fallbackColor, THUMB_SIZE);
    }

    void loadSmooth(ImageView view, Track track, int fallbackColor, int maxSize,
            int transitionDuration) {
        load(view, track, fallbackColor, maxSize, true, transitionDuration);
    }

    void loadCachedOnly(ImageView view, Track track, int fallbackColor, int maxSize) {
        String key = key(track, maxSize);
        view.setTag(key);
        Bitmap cached = cache.get(key);
        if (cached == null && maxSize != THUMB_SIZE) {
            cached = cache.get(key(track, THUMB_SIZE));
        }
        if (cached != null && !cached.isRecycled()) {
            view.setImageBitmap(cached);
        } else {
            view.setImageDrawable(null);
            view.setBackgroundColor(fallbackColor);
        }
    }

    void load(final ImageView view, final Track track, int fallbackColor, final int maxSize) {
        load(view, track, fallbackColor, maxSize, false, 0);
    }

    private void load(final ImageView view, final Track track, int fallbackColor,
            final int maxSize, boolean smooth, int transitionDuration) {
        if (closed) {
            return;
        }
        final String key = key(track, maxSize);
        if (key.equals(view.getTag()) && view.getDrawable() != null) {
            return;
        }
        view.setTag(key);
        Bitmap cached = cache.get(key);
        if (cached != null && !cached.isRecycled()) {
            applyBitmap(view, cached, fallbackColor, smooth, transitionDuration);
            return;
        }
        Bitmap thumbnail = maxSize == THUMB_SIZE ? null : cache.get(key(track, THUMB_SIZE));
        if (thumbnail != null && !thumbnail.isRecycled()) {
            applyBitmap(view, thumbnail, fallbackColor, smooth, transitionDuration);
        } else if (!smooth || view.getDrawable() == null) {
            view.setImageDrawable(null);
            view.setBackgroundColor(fallbackColor);
        }
        synchronized (pendingTargets) {
            ArrayList<PendingTarget> waiting = pendingTargets.get(key);
            if (waiting != null) {
                waiting.add(new PendingTarget(view, fallbackColor, smooth, transitionDuration));
                return;
            }
            waiting = new ArrayList<>();
            waiting.add(new PendingTarget(view, fallbackColor, smooth, transitionDuration));
            pendingTargets.put(key, waiting);
        }
        try {
            executor.execute(() -> {
                ArtworkDiskCache persistentCache = diskCache();
                Bitmap loaded = persistentCache.read(key);
                if (loaded == null) {
                    loaded = read(track, maxSize);
                    if (loaded != null) {
                        persistentCache.write(key, loaded);
                    }
                }
                final Bitmap bitmap = loaded;
                if (closed) {
                    return;
                }
                if (bitmap != null) {
                    cache.put(key, bitmap);
                    if (maxSize != THUMB_SIZE) {
                        cacheThumbnail(track, bitmap);
                    }
                }
                final ArrayList<PendingTarget> targets;
                synchronized (pendingTargets) {
                    targets = pendingTargets.remove(key);
                }
                mainHandler.post(() -> {
                    if (closed || targets == null) {
                        return;
                    }
                    for (PendingTarget pending : targets) {
                        ImageView target = pending.view.get();
                        if (target != null && key.equals(target.getTag())) {
                            if (bitmap == null) {
                                applyFallback(target, pending.fallbackColor,
                                        pending.smooth, pending.transitionDuration);
                            } else {
                                applyBitmap(target, bitmap, pending.fallbackColor,
                                        pending.smooth, pending.transitionDuration);
                            }
                        }
                    }
                });
            });
        } catch (RejectedExecutionException ignored) {
            synchronized (pendingTargets) {
                pendingTargets.remove(key);
            }
        }
    }

    void seedFromView(ImageView view, Track track) {
        if (view == null || track == null || !(view.getDrawable() instanceof BitmapDrawable)) {
            return;
        }
        Bitmap bitmap = ((BitmapDrawable) view.getDrawable()).getBitmap();
        if (bitmap != null && !bitmap.isRecycled()) {
            cache.put(key(track, THUMB_SIZE), bitmap);
        }
    }

    void clear(ImageView view, int fallbackColor) {
        if (view == null) {
            return;
        }
        view.setTag(null);
        view.setImageDrawable(null);
        view.setBackgroundColor(fallbackColor);
    }

    void trimMemory(int level) {
        if (level == android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL
                || level >= android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE) {
            cache.evictAll();
        } else if (level == android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW
                || level == android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE) {
            cache.trimToSize(Math.max(1, cache.maxSize() / 2));
        }
    }

    void close() {
        closed = true;
        executor.shutdownNow();
        synchronized (pendingTargets) {
            pendingTargets.clear();
        }
        cache.evictAll();
    }

    private void cacheThumbnail(Track track, Bitmap fullCover) {
        String key = key(track, THUMB_SIZE);
        if (cache.get(key) != null || fullCover == null || fullCover.isRecycled()) {
            return;
        }
        int width = fullCover.getWidth();
        int height = fullCover.getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        float scale = Math.min((float) THUMB_SIZE / width, (float) THUMB_SIZE / height);
        if (scale >= 1.0f) {
            cache.put(key, fullCover);
            return;
        }
        cache.put(key, Bitmap.createScaledBitmap(fullCover,
                Math.max(1, Math.round(width * scale)),
                Math.max(1, Math.round(height * scale)), true));
    }

    private void applyBitmap(ImageView view, Bitmap bitmap, int fallbackColor,
            boolean smooth, int transitionDuration) {
        if (!smooth || transitionDuration <= 0) {
            view.setImageBitmap(bitmap);
            return;
        }
        applyDrawable(view, new BitmapDrawable(view.getResources(), bitmap),
                fallbackColor, transitionDuration);
    }

    private void applyFallback(ImageView view, int fallbackColor, boolean smooth,
            int transitionDuration) {
        view.setBackgroundColor(fallbackColor);
        if (!smooth || transitionDuration <= 0 || view.getDrawable() == null) {
            view.setImageDrawable(null);
            return;
        }
        applyDrawable(view, new ColorDrawable(fallbackColor), fallbackColor,
                transitionDuration);
    }

    private void applyDrawable(ImageView view, Drawable next, int fallbackColor,
            int transitionDuration) {
        Drawable previous = view.getDrawable();
        if (previous == null) {
            previous = new ColorDrawable(fallbackColor);
        }
        TransitionDrawable transition = new TransitionDrawable(
                new Drawable[] {previous, next});
        transition.setCrossFadeEnabled(true);
        view.setImageDrawable(transition);
        transition.startTransition(transitionDuration);
    }

    private static final class PendingTarget {
        final WeakReference<ImageView> view;
        final int fallbackColor;
        final boolean smooth;
        final int transitionDuration;

        PendingTarget(ImageView view, int fallbackColor, boolean smooth,
                int transitionDuration) {
            this.view = new WeakReference<>(view);
            this.fallbackColor = fallbackColor;
            this.smooth = smooth;
            this.transitionDuration = transitionDuration;
        }
    }

    private Bitmap read(Track track, int maxSize) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, track.asUri());
            byte[] picture = retriever.getEmbeddedPicture();
            if (picture == null || picture.length > MAX_COVER_BYTES) {
                return null;
            }
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(picture, 0, picture.length, bounds);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sampleSize(bounds, maxSize);
            Bitmap bitmap = BitmapFactory.decodeByteArray(picture, 0, picture.length, options);
            if (bitmap == null || (bitmap.getWidth() <= maxSize && bitmap.getHeight() <= maxSize)) {
                return bitmap;
            }
            float scale = Math.min((float) maxSize / bitmap.getWidth(), (float) maxSize / bitmap.getHeight());
            Bitmap scaled = Bitmap.createScaledBitmap(bitmap,
                    Math.max(1, Math.round(bitmap.getWidth() * scale)),
                    Math.max(1, Math.round(bitmap.getHeight() * scale)), true);
            if (scaled != bitmap) {
                bitmap.recycle();
            }
            return scaled;
        } catch (RuntimeException ignored) {
            return null;
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {
            }
        }
    }

    private int sampleSize(BitmapFactory.Options options, int maxSize) {
        int sample = 1;
        while (options.outWidth / sample > maxSize * 2 || options.outHeight / sample > maxSize * 2) {
            sample *= 2;
        }
        return Math.max(1, sample);
    }

    private String key(Track track, int maxSize) {
        return track.trackId + "|" + track.uri + "|" + track.fileSize + "|"
                + track.lastModified + "|" + track.fingerprint + "#" + maxSize;
    }

    private ArtworkDiskCache diskCache() {
        ArtworkDiskCache current = diskCache;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (diskCache == null) {
                diskCache = new ArtworkDiskCache(context);
            }
            return diskCache;
        }
    }
}
