package com.skaldoria.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BuiltinThemesTest {

    @Test
    fun testBuiltinThemesCompleteness() {
        val themes = BuiltinThemes.all
        assertEquals(5, themes.size)

        val themeNames = themes.map { it.name }.toSet()
        assertTrue(themeNames.contains("Skaldoria Dark"))
        assertTrue(themeNames.contains("Sleek Light"))
        assertTrue(themeNames.contains("Cyber Midnight"))
        assertTrue(themeNames.contains("Minimalist Editorial"))
        assertTrue(themeNames.contains("Deutsche Börse"))
    }

    @Test
    fun testPublicVsCorporateThemes() {
        assertEquals(4, BuiltinThemes.publicThemes.size)
        assertFalse(BuiltinThemes.publicThemes.any { it.id == "deutsche-borse" })

        assertTrue(BuiltinThemes.allWithCorporate.any { it.id == "deutsche-borse" })
    }

    @Test
    fun testCorporateUnlockCodes() {
        assertTrue(BuiltinThemes.isCorporateCode("DB_CORP_2026"))
        assertTrue(BuiltinThemes.isCorporateCode("db_corp_2026"))
        assertTrue(BuiltinThemes.isCorporateCode("deutsche-borse"))
        assertTrue(BuiltinThemes.isCorporateCode("DEUTSCHE_BORSE"))
        assertTrue(BuiltinThemes.isCorporateCode("DB_EXECUTIVE"))
        assertTrue(BuiltinThemes.isCorporateCode("FRANKFURT_FLOOR"))

        assertFalse(BuiltinThemes.isCorporateCode("invalid_code"))
        assertFalse(BuiltinThemes.isCorporateCode(""))
    }

    @Test
    fun testGetByIdFallback() {
        val skaldoria = BuiltinThemes.getById("skaldoria-dark")
        assertEquals("Skaldoria Dark", skaldoria.name)

        val cyber = BuiltinThemes.getById("cyber-midnight")
        assertEquals("Cyber Midnight", cyber.name)

        val corporate = BuiltinThemes.getById("deutsche-borse")
        assertEquals("Deutsche Börse", corporate.name)

        val fallback = BuiltinThemes.getById("non-existent-theme-id")
        assertEquals("Skaldoria Dark", fallback.name)
    }

    @Test
    fun testThemesHaveDistinctColors() {
        for (theme in BuiltinThemes.all) {
            assertTrue(theme.name.isNotBlank())
            assertTrue(theme.primary != theme.background, "Theme ${theme.name} primary must differ from background")
            assertTrue(theme.textPrimary != theme.background, "Theme ${theme.name} textPrimary must differ from background")
            assertTrue(theme.textPrimary != theme.surfaceVariant, "Theme ${theme.name} textPrimary must differ from surfaceVariant")
        }
    }
}
