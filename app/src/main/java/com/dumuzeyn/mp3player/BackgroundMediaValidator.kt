package com.dumuzeyn.mp3player

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/** Accepts only bounded, decodable raster images from a read-only content URI. */
internal object BackgroundMediaValidator {
    @JvmStatic
    fun validate(context: Context, uri: Uri?): Result {
        if (uri == null || uri.scheme != "content") return Result.invalid()
        val mime = context.contentResolver.getType(uri)
        if (
            mime == null || !mime.lowercase(Locale.ROOT).startsWith("image/") ||
            mime.equals("image/svg+xml", ignoreCase = true)
        ) {
            return Result.invalid()
        }
        val size = querySize(context, uri)
        if (size > MAX_FILE_BYTES) return Result.invalid()

        try {
            context.contentResolver.openInputStream(uri).use { input ->
                val header = ByteArray(32)
                val count = input?.read(header) ?: -1
                if (count < 10 || !isSupportedRaster(header, count)) return Result.invalid()
            }
        } catch (_: IOException) {
            return Result.invalid()
        } catch (_: SecurityException) {
            return Result.invalid()
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                BitmapFactory.decodeStream(input, null, bounds)
            }
        } catch (_: IOException) {
            return Result.invalid()
        } catch (_: SecurityException) {
            return Result.invalid()
        }
        val pixels = bounds.outWidth.toLong() * bounds.outHeight.toLong()
        val valid = bounds.outWidth > 0 && bounds.outHeight > 0 && pixels <= MAX_PIXELS
        val heavy = valid &&
            ((mime.equals("image/gif", ignoreCase = true) && size > HEAVY_GIF_BYTES) ||
                pixels > HEAVY_PIXELS)
        return if (valid) Result.valid(heavy) else Result.invalid()
    }

    private fun querySize(context: Context, uri: Uri): Long {
        try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.SIZE),
                null,
                null,
                null,
            ).use { cursor ->
                if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) {
                    return max(0L, cursor.getLong(0))
                }
            }
        } catch (_: RuntimeException) {
        }
        return 0L
    }

    private fun isSupportedRaster(data: ByteArray, count: Int): Boolean {
        if (unsigned(data[0]) == 0xff && unsigned(data[1]) == 0xd8 && unsigned(data[2]) == 0xff) {
            return true
        }
        if (
            count >= 8 && unsigned(data[0]) == 0x89 && data[1] == byte('P') &&
            data[2] == byte('N') && data[3] == byte('G') && unsigned(data[4]) == 0x0d &&
            unsigned(data[5]) == 0x0a && unsigned(data[6]) == 0x1a && unsigned(data[7]) == 0x0a
        ) {
            return true
        }
        if (
            count >= 6 && data[0] == byte('G') && data[1] == byte('I') &&
            data[2] == byte('F') && data[3] == byte('8') &&
            (data[4] == byte('7') || data[4] == byte('9')) && data[5] == byte('a')
        ) {
            return true
        }
        if (
            count >= 12 && data[0] == byte('R') && data[1] == byte('I') &&
            data[2] == byte('F') && data[3] == byte('F') && data[8] == byte('W') &&
            data[9] == byte('E') && data[10] == byte('B') && data[11] == byte('P')
        ) {
            return true
        }
        if (data[0] == byte('B') && data[1] == byte('M')) return true
        if (
            count >= 12 && data[4] == byte('f') && data[5] == byte('t') &&
            data[6] == byte('y') && data[7] == byte('p')
        ) {
            val brand = String(data, 8, min(count - 8, 16), StandardCharsets.US_ASCII)
                .lowercase(Locale.ROOT)
            return listOf("heic", "heif", "mif1", "avif", "avis").any(brand::contains)
        }
        return false
    }

    private fun unsigned(value: Byte): Int = value.toInt() and 0xff

    private fun byte(value: Char): Byte = value.code.toByte()

    class Result private constructor(
        @JvmField val valid: Boolean,
        @JvmField val heavy: Boolean,
    ) {
        companion object {
            fun valid(heavy: Boolean): Result = Result(true, heavy)
            fun invalid(): Result = Result(false, false)
        }
    }

    private const val MAX_FILE_BYTES = 32L * 1024L * 1024L
    private const val MAX_PIXELS = 80L * 1024L * 1024L
    private const val HEAVY_GIF_BYTES = 8L * 1024L * 1024L
    private const val HEAVY_PIXELS = 24L * 1024L * 1024L
}
