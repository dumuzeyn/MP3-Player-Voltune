package com.dumuzeyn.mp3player

import android.widget.LinearLayout
import kotlin.math.roundToInt

internal class AudioEditorAnalysisView(private val host: MainActivityCore, private var clip: AudioEditClip) : LinearLayout(host) {
    private val label = host.uiFactory.text(host.tr("Analyzing BPM and key", "Определение BPM и тональности"), 14, false)
    private var subscription: AutoCloseable? = null
    private val refresh = Runnable { request() }
    fun selection(startMs: Long, endMs: Long) {
        val next = runCatching { clip.copy(startMs = startMs, endMs = endMs) }.getOrNull() ?: return
        if (next == clip) return
        clip = next
        subscription?.close()
        removeCallbacks(refresh)
        label.text = host.tr("Analyzing BPM and key", "Определение BPM и тональности")
        if (isAttachedToWindow) postDelayed(refresh, 400)
    }
    init {
        orientation = VERTICAL
        setPadding(0, host.dp(4), 0, host.dp(8))
        addView(label, LayoutParams(-1, -2))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        request()
    }

    private fun request() {
        subscription?.close()
        subscription = host.audioEditorController.analysis.request(clip) { result ->
            label.text = result.fold(onSuccess = { analysis ->
                val bpm = if (analysis.bpm > 0) "~${analysis.bpm.roundToInt()}" else host.tr("undetermined", "не определён")
                val key = analysis.key?.label(host.appearanceState.language != "en") ?: host.tr("undetermined", "не определена")
                "BPM: $bpm\n${host.tr("Key", "Тональность")}: $key"
            }, onFailure = { host.tr("Audio analysis unavailable", "Анализ аудио недоступен") })
        }
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(refresh)
        subscription?.close()
        subscription = null
        super.onDetachedFromWindow()
    }
}
