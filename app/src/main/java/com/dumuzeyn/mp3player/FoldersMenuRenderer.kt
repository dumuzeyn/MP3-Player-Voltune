package com.dumuzeyn.mp3player

import android.text.TextUtils
import android.view.View
import android.widget.LinearLayout

class FoldersMenuRenderer(private val host: MainActivityCore) : MenuRenderer {
    override fun render() {
        val folders = host.libraryState.homeContent.folders
        if (folders.isEmpty()) {
            host.list.addView(
                host.uiFactory.text(
                    host.tr("No imported folders", "Нет импортированных папок"),
                    17,
                    false,
                ),
            )
            return
        }
        folders.forEach { (name, tracks) -> host.list.addView(folderRow(name, tracks)) }
    }

    override fun needsMiniSpacer(): Boolean = true

    private fun folderRow(name: String, tracks: ArrayList<Track>): View {
        val row = host.uiFactory.row().apply {
            setPadding(host.dp(10), host.dp(5), host.dp(6), host.dp(5))
        }
        host.uiFactory.applyCardStyle(row, host.appearanceState.songCardOpacity)
        val labels = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            val title = host.uiFactory.text(name, 17, true).apply {
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.END
            }
            addView(title)
            addView(host.uiFactory.text("${tracks.size} ${host.tr("songs", "песен")}", 13, false))
        }
        row.addView(labels, LinearLayout.LayoutParams(0, host.dp(62), 1.0f))

        val add = host.uiFactory.icon("+").apply {
            contentDescription = host.tr("Add folder to queue", "Добавить папку в очередь")
            setOnClickListener { host.playbackQueueController.addAll(tracks) }
        }
        row.addView(add, host.uiFactory.square(44))
        val shuffle = host.uiFactory.shuffleButton().apply {
            contentDescription = host.tr("Shuffle folder", "Перемешать папку")
            setOnClickListener { host.playbackQueueController.playList(tracks, true) }
        }
        row.addView(shuffle, host.uiFactory.square(44))
        val play = host.uiFactory.icon("▶").apply {
            contentDescription = host.tr("Play folder", "Воспроизвести папку")
            setOnClickListener { host.playbackQueueController.playList(tracks, false) }
        }
        row.addView(play, host.uiFactory.square(44))
        row.setOnClickListener { host.overlayController.openGroup(name, tracks) }
        return host.uiFactory.spaced(row)
    }
}
