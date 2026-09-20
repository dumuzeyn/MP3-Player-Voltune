package com.dumuzeyn.mp3player

import java.io.File

internal object Mp3AudioConverter {
    init { System.loadLibrary("voltune_mp3") }

    fun convert(inputWave: File, output: File) {
        val result = encodeWave(inputWave.absolutePath, output.absolutePath)
        check(result == 0 && output.length() > 0) { "MP3 encoding failed: $result" }
    }

    private external fun encodeWave(inputPath: String, outputPath: String): Int
}
