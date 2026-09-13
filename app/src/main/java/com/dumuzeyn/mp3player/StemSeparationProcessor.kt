package com.dumuzeyn.mp3player

import android.content.Context
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CancellationException
import java.util.concurrent.Semaphore

/** Disk-backed stereo input and overlapping, bounded inference windows. */
internal class StemSeparationProcessor(private val context: Context) {
    fun process(clip: AudioEditClip, outputs: List<File>, cancelled: () -> Boolean, progress: (Int) -> Unit) {
        require(outputs.size == 1 || outputs.size == 4)
        val temporary = File.createTempFile("voltune-separation-", ".pcm", context.cacheDir)
        var locked = false
        val writers = ArrayList<PcmWaveWriter>()
        fun checkCancelled() { if (cancelled() || Thread.currentThread().isInterrupted) throw CancellationException() }
        try {
            gate.acquire()
            locked = true
            checkCancelled()
            val model = SeparationModelStore.prepare(context, cancelled)
            temporary.outputStream().buffered(65536).use { output ->
                val bytes = ByteArray(65536)
                AudioPcmResampler(context).decode(clip, RATE, true, cancelled) { pcm, _ ->
                    checkCancelled()
                    while (pcm.hasRemaining()) {
                        val count = minOf(bytes.size, pcm.remaining())
                        pcm.get(bytes, 0, count)
                        output.write(bytes, 0, count)
                    }
                }
            }
            outputs.forEach { writers.add(PcmWaveWriter(it, RATE, 2)) }
            RandomAccessFile(temporary, "r").use { input ->
                val total = input.length() / 8
                check(total >= 2) { "Selection too short" }
                DemucsSeparator(model).use { separator ->
                    var offset = 0L
                    var tail: Array<FloatArray>? = null
                    while (offset < total) {
                        checkCancelled()
                        val length = minOf(CORE.toLong(), total - offset).toInt()
                        val from = maxOf(0, offset - CONTEXT)
                        val to = minOf(total, offset + length + CONTEXT)
                        val bytes = ByteArray(((to - from) * 8).toInt())
                        input.seek(from * 8)
                        input.readFully(bytes)
                        val floats = FloatArray(bytes.size / 4)
                        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(floats)
                        val stems = separator.window(floats) { value ->
                            progress((5 + (offset + length * value.coerceIn(0f, 1f)) * 94 / total).toInt().coerceAtMost(99))
                            !cancelled() && !Thread.currentThread().isInterrupted
                        }
                        val start = ((offset - from) * 2).toInt()
                        val selected = Array(4) { stems[it].copyOfRange(start, start + length * 2) }
                        tail?.let { previous ->
                            repeat(minOf(length, OVERLAP)) { frame ->
                                val weight = frame / OVERLAP.toFloat()
                                repeat(4) { stem -> repeat(2) { channel ->
                                    val i = frame * 2 + channel
                                    selected[stem][i] = previous[stem][i] * (1 - weight) + selected[stem][i] * weight
                                } }
                            }
                        }
                        val last = offset + length >= total
                        val writeFrames = if (last) length else length - OVERLAP
                        repeat(writeFrames * 2) { i ->
                            if (writers.size == 1) writers[0].sample(selected[0][i] + selected[1][i] + selected[2][i])
                            else repeat(4) { stem -> writers[stem].sample(selected[stem][i]) }
                        }
                        if (last) break
                        tail = Array(4) { selected[it].copyOfRange(writeFrames * 2, length * 2) }
                        offset += CORE - OVERLAP
                    }
                }
            }
            writers.forEach { it.close() }
            writers.clear()
            progress(100)
        } catch (failure: Throwable) {
            writers.forEach { runCatching { it.close() } }
            writers.clear()
            outputs.forEach { it.delete() }
            throw failure
        } finally {
            temporary.delete()
            if (locked) gate.release()
        }
    }

    companion object {
        private const val RATE = 44100
        private const val CORE = RATE * 6
        private const val OVERLAP = RATE
        private const val CONTEXT = RATE
        private val gate = Semaphore(1)
    }
}
