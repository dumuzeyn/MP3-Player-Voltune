package com.dumuzeyn.mp3player

import android.content.Context
import java.io.File
import java.util.UUID

internal data class VocalCompositionResult(
    val vocal: File,
    val vocalDurationMs: Long,
    val instrumentals: List<File>,
)

internal class VocalCompositionProcessor(private val context: Context) {
    fun process(vocal: AudioEditClip, backing: List<AudioEditClip>, directory: File,
        cancelled: () -> Boolean, progress: (Int) -> Unit): VocalCompositionResult {
        require(backing.isNotEmpty())
        val created = ArrayList<File>()
        fun output(name: String) = File(directory, "$name-${UUID.randomUUID()}.wav").also(created::add)
        try {
            val stems = List(4) { output("vocal-stem") }
            StemSeparationProcessor(context).process(vocal, stems, cancelled) { progress(it * 35 / 100) }
            stems.take(3).forEach { it.delete(); created.remove(it) }
            val rawVocal = stems.last()
            val instrumentals = backing.mapIndexed { index, clip ->
                output("instrumental").also { target ->
                    StemSeparationProcessor(context).process(clip, listOf(target), cancelled) { value ->
                        progress(35 + (index * 45 + value * 45 / 100) / backing.size)
                    }
                }
            }
            val targetDuration = backing.maxOf(AudioEditClip::finishMs)
            val adapted = output("adapted-vocal")
            val adaptedDuration = AudioTimeStretchProcessor(context).process(rawVocal,
                vocal.durationMs, targetDuration, adapted, cancelled) { progress(80 + it * 20 / 100) }
            rawVocal.delete()
            created.remove(rawVocal)
            progress(100)
            return VocalCompositionResult(adapted, adaptedDuration, instrumentals)
        } catch (error: Throwable) {
            created.forEach(File::delete)
            throw error
        }
    }
}
