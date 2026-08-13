package com.skaldoria.cv

import kotlin.test.Test
import kotlin.test.assertEquals

class CvZoomPolicyTest {
    @Test
    fun `zoom is stepped and bounded`() {
        assertEquals(110, CvZoomPolicy.zoomIn(100))
        assertEquals(CvZoomPolicy.MaximumPercent, CvZoomPolicy.zoomIn(CvZoomPolicy.MaximumPercent))
        assertEquals(90, CvZoomPolicy.zoomOut(100))
        assertEquals(CvZoomPolicy.MinimumPercent, CvZoomPolicy.zoomOut(CvZoomPolicy.MinimumPercent))
        assertEquals(1.5f, CvZoomPolicy.scale(150))
    }
}
