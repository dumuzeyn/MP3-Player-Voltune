package com.dumuzeyn.mp3player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLanguagesTest {
    @Test
    fun supportsAllReleaseLanguages() {
        assertEquals(
            listOf("ru", "en", "es", "pt-BR", "zh-CN", "de", "fr", "hi", "id", "ja", "ko", "ar"),
            AppLanguages.all.map { it.code },
        )
    }

    @Test
    fun onlyArabicUsesRightToLeftLayout() {
        assertTrue(AppLanguages.find("ar").rightToLeft)
        assertTrue(AppLanguages.all.filterNot { it.code == "ar" }.none { it.rightToLeft })
    }

    @Test
    fun everyAdditionalLanguageTranslatesCoreInterfaceText() {
        AppLanguages.all.filterNot { it.code == "ru" || it.code == "en" }.forEach { language ->
            assertNotEquals(language.code, "Settings", TranslationCatalog.text(language.code, "Settings"))
            assertNotEquals(language.code, "Songs", TranslationCatalog.text(language.code, "Songs"))
            assertNotEquals(language.code, "Done", TranslationCatalog.text(language.code, "Done"))
        }
    }

    @Test
    fun unknownTextAndLanguageFallBackToEnglish() {
        assertEquals("Uncatalogued text", TranslationCatalog.text("es", "Uncatalogued text"))
        assertEquals("Settings", TranslationCatalog.text("xx", "Settings"))
        assertFalse(AppLanguages.supports("xx"))
    }
}
