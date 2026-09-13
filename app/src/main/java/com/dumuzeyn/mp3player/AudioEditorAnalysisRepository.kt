package com.dumuzeyn.mp3player

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import java.util.concurrent.Executors
import java.util.concurrent.Future

internal class AudioEditorAnalysisRepository(context: Context) : AutoCloseable {
    private data class Key(val uri: String, val start: Long, val end: Long)
    private class Request {
        val listeners = LinkedHashSet<(Result<AudioMusicalAnalysis>) -> Unit>()
        var job: Future<*>? = null
    }
    private val extractor = AudioFeatureExtractor(context)
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { task -> Thread {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
        task.run()
    }.apply { name = "editor-analysis" } }
    private val cache = LruCache<Key, AudioMusicalAnalysis>(32)
    private val pending = HashMap<Key, Request>()
    private var closed = false

    fun request(clip: AudioEditClip, callback: (Result<AudioMusicalAnalysis>) -> Unit): AutoCloseable {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (closed) return AutoCloseable { }
        val key = Key(clip.uri, clip.startMs, clip.endMs)
        cache.get(key)?.let { callback(Result.success(it)); return AutoCloseable { } }
        val request = pending.getOrPut(key) { Request() }
        request.listeners.add(callback)
        if (request.job == null) request.job = worker.submit {
            val result = runCatching { extractor.musical(clip) { Thread.currentThread().isInterrupted } }
            handler.post {
                if (!closed && pending[key] === request) {
                    pending.remove(key)
                    result.getOrNull()?.let { cache.put(key, it) }
                    request.listeners.toList().forEach { it(result) }
                }
            }
        }
        return AutoCloseable {
            request.listeners.remove(callback)
            if (request.listeners.isEmpty() && pending[key] === request) {
                pending.remove(key)
                request.job?.cancel(true)
            }
        }
    }

    override fun close() {
        closed = true
        pending.values.forEach { it.job?.cancel(true) }
        pending.clear()
        cache.evictAll()
        worker.shutdownNow()
    }
}
