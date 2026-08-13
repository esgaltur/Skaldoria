package com.skaldoria.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.skaldoria.canvas.model.*
import com.skaldoria.canvas.state.CanvasState
import com.skaldoria.canvas.state.CanvasTool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CanvasStateTest {

    @Test
    fun testAddNodeAndSelection() {
        val state = CanvasState(CanvasDocument())
        assertEquals(0, state.nodes.size)

        val node = state.addNode(Offset(100f, 150f), "## Test Node", NodeColor.Emerald)
        assertEquals(1, state.nodes.size)
        assertEquals(100f, node.x)
        assertEquals(150f, node.y)
        assertEquals(NodeColor.Emerald, node.color)
        assertTrue(state.selectedNodeIds.contains(node.id))
    }

    @Test
    fun testMoveSelectedNodesMultiSelect() {
        val state = CanvasState(CanvasDocument())
        val n1 = state.addNode(Offset(50f, 50f))
        val n2 = state.addNode(Offset(200f, 200f))

        // Multi-select both
        state.selectNode(n1.id, multiSelect = false)
        state.selectNode(n2.id, multiSelect = true)
        assertEquals(setOf(n1.id, n2.id), state.selectedNodeIds)

        state.moveSelectedNodes(Offset(25f, 35f))
        val u1 = state.nodes.first { it.id == n1.id }
        val u2 = state.nodes.first { it.id == n2.id }
        assertEquals(75f, u1.x)
        assertEquals(85f, u1.y)
        assertEquals(225f, u2.x)
        assertEquals(235f, u2.y)
    }

    @Test
    fun testResizeNodeConstraints() {
        val state = CanvasState(CanvasDocument())
        val n = state.addNode(Offset(10f, 10f))
        val initialW = n.width
        val initialH = n.height

        // Expand
        state.resizeNode(n.id, 50f, 60f)
        val expanded = state.nodes.first { it.id == n.id }
        assertEquals(initialW + 50f, expanded.width)
        assertEquals(initialH + 60f, expanded.height)

        // Attempt shrinking below minimum (MIN_WIDTH = 140, MIN_HEIGHT = 90)
        state.resizeNode(n.id, -1000f, -1000f)
        val shrunk = state.nodes.first { it.id == n.id }
        assertEquals(CanvasNode.MIN_WIDTH, shrunk.width)
        assertEquals(CanvasNode.MIN_HEIGHT, shrunk.height)
    }

    @Test
    fun testAddEdgeAndCascadeDeletion() {
        val state = CanvasState(CanvasDocument())
        val n1 = state.addNode(Offset(0f, 0f))
        val n2 = state.addNode(Offset(300f, 0f))

        val edge = state.addEdge(fromId = n1.id, toId = n2.id, label = "Links to")
        assertNotNull(edge)
        assertEquals(1, state.edges.size)

        // Select and delete n1 -> edge should be automatically removed
        state.selectNode(n1.id)
        state.deleteSelected()

        assertEquals(1, state.nodes.size)
        assertEquals(0, state.edges.size)
    }

    @Test
    fun testEdgeStyleAndColorMutations() {
        val state = CanvasState(CanvasDocument())
        val n1 = state.addNode(Offset(0f, 0f))
        val n2 = state.addNode(Offset(200f, 0f))
        val edge = state.addEdge(fromId = n1.id, toId = n2.id, label = "Original")!!

        state.updateEdgeLabel(edge.id, "Updated Label")
        state.updateEdgeStyle(edge.id, EdgeStyle.Dashed)
        state.updateEdgeColor(edge.id, NodeColor.Cyan)

        val updated = state.edges.first { it.id == edge.id }
        assertEquals("Updated Label", updated.label)
        assertEquals(EdgeStyle.Dashed, updated.style)
        assertEquals(NodeColor.Cyan, updated.color)
    }

    @Test
    fun testConnectionLifecycleCreatesEdgeAtScreenTarget() {
        val state = CanvasState(CanvasDocument())
        val source = state.addNode(Offset(50f, 60f), width = 120f, height = 100f)
        val target = state.addNode(Offset(350f, 60f), width = 120f, height = 100f)
        state.panBy(Offset(25f, 15f))
        state.zoomAt(1.5f, Offset.Zero)

        val sourceScreen = state.viewport.canvasToScreen(source.center)
        val targetScreen = state.viewport.canvasToScreen(target.center)
        state.beginConnection(source.id, EdgePort.Right, sourceScreen)
        state.moveConnectionPointerBy(targetScreen - sourceScreen)
        val edge = state.finishConnection()

        assertNotNull(edge)
        assertEquals(source.id, edge.fromNodeId)
        assertEquals(target.id, edge.toNodeId)
        assertEquals(EdgePort.Right, edge.fromPort)
        assertNull(state.connectingSourceNodeId)
        assertNull(state.connectingTargetPosition)
    }

    @Test
    fun testCancelledConnectionCannotCreateEdge() {
        val state = CanvasState(CanvasDocument())
        val source = state.addNode(Offset.Zero)

        state.beginConnection(source.id, EdgePort.Auto, Offset(20f, 20f))
        state.cancelConnection()

        assertNull(state.finishConnection())
        assertTrue(state.edges.isEmpty())
    }

    @Test
    fun testEdgeHitTesting() {
        val state = CanvasState(CanvasDocument())
        val n1 = state.addNode(Offset(0f, 0f), width = 100f, height = 100f)
        val n2 = state.addNode(Offset(400f, 0f), width = 100f, height = 100f)
        val edge = state.addEdge(fromId = n1.id, toId = n2.id, fromPort = EdgePort.Right, toPort = EdgePort.Left)!!

        // Port Right of n1 is at (100, 50), Port Left of n2 is at (400, 50)
        // Midpoint should be around (250, 50)
        val hit = state.findEdgeAt(Offset(250f, 50f), threshold = 20f)
        assertNotNull(hit)
        assertEquals(edge.id, hit.id)

        // Far away point should miss
        val miss = state.findEdgeAt(Offset(250f, 300f), threshold = 20f)
        assertNull(miss)
    }

    @Test
    fun testMarqueeSelection() {
        val state = CanvasState(CanvasDocument())
        val n1 = state.addNode(Offset(50f, 50f), width = 100f, height = 100f)
        val n2 = state.addNode(Offset(300f, 50f), width = 100f, height = 100f)
        val n3 = state.addNode(Offset(600f, 50f), width = 100f, height = 100f)

        // Marquee covering n1 and n2 only (in screen pixels, default zoom=1, pan=0)
        val screenRect = Rect(0f, 0f, 450f, 200f)
        state.applyMarqueeSelection(screenRect)

        assertEquals(setOf(n1.id, n2.id), state.selectedNodeIds)
        assertFalse(state.selectedNodeIds.contains(n3.id))
    }

    @Test
    fun testViewportFocalPointZoom() {
        val state = CanvasState(CanvasDocument())
        val focalPoint = Offset(400f, 300f)

        // Zoom in by 2x
        state.zoomAt(2.0f, focalPoint)
        assertEquals(2.0f, state.viewport.zoom)

        // Canvas point under focal point before and after should map to the exact same screen coordinate
        val canvasPt = state.viewport.screenToCanvas(focalPoint)
        val backToScreen = state.viewport.canvasToScreen(canvasPt)
        assertEquals(focalPoint.x, backToScreen.x, 0.01f)
        assertEquals(focalPoint.y, backToScreen.y, 0.01f)
    }

    @Test
    fun testToolSelection() {
        val state = CanvasState(CanvasDocument())
        assertEquals(CanvasTool.Select, state.activeTool)

        state.activeTool = CanvasTool.Connect
        assertEquals(CanvasTool.Connect, state.activeTool)

        state.activeTool = CanvasTool.Pan
        assertEquals(CanvasTool.Pan, state.activeTool)
    }

    @Test
    fun testUndoRedo() {
        val state = CanvasState(CanvasDocument())
        val n1 = state.addNode(Offset(10f, 10f), "First")
        val n2 = state.addNode(Offset(20f, 20f), "Second")
        assertEquals(2, state.nodes.size)

        state.undo()
        assertEquals(1, state.nodes.size)

        state.redo()
        assertEquals(2, state.nodes.size)
    }

    @Test
    fun testNodeTransformIsOneUndoableOperation() {
        val state = CanvasState(CanvasDocument())
        val node = state.addNode(Offset(10f, 20f))

        state.beginNodeTransform()
        state.moveSelectedNodes(Offset(5f, 10f))
        state.moveSelectedNodes(Offset(15f, 20f))
        state.endNodeTransform()

        assertEquals(30f, state.nodes.single().x)
        assertEquals(50f, state.nodes.single().y)

        state.undo()

        assertEquals(node.x, state.nodes.single().x)
        assertEquals(node.y, state.nodes.single().y)
    }
}
