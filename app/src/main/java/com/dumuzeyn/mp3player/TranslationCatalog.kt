package com.dumuzeyn.mp3player

import com.dumuzeyn.mp3player.localization.*

internal object TranslationCatalog {
    fun text(language: String, english: String): String = when (language) {
        "es" -> TranslationsEs.entries[english]
        "pt-BR" -> TranslationsPtBr.entries[english]
        "zh-CN" -> TranslationsZhCn.entries[english]
        "de" -> TranslationsDe.entries[english]
        "fr" -> TranslationsFr.entries[english]
        "hi" -> TranslationsHi.entries[english]
        "id" -> TranslationsId.entries[english]
        "ja" -> TranslationsJa.entries[english]
        "ko" -> TranslationsKo.entries[english]
        "ar" -> TranslationsAr.entries[english]
        else -> null
    } ?: english
}
