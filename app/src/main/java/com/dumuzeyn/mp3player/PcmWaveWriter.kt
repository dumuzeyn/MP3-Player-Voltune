package com.dumuzeyn.mp3player

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

internal class PcmWaveWriter(file: File, private val rate: Int, private val channels: Int) : AutoCloseable {
    private val output = RandomAccessFile(file, "rw")
    private var samples = 0L
    private val buffer = ByteBuffer.allocate(8192).order(ByteOrder.LITTLE_ENDIAN)
    init { output.setLength(44); output.seek(44) }
    fun sample(value: Float) {
        require(value.isFinite()) { "Non-finite audio output" }
        check(samples < (0xffffffffL - 36) / 2) { "WAV size limit" }
        buffer.putShort((value.coerceIn(-1f, 1f) * 32767).roundToInt().toShort())
        samples++
        if (!buffer.hasRemaining()) flush()
    }
    private fun flush() { output.write(buffer.array(), 0, buffer.position()); buffer.clear() }
    override fun close() {
        try {
            flush()
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray(Charsets.US_ASCII)).putInt((36 + samples * 2).toInt())
            header.put("WAVEfmt ".toByteArray(Charsets.US_ASCII)).putInt(16).putShort(1).putShort(channels.toShort())
            header.putInt(rate).putInt(rate * channels * 2).putShort((channels * 2).toShort()).putShort(16)
            header.put("data".toByteArray(Charsets.US_ASCII)).putInt((samples * 2).toInt())
            output.seek(0)
            output.write(header.array())
        } finally { output.close() }
    }
}
