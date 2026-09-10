package com.dumuzeyn.mp3player

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import java.io.File
import java.util.ArrayDeque
import java.util.concurrent.Executors

internal class AudioEditorController(private val host: MainActivityCore) : AutoCloseable {
    private val store by lazy { AudioEditStore(host) }
    private val exporter by lazy { AudioEditExporter(host) }
    private val waveformRepository = lazy { AudioWaveformRepository(host) }
    val waveforms get() = waveformRepository.value
    private val previewController = lazy { AudioEditorPreviewController(host, ::render) }
    val preview get() = previewController.value
    private val files = Executors.newSingleThreadExecutor()
    private val undo = ArrayDeque<AudioEditProject>()
    private val redo = ArrayDeque<AudioEditProject>()
    var project: AudioEditProject = AudioEditProject()
        private set
    private var loaded = false
    private var closed = false
    private var working = false
    val busy get() = working || (previewController.isInitialized() && preview.active)
    var exporting = false
        private set
    var status = ""
        private set
    var progress = -1
        private set
    private var readyFile: File? = null
    var onProgress: (() -> Unit)? = null
    val canUndo get() = undo.isNotEmpty() && !busy
    val canRedo get() = redo.isNotEmpty() && !busy
    val canSave get() = readyFile?.isFile == true && !busy

    fun load() {
        if (loaded) return
        loaded = true
        project = store.load()
        val saved = host.getSharedPreferences("audio_editor", 0).getString("export", null)
        readyFile = saved?.let { File(host.cacheDir, it) }?.takeIf {
            it.parentFile?.canonicalFile == host.cacheDir.canonicalFile && it.isFile
        }
    }

    fun change(operation: (AudioEditProject) -> AudioEditProject): Boolean {
        load()
        if (busy) return false
        return try {
            val next = operation(project)
            if (next == project) return true
            undo.addLast(project)
            while (undo.size > 32) undo.removeFirst()
            redo.clear()
            project = next
            status = ""
            store.save(project)
            render()
            true
        } catch (_: IllegalArgumentException) {
            message(host.tr("Check the range and overlapping clips on this lane",
                "Проверьте границы и пересечения фрагментов на дорожке"))
            false
        }
    }

    fun add(track: Track, lane: Int) {
        if (track.durationMs <= 0) {
            message(host.tr("Track duration is unavailable", "Длительность трека неизвестна"))
            return
        }
        change { it.append(AudioEditClip(uri = track.uri, title = track.title,
            sourceDurationMs = track.durationMs.toLong()), lane) }
    }

    fun undo() = restore(undo, redo)
    fun redo() = restore(redo, undo)

    private fun restore(from: ArrayDeque<AudioEditProject>, to: ArrayDeque<AudioEditProject>) {
        if (busy || from.isEmpty()) return
        to.addLast(project)
        project = from.removeLast()
        store.save(project)
        render()
    }

    fun export() {
        if (busy || project.clips.isEmpty()) return
        working = true
        exporting = true
        status = host.tr("Exporting M4A", "Экспорт M4A")
        render()
        exporter.export(project, { value ->
            progress = value
            onProgress?.invoke()
        }) { result ->
            working = false
            exporting = false
            progress = -1
            result.fold(onSuccess = { file ->
                readyFile?.delete()
                readyFile = file
                host.getSharedPreferences("audio_editor", 0).edit().putString("export", file.name).apply()
                status = host.tr("M4A is ready to save", "M4A готов к сохранению")
                render()
                saveExport()
            }, onFailure = {
                status = host.tr("Export failed. Check source files and free space.",
                    "Не удалось экспортировать. Проверьте исходники и свободное место.")
                render()
            })
        }
    }

    fun cancelExport() {
        if (!exporting) return
        exporter.close()
        working = false
        exporting = false
        progress = -1
        status = host.tr("Export cancelled", "Экспорт отменён")
        render()
    }

    fun saveExport() {
        if (!canSave) return
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/mp4"
            putExtra(Intent.EXTRA_TITLE, "Voltune-${System.currentTimeMillis()}.m4a")
        }
        try { host.startActivityForResult(intent, SAVE_AUDIO) } catch (_: Exception) {
            message(host.tr("File picker unavailable", "Выбор файла недоступен"))
        }
    }

    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != SAVE_AUDIO) return false
        val uri = data?.data
        if (resultCode != Activity.RESULT_OK || uri == null) return true
        val file = readyFile ?: return true
        if (busy) return true
        val sourceUris = project.clips.map { Uri.parse(it.uri) }.toSet()
        working = true
        status = host.tr("Saving", "Сохранение")
        render()
        val resolver = host.applicationContext.contentResolver
        files.execute {
            val result = runCatching {
                check(uri !in sourceUris) { "Source overwrite" }
                resolver.openOutputStream(uri, "wt").use { output ->
                    checkNotNull(output)
                    file.inputStream().use { input -> input.copyTo(output) }
                }
            }
            host.uiHandler.post {
                if (closed) return@post
                working = false
                if (result.isSuccess) host.audioImportController.importExported(uri, data.flags)
                status = if (result.isSuccess) host.tr("Audio saved", "Аудио сохранено")
                    else host.tr("Saving failed; export is available to retry",
                        "Не удалось сохранить; экспорт доступен для повторной попытки")
                render()
            }
        }
        return true
    }

    private fun render() {
        if (!closed && host.navigationState.tabIndex == LibraryTabs.EDITOR &&
            !host.navigationState.tabAnimating) host.render()
    }

    private fun message(value: String) = Toast.makeText(host, value, Toast.LENGTH_LONG).show()

    override fun close() {
        closed = true
        if (previewController.isInitialized()) preview.close()
        onProgress = null
        exporter.close()
        files.shutdown()
        if (waveformRepository.isInitialized()) waveforms.close()
    }

    companion object { private const val SAVE_AUDIO = 6201 }
}
