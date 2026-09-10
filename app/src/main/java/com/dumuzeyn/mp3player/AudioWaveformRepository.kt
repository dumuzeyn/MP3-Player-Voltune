package com.dumuzeyn.mp3player

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import java.util.concurrent.Executors
import java.util.concurrent.Future

/** Main-thread subscriptions share one cancellable decoder and a small session cache. */
internal class AudioWaveformRepository(context: Context) : AutoCloseable {
    private data class Key(val uri: String, val durationMs: Long)
    private class Request {
        val listeners = LinkedHashSet<(Result<AudioWaveform>) -> Unit>()
        var future: Future<*>? = null
    }
    private val decoder = AudioWaveformDecoder(context)
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val cache = LruCache<Key, AudioWaveform>(16)
    private val requests = HashMap<Key, Request>()
    private var closed = false

    fun request(clip: AudioEditClip, callback: (Result<AudioWaveform>) -> Unit): AutoCloseable {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (closed) return AutoCloseable { }
        val key = Key(clip.uri, clip.sourceDurationMs)
        cache.get(key)?.let { callback(Result.success(it)); return AutoCloseable { } }
        val request = requests.getOrPut(key) { Request() }
        request.listeners.add(callback)
        if (request.future == null) request.future = worker.submit {
            val result = runCatching { decoder.decode(key.uri, key.durationMs) { Thread.currentThread().isInterrupted } }
            handler.post {
                if (!closed && requests[key] === request) {
                    requests.remove(key)
                    result.getOrNull()?.let { cache.put(key, it) }
                    request.listeners.toList().forEach { it(result) }
                }
            }
        }
        return AutoCloseable {
            request.listeners.remove(callback)
            if (request.listeners.isEmpty() && requests[key] === request) {
                requests.remove(key)
                request.future?.cancel(true)
            }
        }
    }

    override fun close() {
        closed = true
        requests.values.forEach { it.future?.cancel(true) }
        requests.clear()
        cache.evictAll()
        worker.shutdownNow()
    }
}
