package com.skaldoria.cv.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CvThemeCatalogTest {
    @Test
    fun `metadata names resolve every unique theme`() {
        assertEquals(CvThemeId.entries.size, CvThemeCatalog.all.map { it.metadataValue }.toSet().size)
        CvThemeCatalog.all.forEach { theme ->
            assertEquals(theme, CvThemeCatalog.fromMetadata(" ${theme.metadataValue.uppercase()} "))
        }
        assertNull(CvThemeCatalog.fromMetadata("unknown"))
    }
}
