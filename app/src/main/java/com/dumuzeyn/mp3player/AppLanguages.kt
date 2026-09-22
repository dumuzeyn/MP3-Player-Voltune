package com.dumuzeyn.mp3player

internal data class AppLanguage(
    val code: String,
    val nativeName: String,
    val languageTag: String = code,
    val rightToLeft: Boolean = false,
)

internal object AppLanguages {
    val all: List<AppLanguage> = listOf(
        AppLanguage("ru", "Русский"),
        AppLanguage("en", "English"),
        AppLanguage("es", "Español"),
        AppLanguage("pt-BR", "Português (Brasil)"),
        AppLanguage("zh-CN", "简体中文"),
        AppLanguage("de", "Deutsch"),
        AppLanguage("fr", "Français"),
        AppLanguage("hi", "हिन्दी"),
        AppLanguage("id", "Bahasa Indonesia"),
        AppLanguage("ja", "日本語"),
        AppLanguage("ko", "한국어"),
        AppLanguage("ar", "العربية", rightToLeft = true),
    )

    fun find(code: String): AppLanguage = all.firstOrNull { it.code == code } ?: all.first()

    fun supports(code: String): Boolean = all.any { it.code == code }
}
