package com.dumuzeyn.mp3player

import android.widget.LinearLayout
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.SeekBar
import kotlin.math.min

internal class CardTransparencyController(private val host: MainActivityCore) {
    fun settingLabel(): String =
        host.tr("Card opacity by section", "Прозрачность карточек по разделам")

    fun openDialog() {
        val shade = host.uiFactory.shade()
        val panel = host.uiFactory.panelCard()
        panel.setPadding(host.dp(16), host.dp(12), host.dp(16), host.dp(12))
        panel.addView(
            host.uiFactory.dialogTitle(settingLabel(), 18),
            host.uiFactory.dialogTitleParams(),
        )

        val controls = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL }
        addControl(
            controls,
            host.tr("Songs", "Песни"),
            { host.appearanceState.songCardOpacity },
            { host.appearanceState.songCardOpacity = it },
        )
        addControl(
            controls,
            host.tr("Favorites", "Избранное"),
            { host.appearanceState.favoriteCardOpacity },
            { host.appearanceState.favoriteCardOpacity = it },
        )
        addControl(
            controls,
            host.tr("Playlists", "Плейлисты"),
            { host.appearanceState.playlistCardOpacity },
            { host.appearanceState.playlistCardOpacity = it },
        )
        addControl(
            controls,
            host.tr("Genres", "Жанры"),
            { host.appearanceState.genreCardOpacity },
            { host.appearanceState.genreCardOpacity = it },
        )
        addControl(
            controls,
            host.tr("Artists", "Исполнители"),
            { host.appearanceState.artistCardOpacity },
            { host.appearanceState.artistCardOpacity = it },
        )
        addControl(
            controls,
            host.tr("Albums", "Альбомы"),
            { host.appearanceState.albumCardOpacity },
            { host.appearanceState.albumCardOpacity = it },
        )
        addControl(
            controls,
            host.tr("Settings", "Настройки"),
            { host.appearanceState.settingsCardOpacity },
            { host.appearanceState.settingsCardOpacity = it },
        )
        addControl(
            controls,
            host.tr("Mini-player", "Мини-плеер"),
            { host.appearanceState.miniPlayerCardOpacity },
            { host.appearanceState.miniPlayerCardOpacity = it },
        )
        addControl(
            controls,
            host.tr("Application header", "Шапка приложения"),
            { host.appearanceState.headerCardOpacity },
            { host.appearanceState.headerCardOpacity = it },
        )
        addControl(
            controls,
            host.tr("Dialogs", "Диалоговые окна"),
            { host.appearanceState.dialogCardOpacity },
            { host.appearanceState.dialogCardOpacity = it },
        )

        val scroll = ScrollView(host)
        scroll.addView(controls, FrameLayout.LayoutParams(-1, -2))
        panel.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val done = host.uiFactory.button(host.tr("Done", "Готово"))
        host.uiFactory.applyPrimaryButtonStyle(done)
        done.setOnClickListener {
            host.saveState()
            host.overlayHost.removeView(shade)
            host.rebuildUi()
        }
        panel.addView(done, LinearLayout.LayoutParams(-1, host.dp(48)))
        val maxHeight = host.resources.displayMetrics.heightPixels - host.dp(96)
        shade.addView(panel, host.centerParams(host.dp(350), min(host.dp(600), maxHeight)))
        host.overlayHost.addView(shade)
        host.playerUiController.updateMini()
    }

    private fun addControl(
        panel: LinearLayout,
        title: String,
        getValue: () -> Int,
        setValue: (Int) -> Unit,
    ) {
        val label = host.uiFactory.text(labelText(title, getValue()), 14, true)
        panel.addView(label, LinearLayout.LayoutParams(-1, host.dp(24)))

        val seek = SeekBar(host).apply {
            max = MAX_OPACITY - MIN_OPACITY
            progress = getValue() - MIN_OPACITY
        }
        host.uiFactory.applySeekBarColors(seek)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    setValue(MIN_OPACITY + progress)
                    label.text = labelText(title, getValue())
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                host.saveState()
            }
        })
        panel.addView(seek, LinearLayout.LayoutParams(-1, host.dp(32)))
    }

    private fun labelText(title: String, value: Int): String = "$title: $value%"

    companion object {
        private const val MIN_OPACITY = 35
        private const val MAX_OPACITY = 100
    }
}
