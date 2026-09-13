package com.dumuzeyn.mp3player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.provider.DocumentsContract
import java.io.ByteArrayOutputStream
import java.util.LinkedHashMap
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/** Reads bounded local sidecar and embedded lyrics through ContentResolver only. */
internal class LyricsRepository(
    private val context: Context,
    private val mainHandler: Handler,
) : AutoCloseable {
    fun interface Callback {
        fun loaded(document: LrcDocument)
    }

    private val executor = Executors.newSingleThreadExecutor()
    @Volatile private var closed = false
    private val cache = object : LinkedHashMap<String, LrcDocument>(8, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, LrcDocument>?,
        ): Boolean = size > MAX_CACHE_ENTRIES
    }

    fun load(track: Track, callback: Callback) {
        synchronized(cache) {
            cache[track.uri]?.let { cached ->
                mainHandler.post { callback.loaded(cached) }
                return
            }
        }
        try {
            executor.execute {
                val result = find(track)
                synchronized(cache) { cache[track.uri] = result }
                mainHandler.post {
                    if (!closed) callback.loaded(result)
                }
            }
        } catch (_: RejectedExecutionException) {
            // Activity is already closing.
        }
    }

    override fun close() {
        closed = true
        synchronized(cache) { cache.clear() }
        executor.shutdownNow()
    }

    private fun find(track: Track): LrcDocument {
        sidecars(track).forEach { candidate ->
            val value = read(candidate)
            if (value.isNotBlank()) return LrcParser().parse(value)
        }
        try {
            val embedded = EmbeddedLyricsReader().read(context, Uri.parse(track.uri))
            if (embedded.isNotBlank()) return LrcParser().parse(embedded)
        } catch (error: RuntimeException) {
            VoltuneLog.failure("embedded_lyrics_uri_failed", error)
        }
        return LrcDocument(ArrayList(), "", false)
    }

    private fun sidecars(track: Track): List<Uri> {
        val result = ArrayList<Uri>()
        try {
            val source = Uri.parse(track.uri)
            if (
                source.scheme?.equals("content", ignoreCase = true) != true ||
                !DocumentsContract.isDocumentUri(context, source)
            ) {
                return result
            }
            val id = DocumentsContract.getDocumentId(source)
            val slash = id.lastIndexOf('/')
            val parent = if (slash < 0) "" else id.substring(0, slash + 1)
            val filename = if (slash < 0) id else id.substring(slash + 1)
            val extension = filename.lastIndexOf('.')
            val base = if (extension > 0) filename.substring(0, extension) else filename
            addCandidate(result, source, "$parent$base.lrc")
            addCandidate(result, source, "$parent$base.txt")
        } catch (_: RuntimeException) {
            // A malformed or unsupported provider URI simply has no sidecar lyrics.
        }
        return result
    }

    private fun addCandidate(target: MutableList<Uri>, source: Uri, documentId: String) {
        try {
            val candidate = if (source.toString().contains("/tree/")) {
                DocumentsContract.buildDocumentUriUsingTree(source, documentId)
            } else {
                DocumentsContract.buildDocumentUri(source.authority, documentId)
            }
            target.add(candidate)
        } catch (_: RuntimeException) {
            // The provider does not support a sibling document URI.
        }
    }

    private fun read(uri: Uri): String {
        return try {
            context.contentResolver.openInputStream(uri).use { input ->
                if (input == null) return ""
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(BUFFER_BYTES)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > MAX_LYRICS_BYTES) return ""
                    output.write(buffer, 0, count)
                }
                val value = output.toString(Charsets.UTF_8.name())
                if (value.startsWith(BYTE_ORDER_MARK)) value.substring(1) else value
            }
        } catch (_: Exception) {
            ""
        }
    }

    private companion object {
        const val MAX_LYRICS_BYTES = 1024 * 1024
        const val MAX_CACHE_ENTRIES = 12
        const val BUFFER_BYTES = 8192
        const val BYTE_ORDER_MARK = "\ufeff"
    }
}
