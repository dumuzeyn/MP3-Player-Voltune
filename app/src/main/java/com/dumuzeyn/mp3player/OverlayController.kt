package com.dumuzeyn.mp3player

import android.text.InputFilter
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView

internal class OverlayController(private val host: MainActivityCore) : AutoCloseable {
    fun interface SelectionDone {
        fun done(selected: Set<String>)
    }

    private val queueController = QueueOverlayController(host, this)
    private val searchController = GlobalSearchOverlayController(host, this)
    private val selectionController = TrackSelectionOverlayController(host)
    private val deletionController = TrackDeletionController(host)

    fun openGroup(title: String, tracks: ArrayList<Track>) {
        openTrackPanel(title, tracks, null)
    }

    fun openPlaylist(playlist: Playlist) {
        openTrackPanel(playlist.name, host.playlistController.playlistTracks(playlist), playlist)
    }

    private fun openTrackPanel(title: String, tracks: ArrayList<Track>, playlist: Playlist?) {
        val shade = host.uiFactory.shade()
        val panel = host.uiFactory.panelCard()
        val header = host.uiFactory.row()
        header.addView(
            host.uiFactory.text(title, 20, true),
            LinearLayout.LayoutParams(0, host.dp(58), 1f),
        )
        val play = host.uiFactory.icon(
            if (host.playbackQueueController.isPlayingSource(tracks)) "Ⅱ" else "▶",
        )
        play.setOnClickListener {
            if (host.playbackQueueController.isPlayingSource(tracks)) {
                host.playbackQueueController.toggleOrStart()
            } else {
                host.playbackQueueController.playList(tracks, false)
            }
        }
        val shuffle = host.uiFactory.shuffleButton()
        shuffle.setOnClickListener { host.playbackQueueController.playList(tracks, true) }
        header.addView(shuffle, host.uiFactory.square(52))
        header.addView(play, host.uiFactory.square(52))
        if (playlist != null) {
            val add = host.uiFactory.icon("+")
            add.setOnClickListener {
                host.overlayHost.removeView(shade)
                openAddToPlaylist(playlist)
            }
            header.addView(add, host.uiFactory.square(52))
        }
        val close = host.uiFactory.icon("×")
        close.setOnClickListener { close(shade) }
        header.addView(close, host.uiFactory.square(52))
        panel.addView(header)

        val rows = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL }
        tracks.forEach { track ->
            rows.addView(trackPanelRow(track, tracks, playlist, shade, title))
        }
        val scroll = ScrollView(host).apply { addView(rows) }
        panel.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        shade.addView(panel, host.bottomParams())
        host.overlayHost.addView(shade)
        host.playerUiController.updateMini()
    }

    private fun compactSongActionsParams(): FrameLayout.LayoutParams {
        val availableWidth = host.resources.displayMetrics.widthPixels - host.dp(28)
        val width = minOf(host.dp(420), maxOf(host.dp(280), availableWidth))
        return FrameLayout.LayoutParams(
            width,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
        ).apply {
            setMargins(host.dp(14), 0, host.dp(14), host.dp(14))
        }
    }

    private fun trackPanelRow(
        track: Track,
        source: ArrayList<Track>,
        playlist: Playlist?,
        shade: FrameLayout,
        title: String,
    ): View = LinearLayout(host).apply {
        orientation = LinearLayout.VERTICAL
        addView(
            host.songsRenderer.songRow(
                track,
                true,
                true,
                {
                    host.overlayHost.removeView(shade)
                    if (playlist == null) openGroup(title, source) else openPlaylist(playlist)
                },
                {
                    host.overlayHost.removeView(shade)
                    openSongActions(track, playlist)
                },
            ),
        )
    }

    fun openQueue() {
        queueController.open()
    }

    fun refreshPlayback() {
        queueController.refreshPlayback()
    }

    fun openAddFavorites() {
        openSelection(host.tr("Add to favorites", "Добавить в избранное"), HashSet()) { selected ->
            host.libraryState.favorites.addAll(selected)
            host.saveLibraryState()
            host.librarySnapshotApplier.rebuildDerivedAndRender()
        }
    }

    private fun openAddToPlaylist(playlist: Playlist) {
        openSelection(host.tr("Add to ", "Добавить в ") + playlist.name, HashSet()) { selected ->
            host.playlistController.addTracksToPlaylist(playlist, selected)
            host.overlayHost.removeAllViews()
            openPlaylist(playlist)
        }
    }

    fun openSelection(title: String, selected: HashSet<String>, done: SelectionDone) {
        selectionController.open(title, selected, done)
    }

    fun openSongActions(track: Track) {
        openSongActions(track, null)
    }

    fun openSongActions(track: Track, sourcePlaylist: Playlist?) {
        val shade = host.uiFactory.shade()
        val panel = host.uiFactory.panelCard()
        panel.setPadding(host.dp(12), host.dp(10), host.dp(12), host.dp(10))
        val title = host.uiFactory.text(track.title, 19, true).apply {
            setTextColor(host.purple)
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setPadding(host.dp(12), 0, host.dp(12), 0)
        }
        panel.addView(title, LinearLayout.LayoutParams(-1, host.dp(42)))
        addCompactPanelButton(
            panel,
            if (track.uri in host.libraryState.favorites) {
                host.tr("Remove from favorites", "Убрать из избранного")
            } else {
                host.tr("Add to favorites", "Добавить в избранное")
            },
        ) {
            host.toggleFavorite(track)
            close(shade)
        }
        addCompactPanelButton(panel, host.tr("Add to playlist", "Добавить в плейлист")) {
            host.overlayHost.removeView(shade)
            choosePlaylist(track)
        }
        addCompactPanelButton(panel, host.tr("Play next", "Играть следующим")) {
            host.playbackQueueController.playNext(track)
            close(shade)
        }
        addCompactPanelButton(panel, host.tr("Add to end of queue", "В конец очереди")) {
            host.playbackQueueController.add(track)
            close(shade)
        }
        addCompactPanelButton(panel, host.tr("Edit metadata", "Изменить метаданные")) {
            close(shade)
            host.metadataEditorController.open(track)
        }
        if (sourcePlaylist != null) {
            addCompactPanelButton(
                panel,
                host.tr("Remove from playlist", "Убрать из плейлиста"),
            ) {
                sourcePlaylist.uris.remove(track.uri)
                host.saveLibraryState()
                host.overlayHost.removeView(shade)
                openPlaylist(sourcePlaylist)
            }
        }
        addCompactPanelButton(panel, host.tr("Remove from library", "Убрать из медиатеки")) {
            host.overlayHost.removeView(shade)
            confirmRemoveTrack(track)
        }
        if (deletionController.canDeleteFile(track)) {
            addCompactPanelButton(
                panel,
                host.tr("Delete file from device", "Удалить файл с устройства"),
            ) {
                host.overlayHost.removeView(shade)
                confirmDeleteFile(track)
            }
        }
        addCompactPanelButton(panel, host.tr("Close", "Закрыть")) { close(shade) }
        shade.addView(panel, compactSongActionsParams())
        host.overlayHost.addView(shade)
        host.playerUiController.updateMini()
    }

    private fun choosePlaylist(track: Track) {
        openCollectionChooser(track, false)
    }

    fun chooseCollection(track: Track) {
        openCollectionChooser(track, true)
    }

    private fun openCollectionChooser(track: Track, includeFavorites: Boolean) {
        val shade = host.uiFactory.shade()
        val panel = host.uiFactory.panelCard()
        panel.addView(
            host.uiFactory.dialogTitle(
                if (includeFavorites) {
                    host.tr("Save track", "Сохранить песню")
                } else {
                    host.tr("Add to playlist", "Добавить в плейлист")
                },
            ),
            host.uiFactory.dialogTitleParams(),
        )
        val rows = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL }
        if (includeFavorites) {
            addPanelButton(
                rows,
                if (track.uri in host.libraryState.favorites) {
                    host.tr("Remove from favorites", "Убрать из избранного")
                } else {
                    host.tr("Add to favorites", "Добавить в избранное")
                },
            ) {
                host.toggleFavorite(track)
                close(shade)
                if (host.navigationState.tabIndex == LibraryTabs.FAVORITES) host.render()
                host.playerUiController.syncPlaybackUi()
            }
        }
        host.libraryState.playlists.forEach { playlist ->
            val label = if (track.uri in playlist.uris) {
                playlist.name + " " + host.tr("(added)", "(добавлено)")
            } else {
                playlist.name
            }
            addPanelButton(rows, label) {
                host.playlistController.addTrackToPlaylist(playlist, track)
                close(shade)
                if (host.navigationState.tabIndex == LibraryTabs.PLAYLISTS) host.render()
                host.playerUiController.syncPlaybackUi()
            }
        }
        addPanelButton(rows, host.tr("Create new", "Создать новый")) {
            host.overlayHost.removeView(shade)
            showInput(
                host.tr("New playlist", "Новый плейлист"),
                host.tr("Playlist name", "Название плейлиста"),
                "",
                false,
            ) { value ->
                host.playlistController.createPlaylistWithTrack(value, track)
                if (host.navigationState.tabIndex == LibraryTabs.PLAYLISTS) host.render()
            }
        }
        val scroll = ScrollView(host).apply { addView(rows) }
        panel.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        shade.addView(panel, host.centerParams(host.dp(330), host.dp(420)))
        host.overlayHost.addView(shade)
    }

    fun createPlaylist() {
        showInput(
            host.tr("Create playlist", "Создать плейлист"),
            host.tr("Playlist name", "Название плейлиста"),
            "",
            false,
        ) { value ->
            host.playlistController.createPlaylist(value)
            if (host.navigationState.tabIndex == LibraryTabs.PLAYLISTS) host.render()
        }
    }

    fun renamePlaylist(playlist: Playlist) {
        showInput(
            host.tr("Rename playlist", "Переименовать плейлист"),
            host.tr("Playlist name", "Название плейлиста"),
            playlist.name,
            false,
        ) { value ->
            host.playlistController.renamePlaylist(playlist, value)
            host.render()
        }
    }

    fun confirmDeletePlaylist(playlist: Playlist) {
        host.showConfirmPanel(
            host.tr("Delete playlist?", "Удалить плейлист?"),
            host.tr("Songs will stay in the app.", "Песни останутся в приложении."),
        ) {
            host.playlistController.deletePlaylist(playlist)
            host.render()
        }
    }

    private fun confirmRemoveTrack(track: Track) {
        host.showConfirmPanel(
            host.tr("Remove from library?", "Убрать из медиатеки?"),
            host.tr(
                "The song will disappear from the app, but the file will stay on the phone.",
                "Песня исчезнет из приложения, но файл останется на телефоне.",
            ),
        ) { host.playbackQueueController.removeFromLibrary(track) }
    }

    private fun confirmDeleteFile(track: Track) {
        host.showConfirmPanel(
            host.tr("Delete file from device?", "Удалить файл с устройства?"),
            host.tr(
                "This cannot be undone. The song will also be removed from the library.",
                "Это действие нельзя отменить. Песня также исчезнет из медиатеки.",
            ),
        ) { deletionController.deleteFile(track) }
    }

    fun handleActivityResult(requestCode: Int, resultCode: Int): Boolean =
        deletionController.handleActivityResult(requestCode, resultCode)

    fun openSearch() {
        searchController.open()
    }

    fun showInput(
        title: String,
        hint: String,
        value: String,
        numeric: Boolean,
        done: MainActivityCore.InputDone,
    ) {
        val shade = host.uiFactory.shade()
        val panel = host.uiFactory.panelCard()
        panel.setPadding(host.dp(16), host.dp(16), host.dp(16), host.dp(16))
        panel.addView(host.uiFactory.dialogTitle(title), host.uiFactory.dialogTitleParams())
        val input = searchField(hint).apply {
            setText(value)
            setSelection(length())
            inputType = if (numeric) 2 else 1
        }
        panel.addView(input, searchParams())
        val actions = host.uiFactory.row()
        val cancel = host.uiFactory.button(host.tr("Cancel", "Отмена"))
        cancel.setOnClickListener { close(shade) }
        actions.addView(cancel, LinearLayout.LayoutParams(0, host.dp(54), 1f))
        val save = host.uiFactory.button(host.tr("Done", "Готово"))
        host.uiFactory.applyPrimaryButtonStyle(save)
        save.setOnClickListener {
            val result = input.text.toString()
            close(shade)
            done.done(result)
        }
        actions.addView(save, LinearLayout.LayoutParams(0, host.dp(54), 1f))
        panel.addView(actions)
        shade.addView(panel, host.centerParams(host.dp(330), -2))
        host.overlayHost.addView(shade)
        input.requestFocus()
    }

    private fun searchField(hint: String): EditText = EditText(host).apply {
        setSingleLine(true)
        this.hint = hint
        setTextColor(host.fg)
        setHintTextColor(host.muted)
        textSize = 16f
        setPadding(host.dp(14), 0, host.dp(14), 0)
        filters = arrayOf(InputFilter.LengthFilter(80))
        host.uiFactory.setSurface(this, host.panel, true)
    }

    private fun searchParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(-1, host.dp(58)).apply {
            setMargins(0, host.dp(8), 0, host.dp(12))
        }

    private fun addPanelButton(panel: LinearLayout, label: String, action: () -> Unit) {
        val button = host.uiFactory.button(label)
        button.setOnClickListener { action() }
        panel.addView(button, LinearLayout.LayoutParams(-1, host.dp(54)))
    }

    private fun addCompactPanelButton(panel: LinearLayout, label: String, action: () -> Unit) {
        val button: Button = host.uiFactory.button(label)
        button.textSize = 16f
        button.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        button.setPadding(host.dp(12), 0, host.dp(12), 0)
        button.setOnClickListener { action() }
        panel.addView(
            button,
            LinearLayout.LayoutParams(-1, host.dp(46)).apply {
                setMargins(0, host.dp(1), 0, host.dp(1))
            },
        )
    }

    private fun close(shade: FrameLayout) {
        if (shade.parent != null) host.overlayHost.removeView(shade)
        host.playerUiController.updateMini()
    }

    override fun close() {
        deletionController.close()
    }
}
