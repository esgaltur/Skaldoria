package com.skaldoria.cv.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CvTemplateCatalogTest {
    @Test
    fun `metadata names resolve every unique template`() {
        assertEquals(CvTemplateId.entries.size, CvTemplateCatalog.all.map { it.metadataValue }.toSet().size)
        CvTemplateCatalog.all.forEach { template ->
            assertEquals(template, CvTemplateCatalog.fromMetadata(" ${template.metadataValue.uppercase()} "))
        }
        assertNull(CvTemplateCatalog.fromMetadata("unknown"))
    }

    @Test
    fun `software engineer ATS template has readable page geometry`() {
        val layout = CvTemplateId.SoftwareEngineerAts.layout

        assertTrue(layout.horizontalMargin in 45.0..57.0)
        assertTrue(layout.bodySize in 10.0..12.0)
        assertTrue(layout.bodyLineHeight > layout.bodySize)
        assertTrue(layout.bottomReserved > 0.0)
    }
}
