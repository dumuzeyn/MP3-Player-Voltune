package com.dumuzeyn.mp3player;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.graphics.Movie;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.util.LruCache;
import android.view.View;
import android.widget.ImageView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Displays only decoded raster pixels; image metadata, links and scripts are never executed. */
@android.annotation.SuppressLint("ViewConstructor")
final class BackgroundMediaView extends ImageView {
    private static final int MAX_DECODE_SIZE = 2048;
    private static final ExecutorService DECODER = Executors.newFixedThreadPool(2);
    private static final LruCache<String, Bitmap> BITMAP_CACHE =
            new LruCache<String, Bitmap>(12 * 1024) {
                @Override
                protected int sizeOf(String key, Bitmap bitmap) {
                    return Math.max(1, bitmap.getByteCount() / 1024);
                }
            };

    private final android.content.Context context;
    private final Handler mainHandler;
    private final String mediaUri;
    private final int blurPercent;
    private Movie legacyMovie;
    private long movieStartedAt;
    private boolean uiActive = true;

    BackgroundMediaView(android.content.Context context, String mediaUri, int blurPercent,
            int fallbackColor) {
        super(context);
        this.context = context.getApplicationContext();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.mediaUri = mediaUri;
        this.blurPercent = Math.max(0, Math.min(100, blurPercent));
        setScaleType(ScaleType.CENTER_CROP);
        setBackgroundColor(fallbackColor);
        load();
    }

    private void load() {
        String key = mediaUri + "#" + blurPercent + "#" + Build.VERSION.SDK_INT;
        Bitmap cached = BITMAP_CACHE.get(key);
        if (cached != null && !cached.isRecycled()) {
            setImageBitmap(cached);
            applyModernBlur();
            return;
        }
        WeakReference<BackgroundMediaView> reference = new WeakReference<>(this);
        DECODER.execute(() -> {
            try {
                DecodedMedia decoded = decode(key);
                BackgroundMediaView target = reference.get();
                if (target == null || decoded == null) {
                    return;
                }
                mainHandler.post(() -> {
                    BackgroundMediaView liveTarget = reference.get();
                    if (liveTarget == null || !liveTarget.mediaUri.equals(mediaUri)) {
                        return;
                    }
                    liveTarget.display(decoded);
                });
            } catch (Exception ignored) {
                // The validated URI may have been revoked or removed after it was selected.
            }
        });
    }

    private DecodedMedia decode(String cacheKey) throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Api28Decoder.decode(context, mediaUri, blurPercent, cacheKey);
        }
        try (InputStream input = context.getContentResolver()
                .openInputStream(android.net.Uri.parse(mediaUri))) {
            byte[] data = readBounded(input);
            if (blurPercent == 0 && isGif(data)) {
                Movie movie = Movie.decodeByteArray(data, 0, data.length);
                if (movie != null) {
                    return DecodedMedia.movie(movie);
                }
            }
            Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
            if (bitmap == null) {
                return null;
            }
            bitmap = scaleDown(bitmap);
            bitmap = blurLegacy(context, bitmap, blurPercent);
            BITMAP_CACHE.put(cacheKey, bitmap);
            return DecodedMedia.bitmap(bitmap);
        }
    }

    private void display(DecodedMedia decoded) {
        if (decoded.movie != null) {
            legacyMovie = decoded.movie;
            movieStartedAt = SystemClock.uptimeMillis();
            invalidate();
            return;
        }
        setImageDrawable(decoded.drawable);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Api28Decoder.setAnimatedRunning(decoded.drawable, uiActive);
        }
        applyModernBlur();
    }

    void setUiActive(boolean active) {
        uiActive = active;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Api28Decoder.setAnimatedRunning(getDrawable(), active);
        }
        if (active) {
            movieStartedAt = SystemClock.uptimeMillis();
            invalidate();
        }
    }

    @Override
    protected void onDraw(android.graphics.Canvas canvas) {
        if (legacyMovie == null || !uiActive) {
            super.onDraw(canvas);
            return;
        }
        int duration = Math.max(100, legacyMovie.duration());
        legacyMovie.setTime((int) ((SystemClock.uptimeMillis() - movieStartedAt) % duration));
        float scale = Math.max((float) getWidth() / legacyMovie.width(),
                (float) getHeight() / legacyMovie.height());
        float left = (getWidth() - legacyMovie.width() * scale) * 0.5f;
        float top = (getHeight() - legacyMovie.height() * scale) * 0.5f;
        canvas.save();
        canvas.translate(left, top);
        canvas.scale(scale, scale);
        legacyMovie.draw(canvas, 0, 0);
        canvas.restore();
        postInvalidateDelayed(32L);
    }

    private void applyModernBlur() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            float radius = blurPercent == 0 ? 0.0f : Math.max(1.0f, blurPercent * 0.35f);
            Api31Blur.apply(this, radius);
        }
    }

    private static Bitmap blurLegacy(android.content.Context context, Bitmap source, int amount) {
        if (amount <= 0 || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return source;
        }
        Bitmap mutable = source.copy(Bitmap.Config.ARGB_8888, true);
        if (mutable == null) {
            return source;
        }
        RenderScript renderScript = null;
        Allocation input = null;
        Allocation output = null;
        ScriptIntrinsicBlur blur = null;
        try {
            renderScript = RenderScript.create(context.getApplicationContext());
            input = Allocation.createFromBitmap(renderScript, mutable);
            output = Allocation.createTyped(renderScript, input.getType());
            blur = ScriptIntrinsicBlur.create(renderScript, Element.U8_4(renderScript));
            blur.setRadius(Math.max(0.1f, Math.min(25.0f, amount / 4.0f)));
            blur.setInput(input);
            blur.forEach(output);
            output.copyTo(mutable);
            if (mutable != source) {
                source.recycle();
            }
            return mutable;
        } catch (RuntimeException ignored) {
            mutable.recycle();
            return source;
        } finally {
            if (blur != null) blur.destroy();
            if (input != null) input.destroy();
            if (output != null) output.destroy();
            if (renderScript != null) renderScript.destroy();
        }
    }

    private static Bitmap scaleDown(Bitmap bitmap) {
        int largest = Math.max(bitmap.getWidth(), bitmap.getHeight());
        if (largest <= MAX_DECODE_SIZE) {
            return bitmap;
        }
        float scale = (float) MAX_DECODE_SIZE / largest;
        Bitmap scaled = Bitmap.createScaledBitmap(bitmap,
                Math.max(1, Math.round(bitmap.getWidth() * scale)),
                Math.max(1, Math.round(bitmap.getHeight() * scale)), true);
        if (scaled != bitmap) bitmap.recycle();
        return scaled;
    }

    private static byte[] readBounded(InputStream input) throws Exception {
        if (input == null) throw new IllegalStateException("Image is unavailable");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[16 * 1024];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            total += read;
            if (total > 32 * 1024 * 1024) throw new IllegalStateException("Image is too large");
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static boolean isGif(byte[] data) {
        return data.length >= 6 && data[0] == 'G' && data[1] == 'I' && data[2] == 'F';
    }

    private static final class DecodedMedia {
        final Drawable drawable;
        final Movie movie;

        private DecodedMedia(Drawable drawable, Movie movie) {
            this.drawable = drawable;
            this.movie = movie;
        }

        static DecodedMedia bitmap(Bitmap bitmap) {
            return new DecodedMedia(new android.graphics.drawable.BitmapDrawable(null, bitmap), null);
        }

        static DecodedMedia drawable(Drawable drawable) {
            return new DecodedMedia(drawable, null);
        }

        static DecodedMedia movie(Movie movie) {
            return new DecodedMedia(null, movie);
        }
    }

    @TargetApi(Build.VERSION_CODES.P)
    private static final class Api28Decoder {
        static DecodedMedia decode(android.content.Context context, String mediaUri,
                int blurPercent,
                String cacheKey) throws Exception {
            ImageDecoder.Source source = ImageDecoder.createSource(context.getContentResolver(),
                    android.net.Uri.parse(mediaUri));
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && blurPercent > 0) {
                Bitmap bitmap = ImageDecoder.decodeBitmap(source, (decoder, info, src) -> {
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                    setTargetSize(decoder, info);
                });
                bitmap = scaleDown(bitmap);
                bitmap = blurLegacy(context, bitmap, blurPercent);
                BITMAP_CACHE.put(cacheKey, bitmap);
                return DecodedMedia.bitmap(bitmap);
            }
            Drawable drawable = ImageDecoder.decodeDrawable(source,
                    (decoder, info, src) -> setTargetSize(decoder, info));
            return DecodedMedia.drawable(drawable);
        }

        private static void setTargetSize(ImageDecoder decoder, ImageDecoder.ImageInfo info) {
            int width = info.getSize().getWidth();
            int height = info.getSize().getHeight();
            int largest = Math.max(width, height);
            if (largest > MAX_DECODE_SIZE) {
                float scale = (float) MAX_DECODE_SIZE / largest;
                decoder.setTargetSize(Math.max(1, Math.round(width * scale)),
                        Math.max(1, Math.round(height * scale)));
            }
        }

        static void setAnimatedRunning(Drawable drawable, boolean running) {
            if (drawable instanceof android.graphics.drawable.AnimatedImageDrawable) {
                android.graphics.drawable.AnimatedImageDrawable animated =
                        (android.graphics.drawable.AnimatedImageDrawable) drawable;
                if (running) animated.start(); else animated.stop();
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.S)
    private static final class Api31Blur {
        static void apply(View view, float radius) {
            view.setRenderEffect(radius == 0.0f ? null
                    : android.graphics.RenderEffect.createBlurEffect(radius, radius,
                    android.graphics.Shader.TileMode.CLAMP));
        }
    }

}
