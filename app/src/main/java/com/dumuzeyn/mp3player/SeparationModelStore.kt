package com.dumuzeyn.mp3player

import android.content.Context
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CancellationException

internal object SeparationModelStore {
    private const val NAME = "htdemucs-4s-f16.bin"
    private const val HASH = "72b17c42d308982ddb5069bc3bf48b81a5aac4cb6516e4366c0fa7cef6df0064"
    @Synchronized fun prepare(context: Context, cancelled: () -> Boolean): File {
        val directory = File(context.noBackupFilesDir, "audio-models").apply { check(isDirectory || mkdirs()) }
        val destination = File(directory, NAME)
        fun hash(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(65536)
                while (true) {
                    if (cancelled()) throw CancellationException()
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
        if (destination.isFile && hash(destination) == HASH) return destination
        val temporary = File(directory, "$NAME.part")
        try {
            context.assets.open(NAME).use { input ->
                temporary.outputStream().use { output ->
                    val buffer = ByteArray(65536)
                    while (true) {
                        if (cancelled()) throw CancellationException()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                }
            }
            check(hash(temporary) == HASH) { "Model checksum mismatch" }
            check(temporary.renameTo(destination)) { "Model install failed" }
            return destination
        } finally { temporary.delete() }
    }
}
