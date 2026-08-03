package com.markdownpres.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BuiltinThemesTest {

    @Test
    fun testBuiltinThemesCompleteness() {
        val themes = BuiltinThemes.all
        assertEquals(4, themes.size)

        val themeNames = themes.map { it.name }.toSet()
        assertTrue(themeNames.contains("Nord Dark"))
        assertTrue(themeNames.contains("Sleek Light"))
        assertTrue(themeNames.contains("Cyber Midnight"))
        assertTrue(themeNames.contains("Minimalist Editorial"))
    }

    @Test
    fun testGetByIdFallback() {
        val nord = BuiltinThemes.getById("nord-dark")
        assertEquals("Nord Dark", nord.name)

        val cyber = BuiltinThemes.getById("cyber-midnight")
        assertEquals("Cyber Midnight", cyber.name)

        val fallback = BuiltinThemes.getById("non-existent-theme-id")
        assertEquals("Nord Dark", fallback.name)
    }

    @Test
    fun testThemesHaveDistinctColors() {
        for (theme in BuiltinThemes.all) {
            assertTrue(theme.name.isNotBlank())
            assertTrue(theme.primary != theme.background, "Theme ${theme.name} primary must differ from background")
            assertTrue(theme.textPrimary != theme.background, "Theme ${theme.name} textPrimary must differ from background")
        }
    }
}
