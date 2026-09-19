package com.dumuzeyn.mp3player

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.os.Process
import android.widget.Toast
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

    enum class Operation { SPEECH, STEMS, INSTRUMENTAL }
    fun cleanSpeech(clip: AudioEditClip) = start(clip, Operation.SPEECH)
    fun separate(clip: AudioEditClip, instrumental: Boolean) = start(clip,
        if (instrumental) Operation.INSTRUMENTAL else Operation.STEMS)

    fun combineVocals(vocal: AudioEditClip): Boolean {
        val controller = host.audioEditorController
        if (closed || controller.busy) return false
        val original = controller.project
        val backing = original.clips.filterNot { it.id == vocal.id }
        val backingLanes = backing.map(AudioEditClip::lane).distinct().sorted()
        fun reject(message: String): Boolean {
            status = message
            Toast.makeText(host, message, Toast.LENGTH_LONG).show()
            render()
            return false
        }
        if (backing.isEmpty()) return reject(host.tr("Add music for the selected vocal first",
            "Сначала добавьте музыку для выбранного вокала"))
        if (backingLanes.size >= AudioEditClip.MAX_LANES) return reject(host.tr(
            "Too many music lanes", "Слишком много музыкальных дорожек"))
        val memory = ActivityManager.MemoryInfo()
        (host.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(memory)
        if (memory.lowMemory || memory.availMem < 768L * 1024 * 1024) return reject(host.tr(
            "Not enough free memory for vocal processing",
            "Недостаточно свободной памяти для работы с вокалом"))
        val targetDuration = backing.maxOf(AudioEditClip::finishMs)
        val speed = vocal.durationMs.toFloat() / targetDuration
        if (speed !in 0.25f..4f) return reject(host.tr(
            "The vocal and music durations differ too much",
            "Длительность вокала и музыки отличается слишком сильно"))
        val directory = File(host.filesDir, "editor-audio")
        val required = (vocal.durationMs + backing.sumOf(AudioEditClip::durationMs)) * 1060 +
            256L * 1024 * 1024
        if ((!directory.isDirectory && !directory.mkdirs()) || directory.usableSpace < required)
            return reject(host.tr("Not enough free space", "Недостаточно свободного места"))
        val token = ++generation
        active = true
        progress = 0
        status = host.tr("Adapting vocals to music", "Адаптация вокала к музыке")
        render()
        job = executor.submit {
            val result = runCatching {
                VocalCompositionProcessor(host.applicationContext).process(vocal, backing, directory,
                    { Thread.currentThread().isInterrupted }) { value ->
                    host.uiHandler.post {
                        if (generation == token && !closed) { progress = value; onProgress?.invoke() }
                    }
                }
            }
            host.uiHandler.post {
                val composition = result.getOrNull()
                if (closed || token != generation) {
                    composition?.let(::deleteComposition)
                    return@post
                }
                result.exceptionOrNull()?.let { VoltuneLog.failure("editor_vocal_mix_failed", it) }
                active = false
                job = null
                val changed = composition != null && controller.project == original && controller.change {
                    val laneMap = backingLanes.mapIndexed { index, lane -> lane to index + 1 }.toMap()
                    val vocalClip = vocal.copy(uri = Uri.fromFile(composition.vocal).toString(),
                        title = "${vocal.title} (${host.tr("adapted vocal", "адаптированный вокал")})",
                        sourceDurationMs = composition.vocalDurationMs, startMs = 0,
                        endMs = composition.vocalDurationMs, lane = 0, offsetMs = 0,
                        gain = 1f, fadeInMs = 0, fadeOutMs = 0)
                    val music = backing.zip(composition.instrumentals).map { (clip, file) ->
                        clip.copy(uri = Uri.fromFile(file).toString(),
                            title = "${clip.title} (${host.tr("music", "музыка")})",
                            sourceDurationMs = clip.durationMs, startMs = 0, endMs = clip.durationMs,
                            lane = laneMap.getValue(clip.lane), gain = 1f, fadeInMs = 0, fadeOutMs = 0)
                    }
                    AudioEditProject(listOf(vocalClip) + music)
                }
                if (!changed && composition != null) deleteComposition(composition)
                status = if (changed) host.tr("Vocal mix is ready", "Вокал совмещён с музыкой")
                    else host.tr("Vocal processing failed", "Не удалось обработать вокал")
                render()
            }
        }
        return true
    }

    private fun deleteComposition(value: VocalCompositionResult) {
        value.vocal.delete()
        value.instrumentals.forEach(File::delete)
    }

    private fun start(clip: AudioEditClip, operation: Operation): Boolean {
        val controller = host.audioEditorController
        if (closed || controller.busy) return false
        val original = controller.project
        try { original.replace(clip) } catch (_: IllegalArgumentException) { return false }
        fun reject(message: String): Boolean {
            status = message
            Toast.makeText(host, message, Toast.LENGTH_LONG).show()
            render()
            return false
        }
        val lanes = listOf(clip.lane) + (0 until AudioEditClip.MAX_LANES).filter { candidate ->
            candidate != clip.lane && original.clips.none { it.id != clip.id && it.lane == candidate }
        }
        if (operation == Operation.STEMS && (lanes.size < 4 || original.clips.size > 197))
            return reject(host.tr("Three empty lanes are required", "Нужны три свободные дорожки"))
        if (operation != Operation.SPEECH) {
            val memory = ActivityManager.MemoryInfo()
            (host.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(memory)
            if (memory.lowMemory || memory.availMem < 768L * 1024 * 1024)
                return reject(host.tr("Not enough free memory for separation", "Недостаточно свободной памяти для разделения"))
        }
        val directory = File(host.filesDir, "editor-audio")
        val required = clip.durationMs * (if (operation == Operation.SPEECH) 192 else 1060) +
            (if (operation == Operation.SPEECH) 64L else 192L) * 1024 * 1024
        if ((!directory.isDirectory && !directory.mkdirs()) ||
            directory.usableSpace < required) {
            return reject(host.tr("Not enough free space", "Недостаточно свободного места"))
        }
        val outputs = List(if (operation == Operation.STEMS) 4 else 1) {
            File(directory, "processed-${UUID.randomUUID()}.wav")
        }
        val token = ++generation
        active = true
        progress = 0
        status = if (operation == Operation.SPEECH) host.tr("Removing noise", "Удаление шумов")
            else host.tr("Separating audio", "Разделение аудио")
        render()
        job = executor.submit {
            var lastProgress = -1
            val update: (Int) -> Unit = { value ->
                if (value > lastProgress) {
                    lastProgress = value
                    host.uiHandler.post {
                        if (generation == token && !closed) { progress = value; onProgress?.invoke() }
                    }
                }
            }
            val result = runCatching {
                if (operation == Operation.SPEECH) SpeechCleanupProcessor(host.applicationContext)
                    .process(clip, outputs.single(), { Thread.currentThread().isInterrupted }, update)
                else StemSeparationProcessor(host.applicationContext)
                    .process(clip, outputs, { Thread.currentThread().isInterrupted }, update)
            }
            host.uiHandler.post {
                if (closed || token != generation) { outputs.forEach { it.delete() }; return@post }
                result.exceptionOrNull()?.let { VoltuneLog.failure("editor_processing_failed", it) }
                active = false
                job = null
                val changed = result.isSuccess && controller.project == original && controller.change { project ->
                    val names = when (operation) {
                        Operation.SPEECH -> listOf(host.tr("noise reduced", "без шумов"))
                        Operation.INSTRUMENTAL -> listOf(host.tr("instrumental", "без вокала"))
                        Operation.STEMS -> listOf(host.tr("drums", "ударные"), host.tr("bass", "бас"),
                            host.tr("other", "остальное"), host.tr("vocals", "вокал"))
                    }
                    val processed = outputs.mapIndexed { index, output ->
                        clip.copy(id = if (index == 0) clip.id else UUID.randomUUID().toString(),
                            uri = Uri.fromFile(output).toString(), title = "${clip.title} (${names[index]})",
                            sourceDurationMs = clip.durationMs, startMs = 0, endMs = clip.durationMs, lane = lanes[index])
                    }
                    project.copy(clips = project.clips.filterNot { it.id == clip.id } + processed)
                }
                if (!changed) outputs.forEach { it.delete() }
                status = if (changed) host.tr("Processing complete", "Обработка завершена") else host.tr(
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
