package com.dumuzeyn.mp3player;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.RemoteViews;
import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public final class PlayerWidgetProvider extends AppWidgetProvider {
    private static final String ACTION_PREFIX = "com.dumuzeyn.mp3player.widget.";
    private static final String ACTION_PREVIOUS = ACTION_PREFIX + "PREVIOUS";
    private static final String ACTION_TOGGLE = ACTION_PREFIX + "TOGGLE";
    private static final String ACTION_NEXT = ACTION_PREFIX + "NEXT";
    private static final String ACTION_REFRESH = ACTION_PREFIX + "REFRESH";
    private static final ExecutorService ARTWORK_EXECUTOR =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "widget-artwork");
                thread.setDaemon(true);
                return thread;
            });
    private static final AtomicInteger ARTWORK_GENERATION = new AtomicInteger();
    private static final Executor MAIN_EXECUTOR = command ->
            new Handler(Looper.getMainLooper()).post(command);

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        connect(context, ACTION_REFRESH);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent.getAction();
        if (action != null && action.startsWith(ACTION_PREFIX)) {
            connect(context, action);
        }
    }

    static void updateFromPlayer(Context context, Player player) {
        MediaItem item = player.getCurrentMediaItem();
        CharSequence title = item == null || item.mediaMetadata.title == null
                ? "Voltune" : item.mediaMetadata.title;
        CharSequence artist = item == null || item.mediaMetadata.artist == null
                ? "" : item.mediaMetadata.artist;
        Uri source = item == null || item.localConfiguration == null
                ? null : item.localConfiguration.uri;
        boolean playing = player.isPlaying();
        int generation = ARTWORK_GENERATION.incrementAndGet();
        render(context.getApplicationContext(), title, artist, playing, null);
        if (source != null) {
            ARTWORK_EXECUTOR.execute(() -> {
                Bitmap artwork = readArtwork(context, source);
                if (generation == ARTWORK_GENERATION.get()) {
                    render(context.getApplicationContext(), title, artist,
                            playing, artwork);
                }
            });
        }
    }

    private static void connect(Context context, String action) {
        Context app = context.getApplicationContext();
        SessionToken token = new SessionToken(app,
                new ComponentName(app, Media3PlayerService.class));
        ListenableFuture<MediaController> future =
                new MediaController.Builder(app, token).buildAsync();
        future.addListener(() -> {
            MediaController controller = null;
            try {
                controller = future.get();
                if (ACTION_PREVIOUS.equals(action)) {
                    controller.seekToPreviousMediaItem();
                } else if (ACTION_TOGGLE.equals(action)) {
                    if (controller.isPlaying()) {
                        controller.pause();
                    } else {
                        controller.play();
                    }
                } else if (ACTION_NEXT.equals(action)) {
                    controller.seekToNextMediaItem();
                }
                updateFromPlayer(app, controller);
            } catch (Exception ignored) {
                render(app, "Voltune", "", false, null);
            } finally {
                if (controller != null) {
                    controller.release();
                }
            }
        }, MAIN_EXECUTOR);
    }

    private static void render(Context context, CharSequence title, CharSequence artist,
            boolean playing, @Nullable Bitmap artwork) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context,
                PlayerWidgetProvider.class));
        if (ids.length == 0) {
            return;
        }
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.player_widget);
        views.setTextViewText(R.id.widget_title, title);
        views.setTextViewText(R.id.widget_artist, artist);
        views.setImageViewResource(R.id.widget_toggle,
                playing ? R.drawable.ic_widget_pause : R.drawable.ic_widget_play);
        if (artwork == null) {
            views.setImageViewResource(R.id.widget_artwork,
                    R.drawable.voltune_icon_master);
        } else {
            views.setImageViewBitmap(R.id.widget_artwork, artwork);
        }
        views.setOnClickPendingIntent(R.id.widget_root, activityIntent(context));
        views.setOnClickPendingIntent(R.id.widget_previous,
                actionIntent(context, ACTION_PREVIOUS, 1));
        views.setOnClickPendingIntent(R.id.widget_toggle,
                actionIntent(context, ACTION_TOGGLE, 2));
        views.setOnClickPendingIntent(R.id.widget_next,
                actionIntent(context, ACTION_NEXT, 3));
        manager.updateAppWidget(ids, views);
    }

    private static PendingIntent activityIntent(Context context) {
        return PendingIntent.getActivity(context, 0, new Intent(context, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static PendingIntent actionIntent(Context context, String action, int requestCode) {
        Intent intent = new Intent(context, PlayerWidgetProvider.class).setAction(action);
        return PendingIntent.getBroadcast(context, requestCode, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Nullable
    private static Bitmap readArtwork(Context context, Uri source) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, source);
            byte[] bytes = retriever.getEmbeddedPicture();
            if (bytes == null || bytes.length > 8 * 1024 * 1024) {
                return null;
            }
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bitmap == null) {
                return null;
            }
            int max = Math.max(bitmap.getWidth(), bitmap.getHeight());
            if (max <= 320) {
                return bitmap;
            }
            float scale = 320f / max;
            return Bitmap.createScaledBitmap(bitmap, Math.round(bitmap.getWidth() * scale),
                    Math.round(bitmap.getHeight() * scale), true);
        } catch (Exception ignored) {
            return null;
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {
                // Nothing else owns the retriever.
            }
        }
    }
}
