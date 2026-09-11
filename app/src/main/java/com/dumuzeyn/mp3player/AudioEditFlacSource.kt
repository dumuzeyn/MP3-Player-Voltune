package com.dumuzeyn.mp3player

import android.content.Context
import android.media.AudioFormat
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.File
import java.io.DataInputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CancellationException

/** Cuts decoded FLAC samples, retaining the decoder's PCM precision and channel layout. */
internal object AudioEditFlacSource {
    fun prepare(context: Context, project: AudioEditProject, files: MutableList<File>): AudioEditProject {
        val replacements = HashMap<Triple<String, Long, Long>, String>()
        val formats = HashMap<String, Boolean>()
        return project.copy(clips = project.clips.map { clip ->
            checkCancelled()
            val trimmed = clip.startMs > 0 || clip.endMs < clip.sourceDurationMs
            if (!trimmed || !formats.getOrPut(clip.uri) { isFlac(context, clip.uri) }) clip
            else {
                val uri = replacements.getOrPut(Triple(clip.uri, clip.startMs, clip.endMs)) {
                    decode(context, clip, files)
                }
                clip.copy(uri = uri, sourceDurationMs = clip.durationMs, startMs = 0, endMs = clip.durationMs)
            }
        })
    }

    private fun isFlac(context: Context, uri: String): Boolean {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, Uri.parse(uri), null)
            val mimeTypes = (0 until extractor.trackCount).map {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)
            }
            if ("audio/flac" in mimeTypes) return true
            // Some Android FLAC extractors already decode frames and report raw PCM.
            if ("audio/raw" !in mimeTypes) return false
            return context.contentResolver.openInputStream(Uri.parse(uri))?.use { input ->
                DataInputStream(input).readInt() == 0x664c6143
            } ?: false
        } finally { extractor.release() }
    }

    private fun decode(context: Context, clip: AudioEditClip, files: MutableList<File>): String {
        checkCancelled()
        val file = File.createTempFile("voltune-flac-cut-", ".wav", context.cacheDir).also(files::add)
        RandomAccessFile(file, "rw").use { output ->
            output.setLength(44)
            output.seek(44)
            var format: AudioPcmFormat? = null
            var length = 0L
            val bytes = ByteArray(8192)
            AudioPcmDecoder(context).decode(clip.uri, clip.startMs, clip.endMs,
                { Thread.currentThread().isInterrupted }) { pcmFormat, pcm, _ ->
                checkCancelled()
                check(format == null || format == pcmFormat) { "FLAC PCM format changed" }
                format = pcmFormat
                check(file.parentFile!!.usableSpace > pcm.remaining() + 8L * 1024 * 1024) { "Not enough free space" }
                length += pcm.remaining()
                check(length <= 0xffffffffL - 36) { "WAV size limit" }
                while (pcm.hasRemaining()) {
                    val count = minOf(bytes.size, pcm.remaining())
                    pcm.get(bytes, 0, count)
                    output.write(bytes, 0, count)
                }
            }
            val pcm = checkNotNull(format)
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray(Charsets.US_ASCII)).putInt((length + 36).toInt())
            header.put("WAVEfmt ".toByteArray(Charsets.US_ASCII)).putInt(16)
            header.putShort(if (pcm.encoding == AudioFormat.ENCODING_PCM_FLOAT) 3 else 1)
            header.putShort(pcm.channels.toShort()).putInt(pcm.sampleRate)
            header.putInt(pcm.sampleRate * pcm.frameBytes).putShort(pcm.frameBytes.toShort())
            header.putShort((pcm.bytesPerSample * 8).toShort())
            header.put("data".toByteArray(Charsets.US_ASCII)).putInt(length.toInt())
            output.seek(0)
            output.write(header.array())
        }
        return Uri.fromFile(file).toString()
    }

    private fun checkCancelled() {
        if (Thread.currentThread().isInterrupted) throw CancellationException()
    }
}
