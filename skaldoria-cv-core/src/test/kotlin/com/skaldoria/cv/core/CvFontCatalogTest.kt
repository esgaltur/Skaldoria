package com.skaldoria.cv.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CvFontCatalogTest {
    @Test
    fun `metadata resolves supported font choices`() {
        CvFontCatalog.all.forEach { font ->
            assertEquals(font, CvFontCatalog.fromMetadata(" ${font.metadataValue.uppercase()} "))
        }
        assertNull(CvFontCatalog.fromMetadata("comic-sans"))
        assertEquals(CvFontId.Roboto, CvFontCatalog.default)
    }
}
