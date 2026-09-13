package com.dumuzeyn.mp3player

import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/** Queue panel backed exclusively by Media3 queue commands. */
internal class QueueOverlayController(
    private val host: MainActivityCore,
    private val overlays: OverlayController,
) {
    private var activeAdapter: QueueAdapter? = null

    fun open() {
        val shade = host.uiFactory.shade()
        val panel = host.uiFactory.panelCard().apply { addView(header(shade)) }
        val adapter = adapter(ArrayList(host.playbackUiState.queue))
        activeAdapter = adapter
        val list = RecyclerView(host).apply {
            layoutManager = LinearLayoutManager(host)
            this.adapter = adapter
            setHasFixedSize(false)
        }
        attachGestures(list, adapter)
        panel.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        shade.addView(panel, host.bottomParams())
        host.overlayHost.addView(shade)
        host.playerUiController.updateMini()
    }

    fun refreshPlayback() {
        activeAdapter?.refreshPlayback()
    }

    private fun header(shade: FrameLayout): LinearLayout = host.uiFactory.row().apply {
        addView(
            host.uiFactory.text(host.tr("Queue", "Очередь"), 20, true),
            LinearLayout.LayoutParams(0, host.dp(58), 1f),
        )
        addView(
            iconButton("▣", host.tr("Save queue as playlist", "Сохранить очередь как плейлист")) {
                saveQueue(shade)
            },
            host.uiFactory.square(48),
        )
        addView(
            iconButton("⌫", host.tr("Clear queue", "Очистить очередь")) {
                host.playbackQueueController.clear()
                close(shade)
            },
            host.uiFactory.square(48),
        )
        addView(
            iconButton("+", host.tr("Add to queue", "Добавить в очередь")) {
                chooseTracks(shade)
            },
            host.uiFactory.square(48),
        )
        addView(
            iconButton("×", host.tr("Close", "Закрыть")) { close(shade) },
            host.uiFactory.square(48),
        )
    }

    private fun iconButton(symbol: String, description: String, action: () -> Unit): Button =
        host.uiFactory.icon(symbol).apply {
            contentDescription = description
            setOnClickListener { action() }
        }

    private fun adapter(tracks: ArrayList<Track>): QueueAdapter =
        QueueAdapter(
            host,
            tracks,
            object : QueueAdapter.Listener {
                override fun remove(index: Int) {
                    host.playbackController.removeQueueItem(index)
                }

                override fun play(index: Int) {
                    host.playbackQueueController.seekIndex(index)
                }

                override fun move(from: Int, to: Int) {
                    host.playbackQueueController.move(from, to)
                }
            },
        )

    private fun attachGestures(list: RecyclerView, adapter: QueueAdapter) {
        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            ItemTouchHelper.START or ItemTouchHelper.END,
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                source: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder,
            ): Boolean = adapter.move(source.bindingAdapterPosition, target.bindingAdapterPosition)

            override fun onSwiped(holder: RecyclerView.ViewHolder, direction: Int) {
                adapter.remove(holder.bindingAdapterPosition)
            }
        }
        ItemTouchHelper(callback).attachToRecyclerView(list)
    }

    private fun chooseTracks(shade: FrameLayout) {
        close(shade)
        overlays.openSelection(
            host.tr("Add to queue", "Добавить в очередь"),
            HashSet(),
        ) { selected ->
            val additions = selected.mapNotNullTo(ArrayList(), host::findTrack)
            host.playbackQueueController.addAll(additions)
            host.uiHandler.postDelayed(::open, 120L)
        }
    }

    private fun saveQueue(shade: FrameLayout) {
        if (host.playbackUiState.queue.isEmpty()) return
        close(shade)
        overlays.showInput(
            host.tr("Save queue", "Сохранить очередь"),
            host.tr("Playlist name", "Название плейлиста"),
            "",
            false,
        ) { value ->
            val playlist = host.playlistController.createPlaylist(value)
            host.playbackUiState.queue.forEach { track -> playlist.uris.add(track.uri) }
            host.saveLibraryState()
        }
    }

    private fun close(shade: FrameLayout) {
        if (shade.parent != null) host.overlayHost.removeView(shade)
        activeAdapter = null
        host.playerUiController.updateMini()
    }
}
