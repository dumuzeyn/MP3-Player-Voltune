package com.dumuzeyn.mp3player

import android.content.Context
import android.net.Uri
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.min
import kotlin.math.sin

/** Generates synthetic metadata only in the non-distributable benchmark build. */
internal object BenchmarkLibrarySeeder {
    const val EXTRA_TRACK_COUNT = "voltuneBenchmarkTrackCount"

    @JvmStatic
    fun seedIfRequested(context: Context, requestedCount: Int) {
        if (BuildConfig.BUILD_TYPE != "benchmark" || requestedCount <= 0) return
        val count = min(MAX_TRACKS, requestedCount)
        if (TrackStore.load(context).size == count) return

        val tracks = ArrayList<Track>(count)
        val playableUri = createTestTone(context)
        for (index in 0 until count) {
            val recentlyAddedPlayable = index == count - 1 && playableUri.isNotEmpty()
            val uri = if (recentlyAddedPlayable) {
                playableUri
            } else {
                "content://voltune.benchmark/track/$index"
            }
            val title = String.format(Locale.ROOT, "Benchmark song %05d", index)
            tracks.add(
                if (recentlyAddedPlayable) {
                    Track(
                        "benchmark-playable-track",
                        uri,
                        title,
                        "Benchmark artist 0",
                        "Benchmark album 0",
                        "Benchmark genre 0",
                        8_000,
                        -1L,
                        0L,
                        "benchmark",
                    )
                } else {
                    Track(
                        uri,
                        title,
                        "Benchmark artist ${index % 200}",
                        "Benchmark album ${index % 500}",
                        "Benchmark genre ${index % 20}",
                        180_000 + index % 120_000,
                    )
                },
            )
        }
        TrackStore.save(context, tracks)
    }

    private fun createTestTone(context: Context): String {
        val file = File(context.filesDir, "benchmark-tone.wav")
        val sampleCount = SAMPLE_RATE * TONE_DURATION_SECONDS
        return try {
            DataOutputStream(FileOutputStream(file)).use { output ->
                output.writeBytes("RIFF")
                writeLeInt(output, 36 + sampleCount * 2)
                output.writeBytes("WAVEfmt ")
                writeLeInt(output, 16)
                writeLeShort(output, 1)
                writeLeShort(output, 1)
                writeLeInt(output, SAMPLE_RATE)
                writeLeInt(output, SAMPLE_RATE * 2)
                writeLeShort(output, 2)
                writeLeShort(output, 16)
                output.writeBytes("data")
                writeLeInt(output, sampleCount * 2)
                for (sample in 0 until sampleCount) {
                    val phase = 2.0 * Math.PI * TONE_FREQUENCY * sample / SAMPLE_RATE
                    writeLeShort(output, (sin(phase) * TONE_AMPLITUDE).toInt())
                }
            }
            Uri.fromFile(file).toString()
        } catch (_: Exception) {
            ""
        }
    }

    private fun writeLeInt(output: DataOutputStream, value: Int) {
        output.writeByte(value and 0xff)
        output.writeByte((value ushr 8) and 0xff)
        output.writeByte((value ushr 16) and 0xff)
        output.writeByte((value ushr 24) and 0xff)
    }

    private fun writeLeShort(output: DataOutputStream, value: Int) {
        output.writeByte(value and 0xff)
        output.writeByte((value ushr 8) and 0xff)
    }

    private const val MAX_TRACKS = 10_000
    private const val SAMPLE_RATE = 8_000
    private const val TONE_DURATION_SECONDS = 8
    private const val TONE_FREQUENCY = 440.0
    private const val TONE_AMPLITUDE = 5_000
}
