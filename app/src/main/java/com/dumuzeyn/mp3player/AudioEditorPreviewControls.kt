package com.dumuzeyn.mp3player

import android.os.Build
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Toast
import java.util.Locale

internal class AudioEditorPreviewControls(private val host: MainActivityCore,
    private val project: () -> AudioEditProject,
    private val startPositionMs: () -> Long = { 0L },
    private val displayDurationMs: () -> Long = { project().durationMs },
    private val stopOnDetach: Boolean = false,
) : LinearLayout(host) {
    private val preview get() = host.audioEditorController.preview
    private var subscription: AutoCloseable? = null
    private var dragging = false
    private val label = host.uiFactory.text("", 14, false)
    private val play = host.uiFactory.icon("▶")
    private val stop = host.uiFactory.icon("■")
    private val seek = SeekBar(host).apply {
        max = 1000
        contentDescription = host.tr("Preview position", "Позиция предпрослушивания")
    }

    init {
        orientation = VERTICAL
        addView(label, LayoutParams(-1, -2))
        val row = host.uiFactory.row()
        row.addView(play, host.uiFactory.square(44))
        row.addView(seek, LayoutParams(0, host.dp(44), 1f))
        row.addView(stop, host.uiFactory.square(44))
        addView(row)
        host.uiFactory.applySeekBarColors(seek)
        play.setOnClickListener {
            if (preview.active) preview.toggle()
            else runCatching { preview.start(project(), startPositionMs()) }.onFailure {
                Toast.makeText(host, host.tr("Check the clip range", "Проверьте границы фрагмента"), Toast.LENGTH_SHORT).show()
            }
        }
        stop.contentDescription = host.tr("Stop preview", "Остановить предпрослушивание")
        if (Build.VERSION.SDK_INT >= 26) stop.tooltipText = stop.contentDescription
        stop.setOnClickListener { preview.stop() }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) = Unit
            override fun onStartTrackingTouch(bar: SeekBar) { dragging = true }
            override fun onStopTrackingTouch(bar: SeekBar) {
                dragging = false
                preview.seek(preview.durationMs * bar.progress / 1000)
            }
        })
        refresh()
    }

    private fun refresh() {
        val preparing = preview.phase == AudioEditorPreviewController.Phase.PREPARING
        val pending = preparing || preview.phase == AudioEditorPreviewController.Phase.STARTING
        play.text = if (preview.phase == AudioEditorPreviewController.Phase.PLAYING) "Ⅱ" else "▶"
        play.contentDescription = if (preview.active) host.tr("Pause or resume preview", "Пауза или продолжение предпрослушивания")
            else host.tr("Preview audio", "Прослушать аудио")
        if (Build.VERSION.SDK_INT >= 26) play.tooltipText = play.contentDescription
        play.isEnabled = !pending && (preview.active || !host.audioEditorController.busy)
        stop.isEnabled = preview.active
        seek.isEnabled = preview.active && !pending
        if (!dragging) seek.progress = if (preview.durationMs > 0)
            (preview.positionMs * 1000 / preview.durationMs).toInt() else 0
        label.text = when {
            preview.failed -> host.tr("Preview unavailable", "Предпрослушивание недоступно")
            preparing -> host.tr("Preparing preview", "Подготовка предпрослушивания") +
                if (preview.progress >= 0) " ${preview.progress}%" else ""
            preview.active -> precise(preview.positionMs) + " / " + precise(preview.durationMs)
            else -> precise(0) + " / " + precise(displayDurationMs())
        }
        alpha = if (play.isEnabled || stop.isEnabled) 1f else 0.5f
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); subscription = preview.observe(::refresh) }
    override fun onDetachedFromWindow() {
        subscription?.close()
        subscription = null
        if (stopOnDetach) preview.stop()
        super.onDetachedFromWindow()
    }

    private fun precise(valueMs: Long): String {
        val safe = valueMs.coerceAtLeast(0)
        return String.format(Locale.ROOT, "%d:%02d.%03d", safe / 60_000,
            safe / 1000 % 60, safe % 1000)
    }
}
