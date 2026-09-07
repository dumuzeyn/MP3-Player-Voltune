package com.dumuzeyn.mp3player

import android.text.TextUtils
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import kotlin.math.max
import kotlin.math.min

/** Settings UI for persisted SAF music sources. */
internal class MusicFoldersController(private val host: MainActivityCore) {
    fun open() {
        val sources = PersistedFolderStore.list(host)
        val shade = host.uiFactory.shade()
        val panel = host.uiFactory.panelCard()
        panel.setPadding(host.dp(16), host.dp(16), host.dp(16), host.dp(16))
        panel.addView(
            host.uiFactory.dialogTitle(host.tr("Music folders", "Музыкальные папки")),
            host.uiFactory.dialogTitleParams(),
        )

        val rows = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL }
        if (sources.isEmpty()) {
            val empty = host.uiFactory.text(
                host.tr("No folders added", "Папки не добавлены"),
                16,
                false,
            )
            empty.setTextColor(host.muted)
            empty.gravity = Gravity.CENTER_VERTICAL
            rows.addView(empty, LinearLayout.LayoutParams(-1, host.dp(64)))
        } else {
            sources.forEach { source ->
                rows.addView(sourceRow(source, shade), LinearLayout.LayoutParams(-1, host.dp(62)))
            }
        }

        val scroll = ScrollView(host).apply {
            isFillViewport = false
            addView(rows, FrameLayout.LayoutParams(-1, -2))
        }
        val availableScrollHeight = max(
            host.dp(64),
            host.resources.displayMetrics.heightPixels - host.dp(250),
        )
        panel.addView(
            scroll,
            LinearLayout.LayoutParams(
                -1,
                min(availableScrollHeight, host.dp(max(64, sources.size * 62))),
            ),
        )

        val add = host.uiFactory.button(host.tr("Add folder", "Добавить папку"))
        host.uiFactory.applyPrimaryButtonStyle(add)
        add.setOnClickListener {
            close(shade)
            host.audioImportController.openFolder()
        }
        panel.addView(add, buttonParams())

        val done = host.uiFactory.button(host.tr("Done", "Готово"))
        done.setOnClickListener { close(shade) }
        panel.addView(done, buttonParams())
        shade.addView(panel, host.centerParams(host.dp(350), -2))
        host.overlayHost.addView(shade)
    }

    private fun sourceRow(source: LibrarySource, shade: FrameLayout): LinearLayout {
        val row = host.uiFactory.row()
        val name = host.uiFactory.text(source.displayName, 16, true)
        name.setSingleLine(true)
        name.ellipsize = TextUtils.TruncateAt.MIDDLE
        name.contentDescription = source.displayName
        row.addView(name, LinearLayout.LayoutParams(0, -1, 1f))

        val remove = host.uiFactory.icon("×")
        remove.contentDescription = host.tr(
            "Remove folder from Voltune: ",
            "Убрать папку из Voltune: ",
        ) + source.displayName
        remove.setOnClickListener { confirmRemoval(source, shade) }
        row.addView(remove, LinearLayout.LayoutParams(host.dp(52), host.dp(52)))
        return row
    }

    private fun confirmRemoval(source: LibrarySource, shade: FrameLayout) {
        host.showConfirmPanel(
            host.tr(
                "Remove music folder from Voltune?",
                "Убрать музыкальную папку из Voltune?",
            ),
            host.tr(
                "The folder and all its songs will be removed from Voltune. " +
                    "Files on the device will stay unchanged.",
                "Папка и все песни из неё будут удалены из Voltune. " +
                    "Файлы на устройстве останутся без изменений.",
            ),
            Runnable {
                close(shade)
                host.playbackQueueController.removeSource(source)
            },
        )
    }

    private fun buttonParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(-1, host.dp(52)).apply {
            setMargins(0, host.dp(8), 0, 0)
        }

    private fun close(shade: FrameLayout) {
        if (shade.parent != null) host.overlayHost.removeView(shade)
        host.playerUiController.updateMini()
    }
}
