package com.dumuzeyn.mp3player

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.util.Log
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/** In-process smoke entry point, excluded from debug and production release APKs. */
class OptimizedAudioSmokeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Thread({
            val files = ArrayList<File>()
            try {
                checkAudio(files)
                Log.i(TAG, "PASS: FFT, silence, speech cleanup, AAC export, Demucs and cancellation")
            } catch (error: Throwable) {
                Log.e(TAG, "FAIL: optimized audio smoke", error)
            } finally {
                files.forEach { it.delete() }
                runOnUiThread { finish() }
            }
        }, "optimized-audio-smoke").start()
    }

    private fun checkAudio(files: MutableList<File>) {
        val source = File.createTempFile("smoke-chord-", ".wav", cacheDir).also(files::add)
        PcmWaveWriter(source, 8000, 1).use { writer ->
            repeat(48000) { frame ->
                val value = if (frame >= 32000) 0.0 else listOf(60, 64, 67).mapIndexed { i, note ->
                    sin(2 * PI * (440 * 2.0.pow((note - 69) / 12.0)) * frame / 8000) *
                        if (i == 0) .38 else .3
                }.sum()
                writer.sample(value.toFloat())
            }
        }
        val clip = AudioEditClip(uri = Uri.fromFile(source).toString(), title = "Smoke", sourceDurationMs = 6000)
        val features = AudioFeatureExtractor(this)
        val chord = features.musical(clip.copy(endMs = 4000)) { false }
        check(chord.key?.tonic == 0 && chord.key?.minor == false) { "FFT key detection failed" }
        val silence = features.musical(clip.copy(startMs = 4000)) { false }
        check(silence.key == null && silence.bpm == 0.0) { "Silence classification failed" }
        Log.i(TAG, "FFT and silence passed")
        val speech = File.createTempFile("smoke-speech-", ".wav", cacheDir).also(files::add)
        SpeechCleanupProcessor(this).process(clip.copy(endMs = 2000), speech, { false }, {})
        check(speech.length() == 44 + 2000 * 48 * 2L) { "Speech output duration mismatch" }
        Log.i(TAG, "RNNoise passed")
        val exported = AtomicReference<Result<File>>()
        val ready = CountDownLatch(1)
        runOnUiThread {
            AudioEditExporter(this).export(AudioEditProject(listOf(clip.copy(startMs = 201, endMs = 1199))), {}) {
                exported.set(it)
                ready.countDown()
            }
        }
        check(ready.await(60, TimeUnit.SECONDS)) { "AAC export timed out" }
        val output = exported.get().getOrThrow().also(files::add)
        check(output.length() > 1000) { "AAC export empty" }
        Log.i(TAG, "AAC export passed")
        DemucsSeparator(SeparationModelStore.prepare(this) { false }).use { separator ->
            check(separator.verifyAttention()) { "Tiled native kernels differ from reference" }
            val input = FloatArray(44100 * 2 * 2) { index ->
                (.3 * sin(2 * PI * 110 * (index / 2) / 44100)).toFloat()
            }
            val cancelled = runCatching { separator.window(input) { it < .1f } }
            check(cancelled.exceptionOrNull() is java.util.concurrent.CancellationException)
            val stems = separator.window(input) { true }
            check(stems.size == 4 && stems.all { it.size == input.size && it.all(Float::isFinite) })
            check(stems.any { stem -> stem.any { kotlin.math.abs(it) > .01f } })
            check(!stems[0].contentEquals(stems[1]))
        }
        Log.i(TAG, "Demucs passed")
    }

    companion object { private const val TAG = "VoltuneOptimizedSmoke" }
}
