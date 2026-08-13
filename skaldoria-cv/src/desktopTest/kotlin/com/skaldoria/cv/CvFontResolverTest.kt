package com.skaldoria.cv

import com.skaldoria.cv.core.CvFontCatalog
import com.skaldoria.cv.core.CvFontId
import kotlin.test.assertFalse
import kotlin.test.Test
import kotlin.test.assertTrue

class CvFontResolverTest {
    @Test
    fun `every font choice resolves to an installed family or explicit fallback`() {
        CvFontCatalog.all.forEach { font ->
            val resolved = CvFontResolver.resolve(font)
            assertTrue(resolved.resolvedName.isNotBlank())
        }
    }

    @Test
    fun `Roboto resolves from the bundled font instead of the host system`() {
        val resolved = CvFontResolver.resolve(CvFontId.Roboto)

        assertTrue(resolved.isBundled)
        assertFalse(resolved.isFallback)
    }
}
