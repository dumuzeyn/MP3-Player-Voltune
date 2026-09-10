package com.dumuzeyn.mp3player

import android.os.Build
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar

internal class AudioEditorMenuRenderer(private val host: MainActivityCore) : MenuRenderer {
    override fun needsMiniSpacer() = true

    override fun render() {
        val controller = host.audioEditorController
        controller.load()
        val dialogs = AudioEditorDialogs(host)
        val toolbar = host.uiFactory.row()
        toolbar.addView(tool("+", host.tr("Add audio", "Добавить аудио"), !controller.busy) {
            dialogs.chooseTrack(0)
        })
        toolbar.addView(tool("↶", host.tr("Undo", "Отменить"), controller.canUndo, controller::undo))
        toolbar.addView(tool("↷", host.tr("Redo", "Повторить"), controller.canRedo, controller::redo))
        toolbar.addView(tool("×", host.tr("Clear project", "Очистить проект"),
            !controller.busy && controller.project.clips.isNotEmpty()) {
            host.showConfirmPanel(host.tr("Clear project?", "Очистить проект?"),
                host.tr("Remove all clips from the draft?", "Удалить все фрагменты из черновика?"),
                Runnable { controller.change { AudioEditProject() } })
        })
        host.list.addView(toolbar)
        if (controller.project.clips.isEmpty()) {
            host.list.addView(host.uiFactory.text(host.tr("No audio clips", "Нет аудиофрагментов"), 17, false))
        } else {
            host.list.addView(AudioEditorTimelineView(host, controller.project, dialogs::edit),
                LinearLayout.LayoutParams(-1, -2))
            controller.project.clips.groupBy { it.lane }.toSortedMap().forEach { (lane, clips) ->
                val header = host.uiFactory.row()
                header.addView(host.uiFactory.text("${host.tr("Lane", "Дорожка")} ${lane + 1}", 16, true),
                    LinearLayout.LayoutParams(0, -2, 1f))
                header.addView(tool("+", host.tr("Append audio", "Добавить аудио в конец"), !controller.busy) {
                    dialogs.chooseTrack(lane)
                })
                host.list.addView(header)
                clips.sortedBy { it.offsetMs }.forEach { clip ->
                    val row = host.uiFactory.button(
                        "${clip.title}\n${host.formatSeconds(clip.offsetMs / 1000)} + " +
                            host.formatSeconds(clip.durationMs / 1000),
                    ).apply {
                        minHeight = host.dp(62)
                        maxLines = 4
                        setPadding(host.dp(8), host.dp(8), host.dp(8), host.dp(8))
                        host.uiFactory.applySecondaryButtonStyle(this)
                        setOnClickListener { dialogs.edit(clip) }
                        contentDescription = "${host.tr("Edit clip", "Изменить фрагмент")}: ${clip.title}"
                    }
                    host.list.addView(host.uiFactory.spaced(row))
                }
            }
            val nextLane = (0 until AudioEditClip.MAX_LANES).firstOrNull { lane ->
                controller.project.clips.none { it.lane == lane }
            }
            host.list.addView(command(host.tr("Add lane", "Добавить дорожку"),
                !controller.busy && nextLane != null) { dialogs.chooseTrack(nextLane ?: 0) })
            host.list.addView(command(host.tr("Join in clip order", "Соединить по порядку фрагментов"),
                !controller.busy) { controller.change { it.concatenate() } })
        }
        val status = host.uiFactory.text(controller.status, 14, false)
        status.visibility = if (controller.status.isEmpty()) View.GONE else View.VISIBLE
        host.list.addView(status)
        val progress = ProgressBar(host, null, android.R.attr.progressBarStyleHorizontal).apply {
            visibility = if (controller.busy) View.VISIBLE else View.GONE
            isIndeterminate = controller.progress < 0
            this.progress = controller.progress.coerceAtLeast(0)
        }
        host.list.addView(progress, LinearLayout.LayoutParams(-1, host.dp(12)))
        if (!host.navigationState.renderingTabPreview) controller.onProgress = {
            progress.isIndeterminate = controller.progress < 0
            progress.progress = controller.progress.coerceAtLeast(0)
        }
        host.list.addView(command(host.tr("Export M4A", "Экспорт M4A"),
            !controller.busy && controller.project.clips.isNotEmpty(), controller::export).apply {
            host.uiFactory.applyPrimaryButtonStyle(this)
        })
        if (controller.exporting) host.list.addView(command(host.tr("Cancel export", "Отменить экспорт"), true,
            controller::cancelExport))
        if (controller.canSave) host.list.addView(command(host.tr("Save exported audio", "Сохранить готовое аудио"),
            true, controller::saveExport))
    }

    private fun tool(symbol: String, label: String, enabled: Boolean, run: () -> Unit): Button =
        host.uiFactory.icon(symbol).apply {
            contentDescription = label
            if (Build.VERSION.SDK_INT >= 26) tooltipText = label
            isEnabled = enabled
            alpha = if (enabled) 1f else 0.4f
            layoutParams = host.uiFactory.square(44)
            setOnClickListener { run() }
        }

    private fun command(label: String, enabled: Boolean, run: () -> Unit) = host.uiFactory.button(label).apply {
        layoutParams = LinearLayout.LayoutParams(-1, host.dp(52))
        isEnabled = enabled
        alpha = if (enabled) 1f else 0.4f
        setOnClickListener { run() }
    }
}
