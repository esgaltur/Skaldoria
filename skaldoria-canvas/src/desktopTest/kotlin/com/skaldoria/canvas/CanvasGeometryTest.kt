package com.skaldoria.canvas

import com.skaldoria.canvas.model.CanvasPoint as Offset
import com.skaldoria.canvas.model.CanvasRect as Rect
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
    fun testExplicitPortResolution() {
        val n1 = CanvasNode(id = "1", x = 0f, y = 0f, width = 100f, height = 100f)
        val n2 = CanvasNode(id = "2", x = 200f, y = 200f, width = 100f, height = 100f)

        val (start, end) = CanvasGeometry.resolvePorts(n1, n2, EdgePort.Top, EdgePort.Bottom)
        assertEquals(Offset(50f, 0f), start)
        assertEquals(Offset(250f, 300f), end)
    }

    @Test
    fun testBezierCurveHitTesting() {
        val start = Offset(100f, 100f)
        val end = Offset(500f, 100f)

        // Point directly near the middle of the horizontal curve
        assertTrue(CanvasGeometry.isPointNearBezier(Offset(300f, 100f), start, end, threshold = 15f))
        // Point near start
        assertTrue(CanvasGeometry.isPointNearBezier(Offset(110f, 102f), start, end, threshold = 15f))
        // Point far off
        assertFalse(CanvasGeometry.isPointNearBezier(Offset(300f, 400f), start, end, threshold = 15f))
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

    @Test
    fun testCalculateMidpoint() {
        val mid = CanvasGeometry.calculateMidpoint(Offset(100f, 200f), Offset(300f, 400f))
        assertEquals(200f, mid.x)
        assertEquals(300f, mid.y)
    }

    @Test
    fun testBezierGeometryHasStableEndpointsAndEndTangent() {
        val start = Offset(100f, 100f)
        val end = Offset(500f, 300f)
        val curve = CanvasGeometry.bezierBetween(start, end)

        assertEquals(start, curve.pointAt(0f))
        assertEquals(end, curve.pointAt(1f))
        assertEquals(end.y, curve.control2.y)
        assertTrue(curve.control2.x < end.x)
    }
}
