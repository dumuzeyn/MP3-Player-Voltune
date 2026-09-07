package com.dumuzeyn.mp3player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.max

/** Small private disk cache containing only decoded artwork thumbnails. */
internal class ArtworkDiskCache(context: Context) {
    private val directory = File(context.cacheDir, "artwork-v1")

    fun read(key: String): Bitmap? {
        val file = fileFor(key)
        if (!file.isFile) return null
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
        if (bitmap == null) {
            file.delete()
            return null
        }
        file.setLastModified(System.currentTimeMillis())
        return bitmap
    }

    fun write(key: String, bitmap: Bitmap?) {
        if (bitmap == null || bitmap.isRecycled) return
        if (!directory.exists() && !directory.mkdirs()) return
        val target = fileFor(key)
        val temporary = File(directory, target.name + ".tmp")
        try {
            FileOutputStream(temporary).use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                    temporary.delete()
                    return
                }
            }
            if (target.exists()) target.delete()
            if (!temporary.renameTo(target)) temporary.delete()
            trim()
        } catch (_: Exception) {
            temporary.delete()
        }
    }

    private fun trim() {
        val files = directory.listFiles { file -> file.isFile && !file.name.endsWith(".tmp") }
            ?: return
        var total = files.sumOf { file -> max(0L, file.length()) }
        if (total <= MAX_BYTES) return
        files.sortWith { left, right -> left.lastModified().compareTo(right.lastModified()) }
        for (file in files) {
            val length = max(0L, file.length())
            if (file.delete()) total -= length
            if (total <= MAX_BYTES) return
        }
    }

    private fun fileFor(key: String): File = File(directory, digest(key) + ".jpg")

    private fun digest(value: String): String = try {
        val hash = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        buildString(hash.size * 2) {
            hash.forEach { item -> append(String.format(Locale.ROOT, "%02x", item)) }
        }
    } catch (_: Exception) {
        Integer.toHexString(value.hashCode())
    }

    private companion object {
        const val MAX_BYTES = 48L * 1024L * 1024L
        const val JPEG_QUALITY = 88
    }
}
