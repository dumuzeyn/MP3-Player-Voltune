package com.dumuzeyn.mp3player;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.media.MediaMetadata;
import android.media.MediaMetadataRetriever;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.LruCache;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

final class MediaNotificationController {
    private static final String CHANNEL_ID = "playback";
    private static final String DEBUG_TAG = "VoltuneDebug";

    private final Context context;
    private final MediaSession mediaSession;
    private final CoverReadyCallback coverReadyCallback;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService coverExecutor = Executors.newSingleThreadExecutor();
    private final Set<String> pendingCovers = new HashSet<>();
    private final LruCache<String, Bitmap> coverCache = new LruCache<String, Bitmap>(8 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return Math.max(1, value.getByteCount() / 1024);
        }
    };
    private String styledCoverKey = "";
    private Bitmap styledCover;
    private Bitmap appIcon;
    private String appIconKey = "";
    private boolean released;

    MediaNotificationController(Context context, MediaSession.Callback callback,
            CoverReadyCallback coverReadyCallback) {
        this.context = context.getApplicationContext();
        this.coverReadyCallback = coverReadyCallback;
        createChannel();
        mediaSession = new MediaSession(context, "MP3 Player Voltune");
        mediaSession.setCallback(callback);
        mediaSession.setActive(true);
    }

    Notification build(Track track, boolean hasPlayer, boolean playing, int position, int duration) {
        SharedPreferences uiPreferences = context.getSharedPreferences("mp3_player_ui", Context.MODE_PRIVATE);
        String theme = uiPreferences.getString("theme", "light");
        int customBackground = uiPreferences.getInt("customBg", 0xffffffff);
        boolean darkTheme = "dark".equals(theme)
                || ("custom".equals(theme) && ThemeManager.isDarkColor(customBackground));
        boolean circularCover = uiPreferences.getBoolean("circularCovers", false);
        int accentColor = "custom".equals(theme)
                ? uiPreferences.getInt("customFg", 0xff7c32e8)
                : darkTheme ? 0xffa35cff : 0xff7c32e8;
        int secondaryAccentColor = "custom".equals(theme)
                ? uiPreferences.getInt("customSecondaryAccent", 0xffffd000)
                : darkTheme ? 0xffffd438 : 0xffffd000;
        ComponentName launcher = LauncherComponents.forPalette(
                context, theme, darkTheme, accentColor, secondaryAccentColor);
        Intent launchIntent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(launcher);
        PendingIntent activity = PendingIntent.getActivity(context, 1, launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(context, CHANNEL_ID)
                : new Notification.Builder(context);
        int playPauseIcon = playing
                ? android.R.drawable.ic_media_pause
                : android.R.drawable.ic_media_play;
        Bitmap cover = notificationCover(track, circularCover);
        int iconBackground = "custom".equals(theme)
                ? customBackground : darkTheme ? 0xff111015 : 0xffffffff;
        Bitmap themedIcon = themedAppIcon(
                iconBackground, accentColor, secondaryAccentColor);
        builder.setSmallIcon(context.getResources().getIdentifier(
                        "ic_notification_music", "drawable", context.getPackageName()))
                .setContentTitle(track.title)
                .setContentText(track.artist)
                .setLargeIcon(cover)
                .setContentIntent(activity)
                .setOngoing(hasPlayer && playing)
                .setCategory(Notification.CATEGORY_TRANSPORT)
                .setPriority(Notification.PRIORITY_LOW)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setColor(accentColor)
                .addAction(android.R.drawable.ic_media_previous, "Назад",
                        serviceIntent(PlayerService.ACTION_PREV, 2))
                .addAction(playPauseIcon, playing ? "Пауза" : "Играть",
                        serviceIntent(PlayerService.ACTION_TOGGLE, 3))
                .addAction(android.R.drawable.ic_media_next, "Дальше",
                        serviceIntent(PlayerService.ACTION_NEXT, 4));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setColorized(true);
        }
        mediaSession.setSessionActivity(activity);
        updateMediaSession(track, cover, themedIcon, playing, position, duration);
        builder.setStyle(new Notification.MediaStyle()
                .setMediaSession(mediaSession.getSessionToken())
                .setShowActionsInCompactView(0, 1, 2));
        return builder.build();
    }

    void release() {
        released = true;
        coverExecutor.shutdownNow();
        mainHandler.removeCallbacksAndMessages(null);
        pendingCovers.clear();
        mediaSession.release();
        coverCache.evictAll();
        clearStyledCover();
        appIcon = null;
        appIconKey = "";
    }

    private void updateMediaSession(Track track, Bitmap cover, Bitmap themedIcon,
            boolean playing, int position, int duration) {
        int state = playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED;
        MediaMetadata.Builder metadata = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, track.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, track.artist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, track.album)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, duration);
        if (cover != null) {
            metadata.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, cover);
            metadata.putBitmap(MediaMetadata.METADATA_KEY_ART, cover);
        }
        if (themedIcon != null) {
            metadata.putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, themedIcon);
        }
        mediaSession.setMetadata(metadata.build());
        mediaSession.setPlaybackState(new PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY
                        | PlaybackState.ACTION_PAUSE
                        | PlaybackState.ACTION_PLAY_PAUSE
                        | PlaybackState.ACTION_SKIP_TO_NEXT
                        | PlaybackState.ACTION_SKIP_TO_PREVIOUS
                        | PlaybackState.ACTION_SEEK_TO
                        | PlaybackState.ACTION_STOP)
                .setState(state, position, 1.0f)
                .build());
    }

    private Bitmap notificationCover(Track track, boolean circular) {
        Bitmap rawCover = coverFor(track);
        if (!circular || rawCover == null) {
            return rawCover;
        }
        String key = track.uri + "#circle";
        if (key.equals(styledCoverKey) && styledCover != null && !styledCover.isRecycled()) {
            return styledCover;
        }
        clearStyledCover();
        styledCoverKey = key;
        styledCover = circularBitmap(rawCover);
        return styledCover;
    }

    private Bitmap coverFor(Track track) {
        if (track == null || track.uri == null || track.uri.isEmpty()) {
            return null;
        }
        Bitmap cached = coverCache.get(track.uri);
        if (cached != null) {
            return cached;
        }
        scheduleCoverRead(track);
        return null;
    }

    private void scheduleCoverRead(Track track) {
        final String uri = track.uri;
        if (released || !pendingCovers.add(uri)) {
            return;
        }
        try {
            coverExecutor.execute(() -> {
                final Bitmap cover = readCover(track);
                mainHandler.post(() -> {
                    if (released) {
                        return;
                    }
                    pendingCovers.remove(uri);
                    if (cover != null) {
                        coverCache.put(uri, cover);
                    }
                    if (coverReadyCallback != null) {
                        coverReadyCallback.onCoverReady(uri);
                    }
                });
            });
        } catch (RejectedExecutionException ignored) {
            pendingCovers.remove(uri);
        }
    }

    private Bitmap readCover(Track track) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, track.asUri());
            byte[] data = retriever.getEmbeddedPicture();
            if (data == null || data.length == 0) {
                return null;
            }
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = coverSampleSize(bounds, 512);
            return BitmapFactory.decodeByteArray(data, 0, data.length, options);
        } catch (Exception error) {
            Log.w(DEBUG_TAG, "service_cover_failed uri=" + track.uri
                    + " error=" + error.getMessage());
            return null;
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {
            }
        }
    }

    private Bitmap circularBitmap(Bitmap source) {
        if (source == null || source.isRecycled()) {
            return source;
        }
        int size = Math.max(1, Math.min(source.getWidth(), source.getHeight()));
        Bitmap output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        BitmapShader shader = new BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        float scale = Math.max((float) size / source.getWidth(), (float) size / source.getHeight());
        Matrix matrix = new Matrix();
        matrix.setScale(scale, scale);
        matrix.postTranslate((size - source.getWidth() * scale) * 0.5f,
                (size - source.getHeight() * scale) * 0.5f);
        shader.setLocalMatrix(matrix);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        paint.setShader(shader);
        canvas.drawCircle(size * 0.5f, size * 0.5f, size * 0.5f, paint);
        return output;
    }

    private Bitmap themedAppIcon(int backgroundColor,
            int primaryColor, int secondaryColor) {
        String key = backgroundColor + ":" + primaryColor + ":" + secondaryColor;
        if (key.equals(appIconKey) && appIcon != null && !appIcon.isRecycled()) {
            return appIcon;
        }
        try {
            appIcon = AppIconRenderer.renderTile(
                    context, backgroundColor, primaryColor, secondaryColor, 128);
            appIconKey = key;
            return appIcon;
        } catch (RuntimeException error) {
            return null;
        }
    }

    private int coverSampleSize(BitmapFactory.Options options, int maxSize) {
        int sampleSize = 1;
        int width = options.outWidth;
        int height = options.outHeight;
        while (width / sampleSize > maxSize * 2 || height / sampleSize > maxSize * 2) {
            sampleSize *= 2;
        }
        return Math.max(1, sampleSize);
    }

    private PendingIntent serviceIntent(String action, int requestCode) {
        Intent intent = new Intent(context, PlayerService.class);
        intent.setAction(action);
        return PendingIntent.getService(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Музыка", NotificationManager.IMPORTANCE_LOW);
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(NotificationManager.class);
        if (notificationManager != null) {
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void clearStyledCover() {
        styledCover = null;
        styledCoverKey = "";
    }

    interface CoverReadyCallback {
        void onCoverReady(String uri);
    }
}
