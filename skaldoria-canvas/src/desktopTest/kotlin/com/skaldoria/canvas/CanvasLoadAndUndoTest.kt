package com.skaldoria.canvas

import com.skaldoria.canvas.model.CanvasPoint
import com.skaldoria.canvas.io.CanvasSerializer
import com.skaldoria.canvas.model.CanvasDocument
import com.skaldoria.canvas.model.CanvasNode
import com.skaldoria.canvas.model.CanvasViewport
import com.skaldoria.canvas.model.NodeColor
import com.skaldoria.canvas.state.CanvasState
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CanvasLoadAndUndoTest {

    // ---- undo is about content, not navigation ----

    @Test
    fun `undo does not throw the view back to where it was`() {
        val state = CanvasState(CanvasDocument())
        state.addNode(CanvasPoint(10f, 10f))
        state.panBy(CanvasPoint(400f, -250f))
        val viewAfterPanning = state.viewport

        state.undo()

        assertTrue(state.nodes.isEmpty())
        assertEquals(viewAfterPanning, state.viewport)

        state.redo()
        assertEquals(1, state.nodes.size)
        assertEquals(viewAfterPanning, state.viewport)
    }

    @Test
    fun `undoing an add clears the selection of the card that no longer exists`() {
        val state = CanvasState(CanvasDocument())
        state.addNode(CanvasPoint(10f, 10f))
        state.undo()

        assertTrue(state.selectedNodeIds.isEmpty())
        state.deleteSelected()
        state.undo()
        assertTrue(state.nodes.isEmpty(), "a stale selection must not have recorded a phantom history entry")
    }

    @Test
    fun `undoing an edge clears its selection`() {
        val state = CanvasState(CanvasDocument())
        val a = state.addNode(CanvasPoint(0f, 0f))
        val b = state.addNode(CanvasPoint(500f, 0f))
        state.addEdge(a.id, b.id)

        state.undo()

        assertNull(state.selectedEdgeId)
    }

    // ---- loading normalises what gestures could never produce ----

    @Test
    fun `a zoom outside the supported range is clamped on load`() {
        val zero = CanvasSerializer.fromJson("""{"viewport": {"panX": 0, "panY": 0, "zoom": 0}}""")
        val huge = CanvasSerializer.fromJson("""{"viewport": {"panX": 0, "panY": 0, "zoom": 1e9}}""")

        assertEquals(CanvasViewport.MIN_ZOOM, zero.viewport.zoom)
        assertEquals(CanvasViewport.MAX_ZOOM, huge.viewport.zoom)
    }

    @Test
    fun `a non-positive node size falls back to the default`() {
        val doc = CanvasSerializer.fromJson("""{"nodes": [{"id": "a", "width": 0, "height": -40}]}""")

        assertEquals(CanvasNode.DEFAULT_WIDTH, doc.nodes.single().width)
        assertEquals(CanvasNode.DEFAULT_HEIGHT, doc.nodes.single().height)
    }

    @Test
    fun `a small positive node size is kept as written`() {
        val doc = CanvasSerializer.fromJson("""{"nodes": [{"id": "a", "width": 250, "height": 100}]}""")

        assertEquals(100f, doc.nodes.single().height)
    }

    @Test
    fun `the bundled sample uses only colours the palette has`() {
        val sample = File("Ingestion_System_Design.canvas").takeIf { it.exists() }
            ?: File("../Ingestion_System_Design.canvas")
        val raw = sample.readText()
        val doc = CanvasSerializer.fromJson(raw)
        val written = Regex(""""color": "(\w+)"""").findAll(raw).map { it.groupValues[1] }.toSet()

        assertTrue(written.all { name -> NodeColor.entries.any { it.name == name } }, "unknown colours in the sample: $written")
        assertTrue(doc.nodes.any { it.color != NodeColor.Default })
    }
}
