package com.dumuzeyn.mp3player

import android.content.SharedPreferences
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import kotlin.math.roundToInt

internal class VolumeLevelingController(private val host: MainActivityCore) {
    private var playerButton: Button? = null
    private var analyzer: TrackLoudnessNormalizer? = null

    fun settingLabel(): String = host.tr("Volume leveling: ", "Единая громкость: ") +
        host.tr(if (enabled()) "on" else "off", if (enabled()) "вкл" else "выкл")

    fun createPlayerButton(): Button {
        val button = host.uiFactory.button(buttonText()).apply {
            setSingleLine(true)
            textSize = 13f
            setOnClickListener { toggle() }
        }
        playerButton = button
        refreshButton()
        return button
    }

    fun openDialog() {
        val normalizer = normalizer()
        val shade = host.uiFactory.shade()
        val panel = host.uiFactory.panelCard()
        panel.setPadding(host.dp(16), host.dp(14), host.dp(16), host.dp(14))
        panel.addView(
            host.uiFactory.dialogTitle(host.tr("Volume leveling", "Единая громкость")),
            host.uiFactory.dialogTitleParams(),
        )

        val enabledButton = dialogButton(settingLabel())
        enabledButton.setOnClickListener {
            toggle()
            enabledButton.text = settingLabel()
        }
        panel.addView(enabledButton, rowParams())

        val reduceOnly = dialogButton(reduceOnlyLabel())
        reduceOnly.setOnClickListener {
            prefs().edit().putBoolean(TrackLoudnessNormalizer.REDUCE_ONLY, !reduceOnly()).apply()
            reduceOnly.text = reduceOnlyLabel()
            dispatchSettings()
        }
        panel.addView(reduceOnly, rowParams())

        val targetLabel = host.uiFactory.text(targetLabel(), 14, false).apply {
            minHeight = host.dp(28)
        }
        panel.addView(targetLabel, LinearLayout.LayoutParams(-1, -2))
        val target = SeekBar(host).apply {
            max = 14
            progress = targetLufs() + 24
        }
        host.uiFactory.applySeekBarColors(target)
        target.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                prefs().edit()
                    .putInt(TrackLoudnessNormalizer.TARGET_LUFS, progress - 24)
                    .apply()
                targetLabel.text = targetLabel()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                dispatchSettings()
            }
        })
        panel.addView(target, LinearLayout.LayoutParams(-1, host.dp(36)))

        val status = host.uiFactory.text(statusText(normalizer), 14, false).apply {
            minHeight = host.dp(50)
        }
        panel.addView(status, LinearLayout.LayoutParams(-1, -2))

        val analyze = dialogButton(host.tr("Analyze library", "Анализировать медиатеку"))
        analyze.setOnClickListener {
            normalizer.analyzeLibrary(
                host.libraryState.tracks,
                progressListener(normalizer, status),
            )
        }
        panel.addView(analyze, rowParams())

        val cancel = dialogButton(host.tr("Cancel analysis", "Отменить анализ"))
        cancel.setOnClickListener {
            normalizer.cancelAnalysis()
            status.text = host.tr("Cancelling analysis...", "Анализ отменяется...")
        }
        panel.addView(cancel, rowParams())

        val retry = dialogButton(host.tr("Retry errors", "Повторить ошибки"))
        retry.setOnClickListener {
            val failed = normalizer.failedTrackIds()
            val retryTracks = ArrayList<Track>()
            for (track in host.libraryState.tracks) {
                if (failed.contains(track.trackId)) retryTracks.add(track)
            }
            normalizer.analyzeLibrary(retryTracks, progressListener(normalizer, status))
        }
        panel.addView(retry, rowParams())

        val clear = dialogButton(
            host.tr("Clear analysis cache", "Очистить результаты анализа"),
        )
        clear.setOnClickListener {
            normalizer.clearCache()
            status.text = statusText(normalizer)
            dispatchSettings()
        }
        panel.addView(clear, rowParams())

        val done = host.uiFactory.button(host.tr("Done", "Готово"))
        host.uiFactory.applyPrimaryButtonStyle(done)
        done.setOnClickListener { host.overlayHost.removeView(shade) }
        val doneParams = LinearLayout.LayoutParams(-1, host.dp(46)).apply {
            setMargins(0, host.dp(6), 0, 0)
        }
        panel.addView(done, doneParams)

        shade.addView(panel, host.centerParams(host.dp(350), -2))
        host.overlayHost.addView(shade)
        host.playerUiController.updateMini()
    }

    fun release() {
        analyzer?.release()
        analyzer = null
    }

    private fun progressListener(
        normalizer: TrackLoudnessNormalizer,
        status: TextView,
    ): TrackLoudnessNormalizer.ProgressListener = TrackLoudnessNormalizer.ProgressListener {
            completed,
            total,
            errors,
            finished,
            cancelled,
        ->
        if (finished) {
            status.text = statusText(normalizer) + if (cancelled) {
                host.tr(" · cancelled", " · отменено")
            } else {
                ""
            }
            dispatchSettings()
        } else {
            status.text = host.tr("Analysis: ", "Анализ: ") + completed + "/" + total +
                host.tr(" · errors: ", " · ошибок: ") + errors
        }
    }

    private fun dialogButton(text: String): Button = host.uiFactory.button(text).apply {
        textSize = 15f
        minHeight = host.dp(44)
        host.uiFactory.applySecondaryButtonStyle(this)
    }

    private fun rowParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(0, host.dp(2), 0, host.dp(2))
        }

    private fun statusText(normalizer: TrackLoudnessNormalizer): String =
        host.tr("Analyzed: ", "Проанализировано: ") +
            normalizer.analyzedCount(host.libraryState.tracks) +
            "/" + host.libraryState.tracks.size +
            host.tr(" · file errors: ", " · ошибок файлов: ") +
            normalizer.errorCount(host.libraryState.tracks)

    private fun reduceOnlyLabel(): String =
        host.tr(
            "Advanced mode, reduce only: ",
            "Расширенный режим, только уменьшение: ",
        ) + host.tr(
            if (reduceOnly()) "on" else "off",
            if (reduceOnly()) "вкл" else "выкл",
        )

    private fun targetLabel(): String =
        host.tr("Target level: ", "Целевой уровень: ") + targetLufs() + " LUFS"

    private fun targetLufs(): Int = prefs().getInt(
        TrackLoudnessNormalizer.TARGET_LUFS,
        LoudnessGainPolicy.DEFAULT_TARGET_LUFS.roundToInt(),
    ).coerceIn(-24, -10)

    private fun toggle() {
        prefs().edit().putBoolean(ENABLED, !enabled()).apply()
        refreshButton()
        dispatchSettings()
    }

    private fun enabled(): Boolean = prefs().getBoolean(ENABLED, false)

    private fun reduceOnly(): Boolean =
        prefs().getBoolean(TrackLoudnessNormalizer.REDUCE_ONLY, false)

    private fun buttonText(): String = host.tr(
        if (enabled()) "Level ●" else "Level ○",
        if (enabled()) "Уровень ●" else "Уровень ○",
    )

    private fun refreshButton() {
        val button = playerButton ?: return
        button.text = buttonText()
        host.uiFactory.applyPlayerToolStyle(button, enabled())
    }

    private fun prefs(): SharedPreferences =
        host.getSharedPreferences(EqualizerController.PREFS, 0)

    private fun normalizer(): TrackLoudnessNormalizer {
        val current = analyzer
        if (current != null) return current
        return TrackLoudnessNormalizer(host).also { analyzer = it }
    }

    private fun dispatchSettings() {
        host.refreshPlaybackAppearance()
    }

    companion object {
        const val ENABLED = "volume_leveling_enabled"
    }
}
