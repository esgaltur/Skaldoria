package com.skaldoria.cv

import com.skaldoria.cv.core.CvThemeCatalog
import com.skaldoria.theme.ColorScience
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CvPreviewThemesTest {
    @Test
    fun `every theme has one accessible preview palette`() {
        assertEquals(
            CvThemeCatalog.all.toSet(),
            CvPreviewThemes.all.map { it.themeId }.toSet()
        )
        assertEquals(CvPreviewThemes.all.size, CvPreviewThemes.all.map { it.themeId }.distinct().size)

        CvPreviewThemes.all.forEach { theme ->
            assertTrue(ColorScience.contrastRatio(theme.primaryText, theme.pageColor) >= 4.5f)
            assertTrue(ColorScience.contrastRatio(theme.secondaryText, theme.pageColor) >= 4.5f)
            assertTrue(ColorScience.contrastRatio(theme.accent, theme.pageColor) >= 4.5f)
        }
    }
}
