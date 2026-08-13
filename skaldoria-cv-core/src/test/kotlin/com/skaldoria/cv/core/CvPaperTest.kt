package com.skaldoria.cv.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CvPaperTest {
    @Test
    fun `A4 geometry has the ISO 216 aspect ratio`() {
        val paper = CvPaperSize.A4

        assertEquals(210.0, paper.widthMillimeters)
        assertEquals(297.0, paper.heightMillimeters)
        assertTrue(kotlin.math.abs(paper.widthPoints / paper.heightPoints - 1.0 / kotlin.math.sqrt(2.0)) < 0.0001)
        assertEquals(CvPaperSize.A4, CvPaperSize.fromMetadata(" A4 "))
    }

    @Test
    fun `paper defaults safely to A4`() {
        assertEquals(CvPaperSize.A4, CvPaperSize.fromMetadata(null))
        assertEquals(CvPaperSize.A4, CvPaperSize.fromMetadata("unknown"))
        assertEquals(CvPaperSize.Letter, CvPaperSize.fromMetadata("letter"))
    }
}
