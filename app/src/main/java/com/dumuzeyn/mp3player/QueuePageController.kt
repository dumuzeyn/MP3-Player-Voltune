package com.dumuzeyn.mp3player

import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

/** Reuses one diffing queue adapter for the lifetime of the full player. */
internal class QueuePageController(
    private val host: MainActivityCore,
    private val state: PlaybackStateProvider,
    private val navigateBack: Runnable,
) : AutoCloseable {
    private var root: LinearLayout? = null
    private var adapter: QueueAdapter? = null
    private var active = false

    fun createView(): View {
        root?.let { return it }
        val createdRoot = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(host.dp(6), host.dp(4), host.dp(6), host.dp(8))
            addView(actionsRow(), LinearLayout.LayoutParams(-1, host.dp(58)))
        }
        val createdAdapter = QueueAdapter(host, state.activeQueue(), listener())
        adapter = createdAdapter
        val list = RecyclerView(host).apply {
            layoutManager = LinearLayoutManager(host)
            adapter = createdAdapter
            setHasFixedSize(false)
        }
        attachGestures(list)
        createdRoot.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        root = createdRoot
        return createdRoot
    }

    fun setActive(value: Boolean) {
        active = value
        if (value) refresh()
    }

    fun refresh() {
        if (!active) return
        adapter?.let {
            it.submitTracks(state.activeQueue())
            it.refreshPlayback()
        }
    }

    private fun actionsRow(): LinearLayout = host.uiFactory.row().apply {
        gravity = Gravity.CENTER
        addView(
            actionButton("▣", host.tr("Save queue as playlist", "Сохранить очередь как плейлист")) {
                saveQueue()
            },
            host.uiFactory.square(52),
        )
        addView(
            actionButton("⌫", host.tr("Clear queue", "Очистить очередь")) {
                host.playbackQueueController.clear()
                refresh()
            },
            host.uiFactory.square(52),
        )
        addView(
            actionButton("+", host.tr("Add to queue", "Добавить в очередь")) {
                chooseTracks()
            },
            host.uiFactory.square(52),
        )
    }

    private fun actionButton(symbol: String, description: String, action: () -> Unit): Button =
        host.uiFactory.icon(symbol).apply {
            contentDescription = description
            setOnClickListener { action() }
        }

    private fun listener(): QueueAdapter.Listener = object : QueueAdapter.Listener {
        override fun remove(index: Int) {
            host.playbackController.removeQueueItem(index)
        }

        override fun play(index: Int) {
            host.playbackQueueController.seekIndex(index)
        }

        override fun move(from: Int, to: Int) {
            host.playbackQueueController.move(from, to)
        }
    }

    private fun attachGestures(list: RecyclerView) {
        list.addOnItemTouchListener(
            object : RecyclerView.SimpleOnItemTouchListener() {
                private var downX = 0f
                private var downY = 0f
                private var navigating = false

                override fun onInterceptTouchEvent(view: RecyclerView, event: MotionEvent): Boolean {
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                        downX = event.x
                        downY = event.y
                        navigating = false
                        return false
                    }
                    if (event.actionMasked != MotionEvent.ACTION_MOVE || navigating) {
                        return navigating
                    }
                    val dx = event.x - downX
                    val dy = event.y - downY
                    if (dx >= host.dp(42) && dx > abs(dy) * 1.25f) {
                        navigating = true
                        navigateBack.run()
                        return true
                    }
                    return false
                }

                override fun onTouchEvent(view: RecyclerView, event: MotionEvent) {
                    if (event.actionMasked == MotionEvent.ACTION_UP ||
                        event.actionMasked == MotionEvent.ACTION_CANCEL
                    ) {
                        navigating = false
                    }
                }
            },
        )
        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            ItemTouchHelper.LEFT,
        ) {
            override fun onMove(
                view: RecyclerView,
                from: RecyclerView.ViewHolder,
                to: RecyclerView.ViewHolder,
            ): Boolean = adapter?.move(from.bindingAdapterPosition, to.bindingAdapterPosition) ?: false

            override fun onSwiped(holder: RecyclerView.ViewHolder, direction: Int) {
                adapter?.remove(holder.bindingAdapterPosition)
            }
        }
        ItemTouchHelper(callback).attachToRecyclerView(list)
    }

    private fun chooseTracks() {
        host.overlayController.openSelection(
            host.tr("Add to queue", "Добавить в очередь"),
            HashSet(),
        ) { selected ->
            val additions = selected.mapNotNullTo(ArrayList(), host::findTrack)
            host.playbackQueueController.addAll(additions)
            refresh()
        }
    }

    private fun saveQueue() {
        if (state.activeQueue().isEmpty()) return
        host.overlayController.showInput(
            host.tr("Save queue", "Сохранить очередь"),
            host.tr("Playlist name", "Название плейлиста"),
            "",
            false,
        ) { value ->
            val playlist = host.playlistController.createPlaylist(value)
            state.activeQueue().forEach { track -> playlist.uris.add(track.uri) }
            host.saveLibraryState()
        }
    }

    override fun close() {
        active = false
        adapter?.submitTracks(emptyList())
        adapter = null
        root = null
    }
}
