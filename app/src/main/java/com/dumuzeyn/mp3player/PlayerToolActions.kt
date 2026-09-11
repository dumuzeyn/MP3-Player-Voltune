package com.dumuzeyn.mp3player

import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import java.util.Locale

/** Quick actions share the playback session's remembered collection. */
internal class PlayerToolActions(private val host: MainActivityCore) {
    private val preferences get() = host.getSharedPreferences("player_tool_session", 0)

    private fun collection(): Playlist? {
        val name = preferences.getString("collection", null) ?: return null
        return host.libraryState.playlists.firstOrNull { it.name == name }
    }

    fun isSaved(track: Track): Boolean =
        collection()?.uris?.contains(track.uri) ?: host.libraryState.favorites.contains(track.uri)

    fun toggleSaved(track: Track) {
        val playlist = collection()
        if (playlist == null) {
            host.toggleFavorite(track)
        } else {
            if (!playlist.uris.remove(track.uri)) playlist.uris.add(track.uri)
            host.saveLibraryState()
            host.librarySnapshotApplier.rebuildDerivedAndRender()
        }
        host.playerUiController.syncPlaybackUi()
    }

    fun chooseCollection(track: Track) {
        val options = mutableListOf<Pair<String, () -> Unit>>()
        options += host.tr("Favorites", "Избранное") to {
            preferences.edit().remove("collection").apply()
            if (!isSaved(track)) toggleSaved(track)
        }
        host.libraryState.playlists.forEach { playlist ->
            options += playlist.name to {
                preferences.edit().putString("collection", playlist.name).apply()
                if (!isSaved(track)) toggleSaved(track)
            }
        }
        options += host.tr("Create playlist", "Создать плейлист") to {
            host.overlayController.showInput(
                host.tr("New playlist", "Новый плейлист"),
                host.tr("Name", "Название"), "", false,
            ) { name ->
                val playlist = host.playlistController.createPlaylistWithTrack(name, track)
                preferences.edit().putString("collection", playlist.name).apply()
                host.playerUiController.syncPlaybackUi()
            }
        }
        choose(host.tr("Add to", "Добавлять в"), options)
    }

    fun toggleTimer() {
        if (host.playbackUiState.sleepTimerEndsAt > System.currentTimeMillis()) {
            host.sleepTimerController.cancel()
        } else {
            host.sleepTimerController.start(host.appearanceState.customTimerMinutes)
        }
    }

    fun chooseRepeat() {
        val labels = listOf(
            host.tr("Repeat off", "Без повтора"),
            host.tr("Repeat one", "Повтор песни"),
            host.tr("Repeat all", "Повтор списка"),
        )
        choose(host.tr("Repeat", "Повтор"), labels.mapIndexed { mode, label ->
            label to { host.playbackController.setRepeatMode(mode); Unit }
        })
    }

    fun speedText(): String = String.format(Locale.ROOT, "%.2f×", host.playbackController.playbackSpeed())

    fun toggleSpeed() {
        val speed = host.playbackController.playbackSpeed()
        val selected = preferences.getFloat("speed", 1.25f)
        host.playbackController.setPlaybackSpeed(PlaybackSpeedPolicy.toggle(speed, selected))
    }

    fun chooseSpeed() {
        val shade = host.uiFactory.shade()
        val panel = host.uiFactory.panelCard()
        panel.addView(host.uiFactory.dialogTitle(host.tr("Playback speed", "Скорость воспроизведения")),
            host.uiFactory.dialogTitleParams())
        val content = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL }
        panel.addView(ScrollView(host).apply { addView(content) }, LinearLayout.LayoutParams(-1, -2, 1f))
        var selected = PlaybackSpeedPolicy.constrain(preferences.getFloat("speed", 1.25f))
        val label = host.uiFactory.text(String.format(Locale.ROOT, "%.2f×", selected), 22, true)
        label.gravity = android.view.Gravity.CENTER
        content.addView(label, LinearLayout.LayoutParams(-1, host.dp(44)))
        val slider = SeekBar(host).apply {
            contentDescription = host.tr("Speed multiplier", "Коэффициент скорости")
            max = PlaybackSpeedPolicy.STEPS
            progress = PlaybackSpeedPolicy.progress(selected)
        }
        host.uiFactory.applySeekBarColors(slider)
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) {
                if (!fromUser) return
                selected = PlaybackSpeedPolicy.fromProgress(value)
                label.text = String.format(Locale.ROOT, "%.2f×", selected)
            }
            override fun onStartTrackingTouch(bar: SeekBar) = Unit
            override fun onStopTrackingTouch(bar: SeekBar) = Unit
        })
        content.addView(slider, LinearLayout.LayoutParams(-1, host.dp(48)))
        val limits = host.uiFactory.row()
        limits.addView(host.uiFactory.text("0.25×", 13, false), LinearLayout.LayoutParams(0, -2, 1f))
        limits.addView(host.uiFactory.text("4.00×", 13, false).apply {
            gravity = android.view.Gravity.END
        }, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(limits)
        panel.addView(host.uiFactory.button(host.tr("Apply", "Применить")).apply {
            host.uiFactory.applyPrimaryButtonStyle(this)
            setOnClickListener {
                preferences.edit().putFloat("speed", selected).apply()
                host.playbackController.setPlaybackSpeed(selected)
                host.overlayHost.removeView(shade)
                host.playerUiController.syncPlaybackUi()
            }
        }, LinearLayout.LayoutParams(-1, host.dp(48)))
        shade.addView(panel, host.centerParams(host.dp(340), -2))
        host.overlayHost.addView(shade)
    }

    private fun choose(title: String, options: List<Pair<String, () -> Unit>>) {
        val shade = host.uiFactory.shade()
        val panel = host.uiFactory.panelCard()
        panel.addView(host.uiFactory.dialogTitle(title), host.uiFactory.dialogTitleParams())
        val rows = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL }
        options.forEach { (label, action) ->
            rows.addView(host.uiFactory.button(label).apply {
                setOnClickListener {
                    host.overlayHost.removeView(shade)
                    action()
                    host.playerUiController.syncPlaybackUi()
                }
            }, LinearLayout.LayoutParams(-1, host.dp(52)))
        }
        panel.addView(ScrollView(host).apply { addView(rows) }, LinearLayout.LayoutParams(-1, 0, 1f))
        shade.addView(panel, host.centerParams(host.dp(340), host.dp(420)))
        host.overlayHost.addView(shade)
    }
}
