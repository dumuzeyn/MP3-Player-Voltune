package com.dumuzeyn.mp3player

import android.net.Uri
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/** Safe library metadata editor; source audio bytes are never modified. */
internal class MetadataEditorController(private val host: MainActivityCore) : AutoCloseable {
    private val executor = Executors.newSingleThreadExecutor()

    @Volatile
    private var closed = false

    fun open(track: Track) {
        val shade = host.uiFactory.shade()
        val panel = host.uiFactory.panelCard()
        val header = host.uiFactory.row()
        header.addView(
            host.uiFactory.text(host.tr("Metadata", "Метаданные"), 21, true),
            LinearLayout.LayoutParams(0, host.dp(54), 1f),
        )
        val close = host.uiFactory.icon("×").apply {
            contentDescription = host.tr("Close", "Закрыть")
            setOnClickListener { close(shade) }
        }
        header.addView(close, host.uiFactory.square(50))
        panel.addView(header)

        val scroll = ScrollView(host)
        val fields = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL }
        fields.addView(
            host.uiFactory.text(
                host.tr(
                    "Edits apply to the Voltune library. The source file stays unchanged.",
                    "Изменения применяются к библиотеке Voltune. Исходный файл не изменяется.",
                ),
                14,
                false,
            ),
        )
        val title = field(fields, host.tr("Title", "Название"), track.title, false)
        val artist = field(fields, host.tr("Artist", "Исполнитель"), track.artist, false)
        val album = field(fields, host.tr("Album", "Альбом"), track.album, false)
        val albumArtist = field(
            fields,
            host.tr("Album artist", "Исполнитель альбома"),
            track.albumArtist,
            false,
        )
        val genre = field(fields, host.tr("Genre", "Жанр"), track.genre, false)
        val year = field(fields, host.tr("Year", "Год"), number(track.year), true)
        val trackNumber = field(
            fields,
            host.tr("Track number", "Номер трека"),
            number(track.trackNumber),
            true,
        )
        val discNumber = field(
            fields,
            host.tr("Disc number", "Номер диска"),
            number(track.discNumber),
            true,
        )
        fields.addView(host.uiFactory.text(readOnlyDetails(track), 14, false))
        val save = host.uiFactory.button(host.tr("Save in library", "Сохранить в библиотеке"))
        host.uiFactory.applyPrimaryButtonStyle(save)
        save.setOnClickListener {
            val updated = track.withMetadata(
                required(title, track.title),
                required(artist, track.artist),
                required(album, track.album),
                required(albumArtist, artist.text.toString()),
                required(genre, track.genre),
                MetadataValidator.year(year.text.toString()),
                MetadataValidator.trackNumber(trackNumber.text.toString()),
                MetadataValidator.trackNumber(discNumber.text.toString()),
            )
            apply(updated)
            close(shade)
        }
        fields.addView(save, LinearLayout.LayoutParams(-1, host.dp(54)))
        scroll.addView(fields)
        panel.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        shade.addView(panel, host.bottomParams())
        host.overlayHost.addView(shade)
    }

    fun applyBatch(tracks: List<Track>, artist: String, album: String, genre: String) {
        val updates = ArrayList<Track>()
        for (track in ArrayList(tracks)) {
            updates += track.withMetadata(
                track.title,
                emptyKeeps(artist, track.artist),
                emptyKeeps(album, track.album),
                track.albumArtist,
                emptyKeeps(genre, track.genre),
                track.year,
                track.trackNumber,
                track.discNumber,
            )
        }
        applyMany(updates)
    }

    fun openBatchSelection() {
        host.overlayController.openSelection(
            host.tr("Select tracks to edit", "Выберите треки для изменения"),
            HashSet(),
        ) { selected ->
            val tracks = ArrayList<Track>()
            for (uri in selected) {
                host.findTrack(uri)?.let(tracks::add)
            }
            if (tracks.isNotEmpty()) openBatchEditor(tracks)
        }
    }

    override fun close() {
        closed = true
        executor.shutdownNow()
    }

    private fun apply(updated: Track) = applyMany(listOf(updated))

    private fun applyMany(updates: List<Track>) {
        val persisted = ArrayList<Track>()
        for (updated in updates) {
            if (replaceInMemory(updated)) persisted += updated
        }
        if (persisted.isEmpty()) return
        host.libraryRepository.reindex()
        host.librarySnapshotApplier.rebuildDerivedAndRender()
        try {
            executor.execute {
                TrackStore.updateMetadata(host.applicationContext, persisted)
            }
        } catch (_: RejectedExecutionException) {
            // Activity is already closing.
        }
    }

    private fun replaceInMemory(updated: Track): Boolean {
        val index = indexOf(updated.trackId)
        if (index < 0) return false
        host.libraryState.tracks[index] = updated
        for (queueIndex in host.playbackUiState.queue.indices) {
            if (updated.trackId == host.playbackUiState.queue[queueIndex].trackId) {
                host.playbackUiState.queue[queueIndex] = updated
            }
        }
        return true
    }

    private fun openBatchEditor(tracks: List<Track>) {
        val shade = host.uiFactory.shade()
        val panel = host.uiFactory.panelCard()
        val header = host.uiFactory.row()
        header.addView(
            host.uiFactory.text(host.tr("Batch metadata", "Массовые метаданные"), 20, true),
            LinearLayout.LayoutParams(0, host.dp(54), 1f),
        )
        val close = host.uiFactory.icon("×").apply {
            setOnClickListener { close(shade) }
        }
        header.addView(close, host.uiFactory.square(50))
        panel.addView(header)
        val fields = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL }
        fields.addView(
            host.uiFactory.text(
                host.tr(
                    "Blank fields keep their current values. Source files stay unchanged.",
                    "Пустые поля сохраняют текущие значения. Исходные файлы не изменяются.",
                ),
                14,
                false,
            ),
        )
        val artist = field(fields, host.tr("Artist", "Исполнитель"), "", false)
        val album = field(fields, host.tr("Album", "Альбом"), "", false)
        val genre = field(fields, host.tr("Genre", "Жанр"), "", false)
        val save = host.uiFactory.button(host.tr("Apply", "Применить"))
        host.uiFactory.applyPrimaryButtonStyle(save)
        save.setOnClickListener {
            applyBatch(tracks, artist.text.toString(), album.text.toString(), genre.text.toString())
            close(shade)
        }
        fields.addView(save, LinearLayout.LayoutParams(-1, host.dp(54)))
        panel.addView(fields, LinearLayout.LayoutParams(-1, -2))
        shade.addView(panel, host.bottomParams())
        host.overlayHost.addView(shade)
    }

    private fun indexOf(trackId: String): Int {
        for (index in host.libraryState.tracks.indices) {
            if (trackId == host.libraryState.tracks[index].trackId) return index
        }
        return -1
    }

    private fun field(parent: LinearLayout, hint: String, value: String, numeric: Boolean): EditText {
        val input = EditText(host).apply {
            this.hint = hint
            setText(value)
            setTextColor(host.fg)
            setHintTextColor(host.muted)
            setSingleLine(true)
            inputType = if (numeric) {
                InputType.TYPE_CLASS_NUMBER
            } else {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            }
        }
        parent.addView(input, LinearLayout.LayoutParams(-1, host.dp(52)))
        return input
    }

    private fun readOnlyDetails(track: Track): String {
        val source = try {
            Uri.parse(track.uri).authority ?: host.tr("Local source", "Локальный источник")
        } catch (_: RuntimeException) {
            host.tr("Unavailable source", "Недоступный источник")
        }
        return host.tr("Read only", "Только чтение") + ":\n" +
            host.tr("Duration", "Длительность") + ": " + host.formatTrackDuration(track) +
            "\n" + host.tr("Format", "Формат") + ": " + extension(track.uri) +
            "\n" + host.tr("Size", "Размер") + ": " + size(track.fileSize) +
            "\n" + host.tr("Source", "Источник") + ": " + source +
            "\n" + host.tr(
                "Artwork and bitrate depend on the source provider.",
                "Обложка и bitrate зависят от исходного provider.",
            )
    }

    private fun required(field: EditText, fallback: String): String =
        MetadataValidator.cleanText(field.text.toString()).ifEmpty { fallback }

    private fun emptyKeeps(value: String, fallback: String): String =
        MetadataValidator.cleanText(value).ifEmpty { fallback }

    private fun number(value: Int): String = if (value <= 0) "" else value.toString()

    private fun extension(uri: String?): String {
        val dot = uri?.lastIndexOf('.') ?: -1
        return if (dot < 0) {
            "Unknown"
        } else {
            uri!!.substring(dot + 1).replace(NON_ALPHANUMERIC, "").uppercase(Locale.ROOT)
        }
    }

    private fun size(bytes: Long): String = if (bytes < 0L) {
        "Unknown"
    } else {
        String.format(Locale.ROOT, "%.1f MB", bytes / 1024.0 / 1024.0)
    }

    private fun close(shade: FrameLayout) {
        if (shade.parent != null) host.overlayHost.removeView(shade)
        host.playerUiController.updateMini()
    }

    private companion object {
        val NON_ALPHANUMERIC = Regex("[^A-Za-z0-9]")
    }
}
