package com.dumuzeyn.mp3player

import android.content.Context
import java.io.File
import java.security.MessageDigest

internal class AudioEditorPreviewCache(context: Context) {
    private val directory = File(context.filesDir, DIRECTORY)

    fun get(project: AudioEditProject): File? = file(project).takeIf { it.isFile && it.length() > 0 }

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
        return target
    }

    fun retain(project: AudioEditProject) {
        val keep = file(project).name
        directory.listFiles()?.forEach { file -> if (file.name != keep) file.delete() }
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

    companion object { private const val DIRECTORY = "editor-preview" }
}
