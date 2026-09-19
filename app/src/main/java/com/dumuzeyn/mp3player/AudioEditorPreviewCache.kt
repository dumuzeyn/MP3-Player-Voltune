package com.dumuzeyn.mp3player

import android.content.Context
import java.io.File
import java.security.MessageDigest

internal class AudioEditorPreviewCache(context: Context) {
    private val directory = File(context.filesDir, DIRECTORY)

    fun get(project: AudioEditProject): File? = file(project).takeIf { it.isFile && it.length() > 0 }
        ?.also { it.setLastModified(System.currentTimeMillis()) }

    fun put(project: AudioEditProject, source: File): File {
        check(directory.isDirectory || directory.mkdirs())
        val target = file(project)
        if (target.isFile && target.length() > 0) {
            source.delete()
            return target
        }
        val pending = File(directory, "${target.name}.pending")
        pending.delete()
        if (!source.renameTo(pending)) {
            source.inputStream().use { input ->
                pending.outputStream().use { output -> input.copyTo(output) }
            }
            source.delete()
        }
        check(pending.renameTo(target) || (target.isFile && target.length() > 0))
        pending.delete()
        target.setLastModified(System.currentTimeMillis())
        trim(setOf(target.name))
        return target
    }

    fun maintain(project: AudioEditProject) {
        if (project.clips.isEmpty()) clear() else trim(setOf(file(project).name))
    }

    fun clear() {
        directory.listFiles()?.forEach(File::delete)
        directory.delete()
    }

    private fun file(project: AudioEditProject): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(AudioEditStore.encode(project).toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        return File(directory, "$digest.m4a")
    }

    private fun trim(protected: Set<String>) {
        val files = directory.listFiles()?.filter { file ->
            if (file.extension == "pending" || !file.isFile || file.length() <= 0) {
                file.delete()
                false
            } else file.extension == "m4a"
        }?.sortedByDescending(File::lastModified)?.toMutableList() ?: return
        var bytes = files.sumOf(File::length)
        while (files.size > MAX_FILES || bytes > MAX_BYTES) {
            val candidate = files.indexOfLast { it.name !in protected }
            if (candidate < 0) break
            val removed = files.removeAt(candidate)
            bytes -= removed.length()
            removed.delete()
        }
    }

    companion object {
        private const val DIRECTORY = "editor-preview"
        private const val MAX_FILES = 8
        private const val MAX_BYTES = 192L * 1024 * 1024
    }
}
