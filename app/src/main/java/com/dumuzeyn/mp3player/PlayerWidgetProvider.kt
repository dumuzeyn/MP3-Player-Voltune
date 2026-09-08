package com.dumuzeyn.mp3player

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.RemoteViews
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.roundToInt

class PlayerWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        connect(context, ACTION_REFRESH)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action
        if (action != null && action.startsWith(ACTION_PREFIX)) connect(context, action)
    }

    companion object {
        private const val ACTION_PREFIX = "com.dumuzeyn.mp3player.widget."
        private const val ACTION_PREVIOUS = ACTION_PREFIX + "PREVIOUS"
        private const val ACTION_TOGGLE = ACTION_PREFIX + "TOGGLE"
        private const val ACTION_NEXT = ACTION_PREFIX + "NEXT"
        private const val ACTION_REFRESH = ACTION_PREFIX + "REFRESH"
        private val artworkExecutor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "widget-artwork").apply { isDaemon = true }
        }
        private val artworkGeneration = AtomicInteger()
        private val mainExecutor = Executor { command ->
            Handler(Looper.getMainLooper()).post(command)
        }

        @JvmStatic
        fun updateFromPlayer(context: Context, player: Player) {
            val item = player.currentMediaItem
            val title: CharSequence = item?.mediaMetadata?.title ?: "Voltune"
            val artist: CharSequence = item?.mediaMetadata?.artist ?: ""
            val source = item?.localConfiguration?.uri
            val playing = player.isPlaying
            val generation = artworkGeneration.incrementAndGet()
            render(context.applicationContext, title, artist, playing, null)
            if (source != null) {
                artworkExecutor.execute {
                    val artwork = readArtwork(context, source)
                    if (generation == artworkGeneration.get()) {
                        render(context.applicationContext, title, artist, playing, artwork)
                    }
                }
            }
        }

        private fun connect(context: Context, action: String) {
            val app = context.applicationContext
            val token = SessionToken(app, ComponentName(app, Media3PlayerService::class.java))
            val future = MediaController.Builder(app, token).buildAsync()
            future.addListener(
                {
                    var controller: MediaController? = null
                    try {
                        controller = future.get()
                        when (action) {
                            ACTION_PREVIOUS -> controller.seekToPreviousMediaItem()
                            ACTION_TOGGLE -> if (controller.isPlaying) {
                                controller.pause()
                            } else {
                                controller.play()
                            }
                            ACTION_NEXT -> controller.seekToNextMediaItem()
                        }
                        updateFromPlayer(app, controller)
                    } catch (_: Exception) {
                        render(app, "Voltune", "", false, null)
                    } finally {
                        controller?.release()
                    }
                },
                mainExecutor,
            )
        }

        private fun render(
            context: Context,
            title: CharSequence,
            artist: CharSequence,
            playing: Boolean,
            artwork: Bitmap?,
        ) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, PlayerWidgetProvider::class.java),
            )
            if (ids.isEmpty()) return
            val views = RemoteViews(context.packageName, R.layout.player_widget)
            views.setTextViewText(R.id.widget_title, title)
            views.setTextViewText(R.id.widget_artist, artist)
            views.setImageViewResource(
                R.id.widget_toggle,
                if (playing) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
            )
            if (artwork == null) {
                views.setImageViewResource(R.id.widget_artwork, R.drawable.voltune_icon_master)
            } else {
                views.setImageViewBitmap(R.id.widget_artwork, artwork)
            }
            views.setOnClickPendingIntent(R.id.widget_root, activityIntent(context))
            views.setOnClickPendingIntent(
                R.id.widget_previous,
                actionIntent(context, ACTION_PREVIOUS, 1),
            )
            views.setOnClickPendingIntent(
                R.id.widget_toggle,
                actionIntent(context, ACTION_TOGGLE, 2),
            )
            views.setOnClickPendingIntent(
                R.id.widget_next,
                actionIntent(context, ACTION_NEXT, 3),
            )
            manager.updateAppWidget(ids, views)
        }

        private fun activityIntent(context: Context): PendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        private fun actionIntent(
            context: Context,
            action: String,
            requestCode: Int,
        ): PendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, PlayerWidgetProvider::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        private fun readArtwork(context: Context, source: Uri): Bitmap? {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, source)
                val bytes = retriever.embeddedPicture
                if (bytes == null || bytes.size > 8 * 1024 * 1024) return null
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
                val largestSide = max(bitmap.width, bitmap.height)
                if (largestSide <= 320) return bitmap
                val scale = 320f / largestSide
                return Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).roundToInt(),
                    (bitmap.height * scale).roundToInt(),
                    true,
                )
            } catch (_: Exception) {
                return null
            } finally {
                try {
                    retriever.release()
                } catch (_: Exception) {
                }
            }
        }
    }
}
