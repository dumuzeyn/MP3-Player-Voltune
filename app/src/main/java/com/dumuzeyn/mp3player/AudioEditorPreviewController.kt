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
    private val handler = Handler(Looper.getMainLooper())
    private var cachedProject: AudioEditProject? = null
    private var cachedFile: File? = null
    private var generation = 0
    private var closed = false
    private var token = ""
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

    fun start(project: AudioEditProject) {
        if (closed || active || project.clips.isEmpty() || host.audioEditorController.busy) return
        generation++
        val expected = generation
        token = UUID.randomUUID().toString()
        failed = false
        positionMs = 0
        durationMs = project.durationMs
        progress = -1
        phase = Phase.PREPARING
        notifyChanged(true)
        handler.post(tick)
        if (cachedProject == project && cachedFile?.isFile == true) {
            play(checkNotNull(cachedFile))
            return
        }
        exporter.export(project, { progress = it; notifyChanged() }) { result ->
            if (closed || generation != expected) { result.getOrNull()?.delete(); return@export }
            result.fold(onSuccess = { file ->
                cachedFile?.delete()
                cachedFile = file
                cachedProject = project
                play(file)
            }, onFailure = { fail() })
        }
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
        phase = if (state.getBoolean("playing")) Phase.PLAYING else Phase.PAUSED
        positionMs = state.getLong("position").coerceAtLeast(0)
        state.getLong("duration").takeIf { it > 0 }?.let { durationMs = it }
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
        exporter.close()
        cachedFile?.delete()
        listeners.clear()
    }
}
