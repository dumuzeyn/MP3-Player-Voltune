package com.dumuzeyn.mp3player

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import java.util.ArrayDeque
import java.util.concurrent.Executor

/** Owns the Activity-scoped MediaController connection and commands queued during startup. */
class MediaControllerConnection(
    private val context: Context,
    private val listener: Player.Listener,
    private val onConnected: (MediaController) -> Unit,
) : AutoCloseable {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pendingCommands = ArrayDeque<(MediaController) -> Unit>()
    private var future: ListenableFuture<MediaController>? = null
    var controller: MediaController? = null
        private set
    private var closed = false

    fun connect() {
        if (future != null || closed) return
        val token = SessionToken(context, ComponentName(context, Media3PlayerService::class.java))
        future = MediaController.Builder(context, token).buildAsync().also { pending ->
            pending.addListener(
                { mainHandler.post(::finishConnection) },
                Executor(Runnable::run),
            )
        }
    }

    fun execute(command: (MediaController) -> Unit) {
        if (closed) return
        controller?.let(command) ?: run {
            if (pendingCommands.size >= MAX_PENDING_COMMANDS) pendingCommands.removeFirst()
            pendingCommands.addLast(command)
            connect()
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        mainHandler.removeCallbacksAndMessages(null)
        pendingCommands.clear()
        controller?.let { connected ->
            connected.removeListener(listener)
            connected.release()
        } ?: future?.let(MediaController::releaseFuture)
        controller = null
        future = null
    }

    private fun finishConnection() {
        val pending = future ?: return
        if (closed) return
        runCatching { pending.get() }
            .onSuccess { connected ->
                controller = connected
                connected.addListener(listener)
                onConnected(connected)
                while (pendingCommands.isNotEmpty()) pendingCommands.removeFirst()(connected)
            }
            .onFailure { error ->
                VoltuneLog.failure("media_controller_connection_failed", error)
                pendingCommands.clear()
            }
    }

    private companion object {
        const val MAX_PENDING_COMMANDS = 24
    }
}
