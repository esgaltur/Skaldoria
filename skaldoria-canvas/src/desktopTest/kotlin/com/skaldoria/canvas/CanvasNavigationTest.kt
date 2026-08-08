package com.skaldoria.canvas

import androidx.compose.ui.geometry.Offset
import com.skaldoria.canvas.model.CanvasDocument
import com.skaldoria.canvas.model.CanvasViewport
import com.skaldoria.canvas.state.CanvasState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CanvasNavigationTest {

    @Test
    fun testMouseWheelVerticalPan() {
        val state = CanvasState(CanvasDocument())
        assertEquals(0f, state.viewport.panX)
        assertEquals(0f, state.viewport.panY)

        val panSpeed = 40f
        // User scrolls mouse wheel down (scrollDelta.y = 1.0f)
        val scrollDeltaY = 1.0f
        val deltaY = -scrollDeltaY * panSpeed
        state.panBy(Offset(0f, deltaY))

        assertEquals(0f, state.viewport.panX)
        assertEquals(-40f, state.viewport.panY)

        // User scrolls mouse wheel up (scrollDelta.y = -2.0f)
        val scrollDeltaYUp = -2.0f
        val deltaYUp = -scrollDeltaYUp * panSpeed
        state.panBy(Offset(0f, deltaYUp))

        assertEquals(0f, state.viewport.panX)
        assertEquals(40f, state.viewport.panY)
    }

    @Test
    fun testShiftMouseWheelHorizontalPan() {
        val state = CanvasState(CanvasDocument())
        val panSpeed = 40f

        // User holds Shift and scrolls mouse wheel down (scrollDelta.y = 1.0f)
        val scrollDelta = 1.0f
        val deltaX = -scrollDelta * panSpeed
        state.panBy(Offset(deltaX, 0f))

        assertEquals(-40f, state.viewport.panX)
        assertEquals(0f, state.viewport.panY)
    }

    @Test
    fun testMiddleMouseDragPanning() {
        val state = CanvasState(CanvasDocument())
        state.panBy(Offset(120f, -85f))

        assertEquals(120f, state.viewport.panX)
        assertEquals(-85f, state.viewport.panY)
    }

    @Test
    fun testCtrlMouseWheelFocalZoomPreservesPointerCoordinate() {
        val state = CanvasState(CanvasDocument())
        val mousePos = Offset(640f, 400f)

        // Find world position under cursor before zoom
        val worldBefore = state.viewport.screenToCanvas(mousePos)

        // Zoom in by factor 1.25
        state.zoomAt(1.25f, mousePos)
        assertEquals(1.25f, state.viewport.zoom)

        // World position under cursor after zoom must remain identical
        val worldAfter = state.viewport.screenToCanvas(mousePos)
        assertEquals(worldBefore.x, worldAfter.x, 0.001f)
        assertEquals(worldBefore.y, worldAfter.y, 0.001f)
    }

    @Test
    fun testZoomClamping() {
        val state = CanvasState(CanvasDocument())
        val center = Offset(500f, 500f)

        // Zoom out beyond minimum
        state.zoomAt(0.01f, center)
        assertEquals(CanvasViewport.MIN_ZOOM, state.viewport.zoom)

        // Zoom in beyond maximum
        state.zoomAt(1000f, center)
        assertEquals(CanvasViewport.MAX_ZOOM, state.viewport.zoom)
    }

    @Test
    fun testZoomToFitCalculatesCorrectBoundingBox() {
        val state = CanvasState(CanvasDocument())
        state.addNode(Offset(100f, 100f), width = 200f, height = 100f)
        state.addNode(Offset(500f, 400f), width = 200f, height = 100f)

        state.zoomToFit(screenWidth = 1200f, screenHeight = 800f)

        // All nodes should now be inside screen coordinates [0..1200, 0..800]
        state.nodes.forEach { node ->
            val screenTopLeft = state.viewport.canvasToScreen(Offset(node.x, node.y))
            val screenBottomRight = state.viewport.canvasToScreen(Offset(node.x + node.width, node.y + node.height))

            assertTrue(screenTopLeft.x >= 0f, "Node ${node.id} left edge is out of bounds: ${screenTopLeft.x}")
            assertTrue(screenTopLeft.y >= 0f, "Node ${node.id} top edge is out of bounds: ${screenTopLeft.y}")
            assertTrue(screenBottomRight.x <= 1200f, "Node ${node.id} right edge is out of bounds: ${screenBottomRight.x}")
            assertTrue(screenBottomRight.y <= 800f, "Node ${node.id} bottom edge is out of bounds: ${screenBottomRight.y}")
        }
    }
}
