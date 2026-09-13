package com.dumuzeyn.mp3player

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import kotlin.math.min

/** Bounded, read-only ID3 USLT reader for embedded offline lyrics. */
internal class EmbeddedLyricsReader {
    fun read(context: Context, uri: Uri): String = try {
        context.contentResolver.openInputStream(uri).use { input ->
            if (input == null) return ""
            val header = readExactly(input, HEADER_BYTES)
            if (!hasId3Header(header)) return ""
            val size = synchsafe(header, 6)
            if (size <= 0 || size > MAX_TAG_BYTES) return ""
            parseFrames(readExactly(input, size), header[3].toInt())
        }
    } catch (error: Exception) {
        VoltuneLog.failure("embedded_lyrics_read_failed", error)
        ""
    }

    companion object {
        @JvmStatic
        fun parse(id3: ByteArray?): String {
            if (id3 == null || !hasId3Header(id3)) return ""
            val size = min(synchsafe(id3, 6), id3.size - HEADER_BYTES)
            val tag = id3.copyOfRange(HEADER_BYTES, HEADER_BYTES + size.coerceAtLeast(0))
            return parseFrames(tag, id3[3].toInt())
        }

        private fun hasId3Header(bytes: ByteArray): Boolean =
            bytes.size >= HEADER_BYTES && bytes[0] == 'I'.code.toByte() &&
                bytes[1] == 'D'.code.toByte() && bytes[2] == '3'.code.toByte()

        private fun parseFrames(tag: ByteArray, version: Int): String {
            var offset = 0
            while (offset + FRAME_HEADER_BYTES <= tag.size) {
                val id = String(tag, offset, 4, StandardCharsets.ISO_8859_1)
                if (!FRAME_ID.matches(id)) break
                val size = if (version >= 4) synchsafe(tag, offset + 4) else bigEndian(tag, offset + 4)
                val payload = offset + FRAME_HEADER_BYTES
                if (size < 0 || payload + size > tag.size) break
                if (id == "USLT") return decodeUslt(tag, payload, size)
                offset = payload + size
            }
            return ""
        }

        private fun decodeUslt(bytes: ByteArray, offset: Int, size: Int): String {
            if (size < 5) return ""
            val encoding = bytes[offset].toInt() and 0xff
            var textStart = offset + 4
            val end = offset + size
            val terminatorWidth = if (encoding == 1 || encoding == 2) 2 else 1
            while (textStart + terminatorWidth <= end) {
                if (
                    bytes[textStart].toInt() == 0 &&
                    (terminatorWidth == 1 || bytes[textStart + 1].toInt() == 0)
                ) {
                    textStart += terminatorWidth
                    break
                }
                textStart += terminatorWidth
            }
            if (textStart >= end) return ""
            val value = String(bytes, textStart, end - textStart, charset(encoding))
                .replace('\u0000', ' ')
                .trim()
            return if (value.length > MAX_TAG_BYTES) value.substring(0, MAX_TAG_BYTES) else value
        }

        private fun charset(encoding: Int): Charset = when (encoding) {
            1 -> StandardCharsets.UTF_16
            2 -> StandardCharsets.UTF_16BE
            3 -> StandardCharsets.UTF_8
            else -> StandardCharsets.ISO_8859_1
        }

        private fun synchsafe(bytes: ByteArray, offset: Int): Int {
            if (offset < 0 || offset + 4 > bytes.size) return 0
            return ((bytes[offset].toInt() and 0x7f) shl 21) or
                ((bytes[offset + 1].toInt() and 0x7f) shl 14) or
                ((bytes[offset + 2].toInt() and 0x7f) shl 7) or
                (bytes[offset + 3].toInt() and 0x7f)
        }

        private fun bigEndian(bytes: ByteArray, offset: Int): Int =
            ((bytes[offset].toInt() and 0xff) shl 24) or
                ((bytes[offset + 1].toInt() and 0xff) shl 16) or
                ((bytes[offset + 2].toInt() and 0xff) shl 8) or
                (bytes[offset + 3].toInt() and 0xff)

        private fun readExactly(input: InputStream, limit: Int): ByteArray {
            val output = ByteArrayOutputStream(min(limit, BUFFER_BYTES))
            val buffer = ByteArray(BUFFER_BYTES)
            while (output.size() < limit) {
                val count = input.read(buffer, 0, min(buffer.size, limit - output.size()))
                if (count < 0) break
                output.write(buffer, 0, count)
            }
            return output.toByteArray()
        }

        private const val MAX_TAG_BYTES = 1024 * 1024
        private const val HEADER_BYTES = 10
        private const val FRAME_HEADER_BYTES = 10
        private const val BUFFER_BYTES = 8192
        private val FRAME_ID = Regex("[A-Z0-9]{4}")
    }
}
