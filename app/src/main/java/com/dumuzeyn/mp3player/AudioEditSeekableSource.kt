package com.dumuzeyn.mp3player

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.CancellationException

/** Adds an MP4 sample index to raw AAC without decoding or recompressing it. */
internal object AudioEditSeekableSource {
    fun prepare(context: Context, project: AudioEditProject, files: MutableList<File>): AudioEditProject {
        val replacements = HashMap<String, String>()
        return project.copy(clips = project.clips.map { clip ->
            if (clip.startMs == 0L) clip else clip.copy(uri = replacements.getOrPut(clip.uri) {
                remux(context, clip.uri, files)
            })
        })
    }

    private fun remux(context: Context, uri: String, files: MutableList<File>): String {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            extractor.setDataSource(context, Uri.parse(uri), null)
            val index = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME) == "audio/mp4a-latm"
            } ?: return uri
            val file = File.createTempFile("voltune-seekable-", ".m4a", context.cacheDir)
            files.add(file)
            val format = extractor.getTrackFormat(index)
            extractor.selectTrack(index)
            val engine = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = engine
            val outputTrack = engine.addTrack(format)
            engine.start()
            val bytes = ByteBuffer.allocateDirect(1024 * 1024)
            val info = MediaCodec.BufferInfo()
            while (true) {
                if (Thread.currentThread().isInterrupted) throw CancellationException()
                bytes.clear()
                val size = extractor.readSampleData(bytes, 0)
                if (size < 0) break
                check(size <= bytes.capacity()) { "Oversized AAC frame" }
                check(file.parentFile!!.usableSpace > 8L * 1024 * 1024) { "Not enough free space" }
                info.set(0, size, extractor.sampleTime.coerceAtLeast(0), MediaCodec.BUFFER_FLAG_KEY_FRAME)
                engine.writeSampleData(outputTrack, bytes, info)
                extractor.advance()
            }
            engine.stop()
            return Uri.fromFile(file).toString()
        } finally {
            muxer?.release()
            extractor.release()
        }
    }
}
