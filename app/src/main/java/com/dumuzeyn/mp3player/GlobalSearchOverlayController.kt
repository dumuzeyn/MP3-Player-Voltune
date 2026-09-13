package com.dumuzeyn.mp3player

import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView

internal class GlobalSearchOverlayController(
    private val host: MainActivityCore,
    private val overlays: OverlayController,
) {
    fun open() {
        val shade = host.uiFactory.shade()
        val panel = host.uiFactory.panelCard().apply {
            setPadding(host.dp(14), host.dp(12), host.dp(14), host.dp(12))
            addView(header(shade))
        }
        val input = EditText(host).apply {
            hint = host.tr(
                "Songs, artists, albums, genres, playlists",
                "Песни, исполнители, альбомы, жанры, плейлисты",
            )
            setSingleLine(true)
            setText(host.navigationState.search)
            setTextColor(host.fg)
            setHintTextColor(host.muted)
        }
        panel.addView(input, LinearLayout.LayoutParams(-1, host.dp(54)))
        val results = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(host).apply { addView(results) }
        panel.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        input.addTextChangedListener(watcher(results, shade))
        shade.addView(panel, host.bottomParams())
        host.overlayHost.addView(shade)
        input.requestFocus()
        submit(results, shade, input.text.toString())
    }

    private fun header(shade: FrameLayout): LinearLayout = host.uiFactory.row().apply {
        addView(
            host.uiFactory.text(host.tr("Search", "Поиск"), 22, true),
            LinearLayout.LayoutParams(0, host.dp(52), 1f),
        )
        val close = host.uiFactory.icon("×").apply {
            contentDescription = host.tr("Close", "Закрыть")
            setOnClickListener { close(shade) }
        }
        addView(close, host.uiFactory.square(48))
    }

    private fun watcher(results: LinearLayout, shade: FrameLayout): TextWatcher =
        object : TextWatcher {
            override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun afterTextChanged(text: Editable?) = Unit

            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {
                submit(results, shade, text?.toString().orEmpty())
            }
        }

    private fun submit(results: LinearLayout, shade: FrameLayout, query: String) {
        host.globalSearchController.search(
            host.libraryState.tracks,
            host.libraryState.playlists,
            query,
        ) { result -> render(results, shade, result) }
    }

    private fun render(target: LinearLayout, shade: FrameLayout, result: GlobalSearchResult) {
        target.removeAllViews()
        addTracks(target, shade, host.tr("Songs", "Песни"), result.songs)
        addGroups(target, shade, host.tr("Artists", "Исполнители"), result.artists) { it.artist }
        addGroups(target, shade, host.tr("Albums", "Альбомы"), result.albums) { it.album }
        addGroups(target, shade, host.tr("Genres", "Жанры"), result.genres) { it.genre }
        if (result.playlists.isNotEmpty()) {
            addHeading(target, host.tr("Playlists", "Плейлисты"))
            result.playlists.forEach { playlist ->
                addButton(target, playlist.name) {
                    close(shade)
                    overlays.openPlaylist(playlist)
                }
            }
        }
    }

    private fun addTracks(
        target: LinearLayout,
        shade: FrameLayout,
        title: String,
        tracks: List<Track>,
    ) {
        if (tracks.isEmpty()) return
        addHeading(target, title)
        tracks.forEach { track ->
            target.addView(host.songsRenderer.songRow(track, true, false) { close(shade) })
        }
    }

    private fun addGroups(
        target: LinearLayout,
        shade: FrameLayout,
        title: String,
        values: List<String>,
        valueProvider: GroupValue,
    ) {
        if (values.isEmpty()) return
        addHeading(target, title)
        values.forEach { value ->
            addButton(target, value) {
                close(shade)
                overlays.openGroup(value, groupTracks(value, valueProvider))
            }
        }
    }

    private fun groupTracks(value: String, valueProvider: GroupValue): ArrayList<Track> {
        val expected = Track.normalizeSearchText(value)
        return host.libraryState.tracks.filterTo(ArrayList()) { track ->
            expected == Track.normalizeSearchText(valueProvider.value(track))
        }
    }

    private fun addHeading(target: LinearLayout, value: String) {
        target.addView(
            host.uiFactory.text(value, 15, true),
            LinearLayout.LayoutParams(-1, host.dp(38)),
        )
    }

    private fun addButton(target: LinearLayout, label: String, action: () -> Unit) {
        val button: Button = host.uiFactory.button(label).apply {
            setOnClickListener { action() }
        }
        target.addView(button, LinearLayout.LayoutParams(-1, host.dp(50)))
    }

    private fun close(shade: FrameLayout) {
        host.globalSearchController.cancel()
        if (shade.parent != null) host.overlayHost.removeView(shade)
        host.playerUiController.updateMini()
    }

    private fun interface GroupValue {
        fun value(track: Track): String
    }
}
