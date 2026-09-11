package com.dumuzeyn.mp3player

import android.content.SharedPreferences
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

internal class VolumeLevelingController(private val host: MainActivityCore) {
    private var playerButton: Button? = null
    private var analyzer: TrackLoudnessNormalizer? = null

    fun settingLabel(): String = host.tr("Volume leveling: ", "Единая громкость: ") +
        host.tr(if (enabled()) "on" else "off", if (enabled()) "вкл" else "выкл")

    fun createPlayerButton(): Button {
        val button = host.uiFactory.button(buttonText()).apply {
            setSingleLine(false)
            maxLines = 2
            textSize = 13f
            contentDescription = host.tr("Volume leveling", "Единая громкость")
            setOnClickListener { toggle() }
            setOnLongClickListener { openModeDialog(); true }
        }
        playerButton = button
        refreshButton()
        return button
    }

    fun onLibraryReady(tracks: List<Track>) {
        normalizer().updateReferenceTracks(tracks)
    }

    private fun mode(): LoudnessLevelingMode = LoudnessLevelingMode.fromPreference(
        prefs().getString(LoudnessLevelingMode.PREFERENCE, null),
        prefs().getBoolean(TrackLoudnessNormalizer.REDUCE_ONLY, false),
    )

    private fun modeLabel(value: LoudnessLevelingMode): String = when (value) {
        LoudnessLevelingMode.REDUCE -> host.tr("Make loud tracks quieter", "Громкие до уровня тихих")
        LoudnessLevelingMode.BOOST -> host.tr("Make quiet tracks louder", "Тихие до уровня громких")
        LoudnessLevelingMode.BALANCED -> host.tr("Balanced", "Сбалансированный")
    }

    fun openModeDialog() {
        val shade = host.uiFactory.shade()
        val panel = host.uiFactory.panelCard()
        panel.addView(host.uiFactory.dialogTitle(host.tr("Leveling mode", "Режим громкости")),
            host.uiFactory.dialogTitleParams())
        val choices = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL }
        panel.addView(ScrollView(host).apply { addView(choices) }, LinearLayout.LayoutParams(-1, -2, 1f))
        LoudnessLevelingMode.entries.forEach { value ->
            val option = dialogButton(modeLabel(value)).apply {
                isSelected = value == mode()
                if (isSelected) host.uiFactory.applyPrimaryButtonStyle(this)
                setOnClickListener {
                    prefs().edit().putString(LoudnessLevelingMode.PREFERENCE, value.name).apply()
                    dispatchSettings()
                    host.overlayHost.removeView(shade)
                }
            }
            choices.addView(option, LinearLayout.LayoutParams(-1, host.dp(60)))
        }
        shade.addView(panel, host.centerParams(host.dp(350), -2))
        host.overlayHost.addView(shade)
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
        val content = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL }
        panel.addView(ScrollView(host).apply { addView(content) }, LinearLayout.LayoutParams(-1, -2, 1f))

        val enabledButton = dialogButton(settingLabel())
        enabledButton.setOnClickListener {
            toggle()
            enabledButton.text = settingLabel()
        }
        content.addView(enabledButton, rowParams())

        content.addView(dialogButton(modeLabel(mode())).apply {
            setOnClickListener {
                host.overlayHost.removeView(shade)
                openModeDialog()
            }
        }, rowParams())

        val status = host.uiFactory.text(statusText(normalizer), 14, false).apply {
            minHeight = host.dp(50)
        }
        content.addView(status, LinearLayout.LayoutParams(-1, -2))

        val analyze = dialogButton(host.tr("Analyze library", "Анализировать медиатеку"))
        analyze.setOnClickListener {
            normalizer.analyzeLibrary(
                host.libraryState.tracks,
                progressListener(normalizer, status),
            )
        }
        content.addView(analyze, rowParams())

        val cancel = dialogButton(host.tr("Cancel analysis", "Отменить анализ"))
        cancel.setOnClickListener {
            normalizer.cancelAnalysis()
            status.text = host.tr("Cancelling analysis...", "Анализ отменяется...")
        }
        content.addView(cancel, rowParams())

        val retry = dialogButton(host.tr("Retry errors", "Повторить ошибки"))
        retry.setOnClickListener {
            val failed = normalizer.failedTrackIds()
            val retryTracks = ArrayList<Track>()
            for (track in host.libraryState.tracks) {
                if (failed.contains(track.trackId)) retryTracks.add(track)
            }
            normalizer.analyzeLibrary(retryTracks, progressListener(normalizer, status))
        }
        content.addView(retry, rowParams())

        val clear = dialogButton(
            host.tr("Clear analysis cache", "Очистить результаты анализа"),
        )
        clear.setOnClickListener {
            normalizer.clearCache()
            status.text = statusText(normalizer)
            dispatchSettings()
        }
        content.addView(clear, rowParams())

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

    private fun toggle() {
        prefs().edit().putBoolean(ENABLED, !enabled()).apply()
        refreshButton()
        dispatchSettings()
    }

    private fun enabled(): Boolean = prefs().getBoolean(ENABLED, false)

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
