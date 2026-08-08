package com.skaldoria.canvas

import androidx.compose.ui.geometry.Offset
import com.skaldoria.canvas.model.CanvasDocument
import com.skaldoria.canvas.model.NodeColor
import com.skaldoria.canvas.state.CanvasState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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
    fun testMoveSelectedNodes() {
        val state = CanvasState(CanvasDocument())
        val node = state.addNode(Offset(50f, 50f))
        state.selectNode(node.id)

        state.moveSelectedNodes(Offset(20f, 30f))
        val updated = state.nodes.first { it.id == node.id }
        assertEquals(70f, updated.x)
        assertEquals(80f, updated.y)
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
}
