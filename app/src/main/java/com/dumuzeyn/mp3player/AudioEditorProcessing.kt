package com.dumuzeyn.mp3player

import android.net.Uri
import android.os.Process
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.Future

internal class AudioEditorProcessing(private val host: MainActivityCore, private val render: () -> Unit) : AutoCloseable {
    private val executor = Executors.newSingleThreadExecutor { run ->
        Thread({ Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND); run.run() }, "editor-processing")
    }
    private var job: Future<*>? = null
    private var generation = 0
    private var closed = false
    var active = false
        private set
    var progress = 0
        private set
    var status = ""
        private set
    var onProgress: (() -> Unit)? = null

    fun cleanSpeech(clip: AudioEditClip): Boolean {
        val controller = host.audioEditorController
        if (closed || controller.busy) return false
        val original = controller.project
        try { original.replace(clip) } catch (_: IllegalArgumentException) { return false }
        val directory = File(host.filesDir, "editor-audio")
        if ((!directory.isDirectory && !directory.mkdirs()) ||
            directory.usableSpace < clip.durationMs * 192 + 64 * 1024 * 1024) {
            status = host.tr("Not enough free space", "Недостаточно свободного места")
            render()
            return false
        }
        val output = File(directory, "speech-${UUID.randomUUID()}.wav")
        val token = ++generation
        active = true
        progress = 0
        status = host.tr("Cleaning speech", "Очистка речи")
        render()
        job = executor.submit {
            var lastProgress = -1
            val result = runCatching {
                SpeechCleanupProcessor(host.applicationContext).process(clip, output, { Thread.currentThread().isInterrupted }) { value ->
                    if (value != lastProgress) {
                        lastProgress = value
                        host.uiHandler.post {
                            if (generation == token && !closed) { progress = value; onProgress?.invoke() }
                        }
                    }
                }
            }
            host.uiHandler.post {
                if (closed || token != generation) { output.delete(); return@post }
                active = false
                job = null
                val changed = result.isSuccess && controller.project == original && controller.change { project ->
                    project.replace(clip.copy(uri = Uri.fromFile(output).toString(),
                        title = clip.title + host.tr(" (speech)", " (речь)"),
                        sourceDurationMs = clip.durationMs, startMs = 0, endMs = clip.durationMs))
                }
                if (!changed) output.delete()
                status = if (changed) host.tr("Speech cleaned", "Речь очищена") else host.tr(
                    "Processing failed. Check the file, format and free space.",
                    "Обработка не удалась. Проверьте файл, формат и свободное место.")
                render()
            }
        }
        return true
    }

    fun cancel() {
        if (!active) return
        generation++
        job?.cancel(true)
        job = null
        active = false
        status = host.tr("Processing cancelled", "Обработка отменена")
        render()
    }

    override fun close() {
        closed = true
        cancel()
        onProgress = null
        executor.shutdownNow()
    }
}
