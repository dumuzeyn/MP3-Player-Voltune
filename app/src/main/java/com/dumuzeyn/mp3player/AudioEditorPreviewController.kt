package com.dumuzeyn.mp3player

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.media3.session.SessionResult
import java.io.File
import java.util.UUID

internal class AudioEditorPreviewController(private val host: MainActivityCore,
    private val render: () -> Unit) : AutoCloseable {
    enum class Phase { IDLE, PREPARING, STARTING, PLAYING, PAUSED }
    private val exporter = AudioEditExporter(host)
    private val cacheExporter = AudioEditExporter(host)
    private val cache = AudioEditorPreviewCache(host)
    private val handler = Handler(Looper.getMainLooper())
    private val cacheHandler = Handler(Looper.getMainLooper())
    private var generation = 0
    private var cacheGeneration = 0
    private var closed = false
    private var token = ""
    private var pendingStartPositionMs = 0L
    private val listeners = LinkedHashSet<() -> Unit>()
    var phase = Phase.IDLE
        private set
    val active get() = phase != Phase.IDLE
    var progress = -1
        private set
    var positionMs = 0L
        private set
    var durationMs = 0L
        private set
    var failed = false
        private set
    private val tick = object : Runnable {
        override fun run() {
            if (!active || closed) return
            if (host.navigationState.tabIndex != LibraryTabs.EDITOR) { stop(); return }
            if (phase == Phase.PLAYING || phase == Phase.PAUSED) request("state") { update(it) }
            handler.postDelayed(this, 250)
        }
    }

    fun observe(listener: () -> Unit): AutoCloseable {
        listeners.add(listener)
        listener()
        return AutoCloseable { listeners.remove(listener) }
    }

    fun start(project: AudioEditProject, startPositionMs: Long = 0) {
        if (closed || active || project.clips.isEmpty() || host.audioEditorController.busy) return
        generation++
        val expected = generation
        token = UUID.randomUUID().toString()
        pendingStartPositionMs = startPositionMs.coerceAtLeast(0)
        failed = false
        positionMs = 0
        durationMs = project.durationMs
        progress = -1
        phase = Phase.PREPARING
        notifyChanged(true)
        handler.post(tick)
        cache.get(project)?.let {
            play(it)
            return
        }
        cancelCachePreparation()
        exporter.export(project, { progress = it; notifyChanged() }) { result ->
            if (closed || generation != expected) { result.getOrNull()?.delete(); return@export }
            result.fold(onSuccess = { file ->
                runCatching { cache.put(project, file) }
                    .fold(onSuccess = ::play, onFailure = { file.delete(); fail() })
            }, onFailure = { fail() })
        }
    }

    fun maintainCache(project: AudioEditProject) = cache.maintain(project)
    fun prepareCache(project: AudioEditProject) {
        val expected = ++cacheGeneration
        cacheHandler.removeCallbacksAndMessages(null)
        cacheExporter.close()
        if (closed || project.clips.isEmpty() || cache.get(project) != null) return
        cacheHandler.postDelayed({
            if (closed || expected != cacheGeneration || host.audioEditorController.busy ||
                cache.get(project) != null) return@postDelayed
            cacheExporter.export(project, {}, { result ->
                val file = result.getOrNull()
                if (closed || expected != cacheGeneration) file?.delete()
                else file?.let { runCatching { cache.put(project, it) }.onFailure { _ -> it.delete() } }
            })
        }, CACHE_DELAY_MS)
    }

    fun clearCache() {
        cancelCachePreparation()
        cache.clear()
    }

    private fun cancelCachePreparation() {
        cacheGeneration++
        cacheHandler.removeCallbacksAndMessages(null)
        cacheExporter.close()
    }

    private fun play(file: File) {
        if (host.navigationState.tabIndex != LibraryTabs.EDITOR) { stop(); return }
        phase = Phase.STARTING
        request("start", Bundle().apply {
            putString("path", file.absolutePath)
            putString("token", token)
            putString("title", host.tr("Preview", "Предпрослушивание"))
        }) { update(it) }
        notifyChanged()
    }

    fun toggle() {
        if (phase == Phase.PLAYING || phase == Phase.PAUSED) request("toggle") { update(it) }
    }

    fun seek(position: Long) {
        if (phase == Phase.PLAYING || phase == Phase.PAUSED) {
            request("seek", Bundle().apply { putLong("position", position.coerceIn(0, durationMs)) }) { update(it) }
        }
    }

    private fun request(action: String, args: Bundle = Bundle(), done: (Bundle) -> Unit) {
        val expected = generation
        args.putString("action", action)
        val timeout = Runnable { if (generation == expected && active) fail() }
        handler.postDelayed(timeout, 5000)
        host.playbackController.editorPreviewCommand(args, { !closed && generation == expected }) { result ->
            handler.removeCallbacks(timeout)
            if (result?.resultCode == SessionResult.RESULT_SUCCESS) done(result.extras) else fail()
        }
    }

    private fun update(state: Bundle) {
        if (!state.getBoolean("active") || state.getString("token") != token) {
            failed = state.getBoolean("error")
            finish()
            return
        }
        positionMs = state.getLong("position").coerceAtLeast(0)
        state.getLong("duration").takeIf { it > 0 }?.let { durationMs = it }
        val requestedPosition = pendingStartPositionMs.coerceAtMost(durationMs).takeIf { it > 0 }
        pendingStartPositionMs = 0
        if (requestedPosition != null) {
            phase = Phase.STARTING
            request("seek", Bundle().apply { putLong("position", requestedPosition) }) { update(it) }
            notifyChanged()
            return
        }
        phase = if (state.getBoolean("playing")) Phase.PLAYING else Phase.PAUSED
        notifyChanged()
    }

    fun stop() {
        if (!active) return
        exporter.close()
        host.playbackController.editorPreviewCommand(Bundle().apply { putString("action", "stop") }) { }
        finish()
    }

    private fun finish() {
        generation++
        handler.removeCallbacksAndMessages(null)
        phase = Phase.IDLE
        progress = -1
        positionMs = 0
        pendingStartPositionMs = 0
        notifyChanged(true)
    }

    private fun fail() { failed = true; stop() }
    private fun notifyChanged(full: Boolean = false) {
        if (closed) return
        listeners.toList().forEach { it() }
        if (full) render()
    }

    override fun close() {
        closed = true
        stop()
        handler.removeCallbacksAndMessages(null)
        cacheHandler.removeCallbacksAndMessages(null)
        exporter.close()
        cacheExporter.close()
        listeners.clear()
    }

    companion object { private const val CACHE_DELAY_MS = 600L }
}
