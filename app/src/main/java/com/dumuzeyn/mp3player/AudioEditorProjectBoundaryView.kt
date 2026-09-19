package com.dumuzeyn.mp3player

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

internal class AudioEditorProjectBoundaryView(
    private val host: MainActivityCore,
    private val project: AudioEditProject,
    private val selectedClipId: String,
) : View(host) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    init { contentDescription = host.tr("Song boundaries", "Границы песен") }

    override fun onDraw(canvas: Canvas) {
        val duration = project.durationMs.coerceAtLeast(1)
        val center = height / 2f
        paint.strokeWidth = host.dp(2).toFloat()
        paint.color = host.cardStroke
        canvas.drawLine(0f, center, width.toFloat(), center, paint)
        project.clips.forEach { clip ->
            paint.color = if (clip.id == selectedClipId) host.yellow else BOUNDARY_COLOR
            for (time in longArrayOf(clip.offsetMs, clip.finishMs)) {
                val x = width * time / duration.toFloat()
                canvas.drawLine(x, host.dp(5).toFloat(), x, height - host.dp(5).toFloat(), paint)
            }
        }
        paint.color = host.secondaryText
        paint.textSize = host.dp(10).toFloat()
        canvas.drawText(host.tr("Song boundaries", "Границы песен"), host.dp(5).toFloat(),
            height - host.dp(3).toFloat(), paint)
    }

    companion object { private val BOUNDARY_COLOR = Color.rgb(76, 175, 126) }
}
