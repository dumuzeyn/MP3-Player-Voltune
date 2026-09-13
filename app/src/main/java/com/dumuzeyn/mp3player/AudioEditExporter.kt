package com.dumuzeyn.mp3player

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.Future

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class AudioEditExporter(private val context: Context) : AutoCloseable {
    private val handler = Handler(Looper.getMainLooper())
    private var transformer: Transformer? = null
    private var output: File? = null
    private val completionHandler = Handler(Looper.getMainLooper())
    private var pending: Future<*>? = null
    private var generation = 0
    private val preparedFiles = ArrayList<File>()

    fun export(project: AudioEditProject, progress: (Int) -> Unit, done: (Result<File>) -> Unit) {
        check(transformer == null && pending == null)
        require(project.clips.isNotEmpty())
        if (project.clips.any { it.startMs > 0 || it.endMs < it.sourceDurationMs }) {
            prepareSources(project, progress, done, false) { input, files ->
                AudioEditFlacSource.prepare(context, input, files)
            }
        } else start(project, progress, done, false)
    }

    private fun start(project: AudioEditProject, progress: (Int) -> Unit, done: (Result<File>) -> Unit,
        indexed: Boolean) {
        try {
            val file = File.createTempFile("voltune-edit-", ".m4a", context.cacheDir)
            output = file
            val engine = Transformer.Builder(context.applicationContext)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        handler.removeCallbacksAndMessages(null)
                        transformer = null
                        output = null
                        clearPreparedFiles()
                        done(Result.success(file))
                    }
                    override fun onError(composition: Composition, exportResult: ExportResult,
                        exportException: ExportException) {
                        val unseekable = generateSequence<Throwable>(exportException) { it.cause }.any {
                            it is androidx.media3.exoplayer.source.ClippingMediaSource.IllegalClippingException &&
                                it.reason == androidx.media3.exoplayer.source.ClippingMediaSource.IllegalClippingException.REASON_NOT_SEEKABLE_TO_START
                        }
                        if (!indexed && unseekable) prepareSeekable(project, progress, done)
                        else {
                            close()
                            done(Result.failure(exportException))
                        }
                    }
                }).build()
            transformer = engine
            engine.start(composition(project), file.absolutePath)
            handler.post(object : Runnable {
                override fun run() {
                    if (transformer !== engine) return
                    val holder = ProgressHolder()
                    val state = engine.getProgress(holder)
                    progress(if (state == Transformer.PROGRESS_STATE_AVAILABLE) holder.progress else -1)
                    handler.postDelayed(this, 250)
                }
            })
        } catch (error: Exception) {
            close()
            done(Result.failure(error))
        }
    }

    override fun close() {
        generation++
        pending?.cancel(true)
        pending = null
        handler.removeCallbacksAndMessages(null)
        transformer?.cancel()
        transformer = null
        output?.delete()
        output = null
        clearPreparedFiles()
    }

    private fun prepareSeekable(project: AudioEditProject, progress: (Int) -> Unit,
        done: (Result<File>) -> Unit) {
        handler.removeCallbacksAndMessages(null)
        transformer?.cancel()
        transformer = null
        output?.delete()
        output = null
        prepareSources(project, progress, done, true) { input, files ->
            AudioEditSeekableSource.prepare(context, input, files)
        }
    }

    private fun prepareSources(project: AudioEditProject, progress: (Int) -> Unit,
        done: (Result<File>) -> Unit, indexed: Boolean,
        prepare: (AudioEditProject, MutableList<File>) -> AudioEditProject) {
        val token = ++generation
        progress(-1)
        pending = preparation.submit {
            val files = ArrayList<File>()
            val result = runCatching { prepare(project, files) }
            completionHandler.post {
                if (token != generation) files.forEach { it.delete() }
                else {
                    pending = null
                    preparedFiles.addAll(files)
                    result.onSuccess { start(it, progress, done, indexed) }.onFailure {
                        clearPreparedFiles()
                        done(Result.failure(it))
                    }
                }
            }
        }
    }

    private fun clearPreparedFiles() {
        preparedFiles.forEach { it.delete() }
        preparedFiles.clear()
    }

    companion object {
        private val preparation = Executors.newSingleThreadExecutor { task ->
            Thread({ android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
                task.run() }, "editor-aac-index")
        }

        fun composition(project: AudioEditProject): Composition {
            val sequences = project.clips.groupBy { it.lane }.toSortedMap().values.map { lane ->
                val builder = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO))
                var cursor = 0L
                lane.sortedBy { it.offsetMs }.forEach { clip ->
                    if (clip.offsetMs > cursor) builder.addGap((clip.offsetMs - cursor) * 1000)
                    val item = MediaItem.Builder().setUri(clip.uri)
                        .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(clip.startMs).setEndPositionMs(clip.endMs).build())
                        .build()
                    val volume = ChannelMixingAudioProcessor().apply {
                        for (channels in 1..8) putChannelMixingMatrix(ChannelMixingMatrix(
                            channels, channels, FloatArray(channels * channels) { index ->
                                if (index / channels == index % channels) clip.gain else 0f
                            },
                        ))
                    }
                    builder.addItem(EditedMediaItem.Builder(item).setRemoveVideo(true)
                        .setEffects(Effects(listOf(volume), emptyList())).build())
                    cursor = clip.finishMs
                }
                builder.build()
            }
            return Composition.Builder(sequences).build()
        }
    }
}
