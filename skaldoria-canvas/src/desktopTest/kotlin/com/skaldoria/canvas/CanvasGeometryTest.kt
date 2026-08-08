package com.skaldoria.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.skaldoria.canvas.model.CanvasGeometry
import com.skaldoria.canvas.model.CanvasNode
import com.skaldoria.canvas.model.EdgePort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CanvasGeometryTest {

    @Test
    fun testAutoPortResolutionHorizontal() {
        val n1 = CanvasNode(id = "1", x = 0f, y = 0f, width = 100f, height = 100f)
        val n2 = CanvasNode(id = "2", x = 300f, y = 0f, width = 100f, height = 100f)

        val (start, end) = CanvasGeometry.resolvePorts(n1, n2, EdgePort.Auto, EdgePort.Auto)
        // Since n2 is directly to the right, n1's Right port and n2's Left port should be used
        assertEquals(Offset(100f, 50f), start)
        assertEquals(Offset(300f, 50f), end)
    }

    @Test
    fun testAutoPortResolutionVertical() {
        val n1 = CanvasNode(id = "1", x = 0f, y = 0f, width = 100f, height = 100f)
        val n2 = CanvasNode(id = "2", x = 0f, y = 300f, width = 100f, height = 100f)

        val (start, end) = CanvasGeometry.resolvePorts(n1, n2, EdgePort.Auto, EdgePort.Auto)
        // Since n2 is directly below, n1's Bottom port and n2's Top port should be used
        assertEquals(Offset(50f, 100f), start)
        assertEquals(Offset(50f, 300f), end)
    }

    @Test
    fun testViewportCulling() {
        val n1 = CanvasNode(id = "1", x = 100f, y = 100f, width = 200f, height = 100f)
        val n2 = CanvasNode(id = "2", x = 5000f, y = 5000f, width = 200f, height = 100f)

        val screenViewport = Rect(0f, 0f, 1000f, 1000f)
        assertTrue(CanvasGeometry.isNodeVisible(n1, screenViewport))
        assertFalse(CanvasGeometry.isNodeVisible(n2, screenViewport))
    }

    @Test
    fun testArrowheadCalculation() {
        val start = Offset(0f, 0f)
        val target = Offset(100f, 0f)
        val arrowhead = CanvasGeometry.computeArrowhead(start, target, arrowLength = 10f)

        assertEquals(3, arrowhead.size)
        assertEquals(target, arrowhead[0])
        assertTrue(arrowhead[1].x < target.x)
        assertTrue(arrowhead[2].x < target.x)
    }
}
