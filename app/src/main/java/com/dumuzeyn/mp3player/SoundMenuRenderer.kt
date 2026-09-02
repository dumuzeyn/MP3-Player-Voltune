package com.dumuzeyn.mp3player

import android.text.TextUtils
import android.view.Gravity
import android.widget.LinearLayout

internal class SoundMenuRenderer(host: MainActivityCore) : TrackGroupMenuRenderer(host) {
    override fun render() {
        addStatus()
        val analysis = host.soundAnalysisController
        when {
            !analysis.enabled() -> addMessage(
                host.tr(
                    "Similar-track analysis is disabled in Settings",
                    "Анализ похожих треков выключен в настройках",
                ),
            )
            host.libraryState.tracks.size < 4 -> addMessage(
                host.tr(
                    "Add more songs to find similar tracks",
                    "Добавьте больше песен, чтобы найти похожие треки",
                ),
            )
            analysis.groups().isEmpty() -> {
                addMessage(
                    host.tr(
                        "Similar tracks will appear after local analysis",
                        "Похожие треки появятся после локального анализа",
                    ),
                )
                addRebuildButton()
            }
            else -> {
                super.render()
                addRebuildButton()
            }
        }
    }

    override fun groupedTracks(): Map<String, ArrayList<Track>> {
        val result = LinkedHashMap<String, ArrayList<Track>>()
        val english = host.appearanceState.language == "en"
        host.soundAnalysisController.groups().forEach { group ->
            val tracks = group.trackIds.mapNotNullTo(ArrayList()) { host.findTrack(it) }
            if (tracks.isNotEmpty()) {
                val name = if (english) group.nameEnglish else group.nameRussian
                result.getOrPut(name) { ArrayList() }.addAll(tracks)
            }
        }
        return result
    }

    override fun unknownGroupName(): String = host.tr("Similar tracks", "Похожие треки")

    override fun groupSubtitle(name: String, tracks: ArrayList<Track>): String =
        "${tracks.size} ${host.tr("tracks", "треков")}"

    override fun cardOpacity(): Int = host.appearanceState.genreCardOpacity

    private fun addStatus() {
        val analysis = host.soundAnalysisController
        val status = when {
            !analysis.enabled() -> host.tr("Analysis disabled", "Анализ выключен")
            analysis.rebuildingGroups() -> host.tr("Rebuilding groups", "Пересборка групп")
            analysis.activeTitle().isNotEmpty() ->
                host.tr("Analyzing: ", "Анализ: ") + analysis.activeTitle()
            analysis.blockReason() != SoundAnalysisConstraints.BlockReason.NONE ->
                blockedText(analysis.blockReason())
            analysis.queued() > 0 -> host.tr("Waiting: ", "В очереди: ") + analysis.queued()
            else -> host.tr("Similar tracks are up to date", "Похожие треки актуальны")
        }
        var progress = "${analysis.analyzed()} / ${analysis.total()}"
        if (analysis.failed() > 0) {
            progress += host.tr(" · errors: ", " · ошибок: ") + analysis.failed()
        }
        val block = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(host.dp(12), host.dp(7), host.dp(12), host.dp(9))
            addView(host.uiFactory.text(status, 15, true).apply {
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.END
            })
            addView(host.uiFactory.text(progress, 13, false))
        }
        host.list.addView(block, LinearLayout.LayoutParams(-1, host.dp(58)))
    }

    private fun blockedText(reason: SoundAnalysisConstraints.BlockReason): String = when (reason) {
        SoundAnalysisConstraints.BlockReason.PLAYBACK ->
            host.tr("Paused during playback", "Пауза во время воспроизведения")
        SoundAnalysisConstraints.BlockReason.LOW_BATTERY ->
            host.tr("Paused to save battery", "Пауза для экономии заряда")
        else -> host.tr(
            "Paused until the device cools down",
            "Пауза до охлаждения устройства",
        )
    }

    private fun addMessage(value: String) {
        val message = host.uiFactory.text(value, 15, false).apply {
            setTextColor(host.secondaryText)
            gravity = Gravity.CENTER
            setPadding(host.dp(20), host.dp(12), host.dp(20), host.dp(12))
        }
        host.list.addView(message, LinearLayout.LayoutParams(-1, host.dp(82)))
    }

    private fun addRebuildButton() {
        val analysis = host.soundAnalysisController
        val busy = analysis.rebuildingGroups() || analysis.fullReanalysis()
        val label = if (analysis.rebuildingGroups()) {
            host.tr("Rebuilding groups...", "Пересборка групп...")
        } else {
            host.tr("Rebuild groups", "Пересобрать группы")
        }
        val button = host.uiFactory.button(label)
        host.uiFactory.applySecondaryButtonStyle(button, host.appearanceState.genreCardOpacity)
        button.isEnabled = !busy && analysis.analyzed() >= 4
        button.setOnClickListener { analysis.rebuildGroupsFromSavedProfiles() }
        val params = LinearLayout.LayoutParams(-1, host.dp(48)).apply {
            setMargins(0, host.dp(10), 0, host.dp(4))
        }
        host.list.addView(button, params)
    }
}
