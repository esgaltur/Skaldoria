package com.skaldoria.canvas

import com.skaldoria.canvas.io.CanvasFormatException
import com.skaldoria.canvas.io.CanvasSerializer
import com.skaldoria.canvas.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CanvasSerializerTest {

    @Test
    fun testJsonSerializationRoundtrip() {
        val node1 = CanvasNode(
            id = "node-1",
            x = 50f,
            y = 100f,
            width = 300f,
            height = 200f,
            markdown = "# Title\n\n- Item 1\n- Item \"quoted\"",
            color = NodeColor.Indigo,
            zIndex = 1
        )
        val node2 = CanvasNode(
            id = "node-2",
            x = 450f,
            y = 100f,
            width = 320f,
            height = 220f,
            markdown = "## Section 2\n\n```mermaid\ngraph TD\n  A-->B\n```",
            color = NodeColor.Cyan,
            zIndex = 2
        )

        val edge1 = CanvasEdge(
            id = "edge-1",
            fromNodeId = "node-1",
            toNodeId = "node-2",
            fromPort = EdgePort.Right,
            toPort = EdgePort.Left,
            label = "Next",
            style = EdgeStyle.Dashed,
            color = NodeColor.Indigo
        )

        val doc = CanvasDocument(
            version = 1,
            title = "Test Whiteboard",
            nodes = listOf(node1, node2),
            edges = listOf(edge1),
            viewport = CanvasViewport(panX = 25f, panY = 50f, zoom = 1.25f)
        )

        val json = CanvasSerializer.toJson(doc)
        assertNotNull(json)

        val parsed = CanvasSerializer.fromJson(json)
        assertEquals(doc.version, parsed.version)
        assertEquals(doc.title, parsed.title)
        assertEquals(2, parsed.nodes.size)
        assertEquals(1, parsed.edges.size)

        assertEquals("node-1", parsed.nodes[0].id)
        assertEquals(50f, parsed.nodes[0].x)
        assertEquals(NodeColor.Indigo, parsed.nodes[0].color)
        assertEquals("# Title\n\n- Item 1\n- Item \"quoted\"", parsed.nodes[0].markdown)

        assertEquals("edge-1", parsed.edges[0].id)
        assertEquals("node-1", parsed.edges[0].fromNodeId)
        assertEquals("node-2", parsed.edges[0].toNodeId)
        assertEquals(EdgePort.Right, parsed.edges[0].fromPort)
        assertEquals(EdgePort.Left, parsed.edges[0].toPort)
        assertEquals("Next", parsed.edges[0].label)
        assertEquals(EdgeStyle.Dashed, parsed.edges[0].style)
        assertEquals(NodeColor.Indigo, parsed.edges[0].color)

        assertEquals(25f, parsed.viewport.panX)
        assertEquals(50f, parsed.viewport.panY)
        assertEquals(1.25f, parsed.viewport.zoom)
    }

    @Test
    fun testEmptyDocumentParsing() {
        val emptyDoc = CanvasDocument()
        val json = CanvasSerializer.toJson(emptyDoc)
        val parsed = CanvasSerializer.fromJson(json)

        assertEquals("Untitled Canvas", parsed.title)
        assertEquals(0, parsed.nodes.size)
        assertEquals(0, parsed.edges.size)
    }

    // --- Malformed input is rejected, never silently reinterpreted -------------------------
    //
    // The previous reader typed a token by what it looked like, so every case below parsed to
    // *something* and returned a document. The round-trip tests above could not see any of it,
    // because they only ever fed the reader the writer's own output.

    @Test
    fun `a stray token inside an object is rejected`() {
        // The exact corruption found in Ingestion_System_Design.canvas.
        assertRejected("""{"version": 1, "nodes": [{"id": "a", "zIndex": 2,abc}]}""")
    }

    @Test
    fun `a truncated document is rejected`() {
        val valid = CanvasSerializer.toJson(documentWithOneNode())
        assertRejected(valid.substring(0, valid.length / 2))
    }

    @Test
    fun `trailing content after the document is rejected`() {
        assertRejected(CanvasSerializer.toJson(documentWithOneNode()) + "garbage")
    }

    @Test
    fun `an unterminated string is rejected`() {
        assertRejected("""{"title": "never closed}""")
    }

    @Test
    fun `a missing colon is rejected`() {
        assertRejected("""{"version" 1}""")
    }

    @Test
    fun `a non-object root is rejected`() {
        assertRejected("[]")
        assertRejected("\"just a string\"")
        assertRejected("")
    }

    @Test
    fun `a node without an id is rejected rather than dropped`() {
        val failure = assertFailsWith<CanvasFormatException> {
            CanvasSerializer.fromJson("""{"nodes": [{"x": 1, "y": 2}]}""")
        }
        assertTrue(failure.message!!.contains("nodes[0]"), "message should locate the entry: ${failure.message}")
    }

    @Test
    fun `a collection of the wrong shape is rejected rather than read as empty`() {
        assertRejected("""{"nodes": "oops"}""")
        assertRejected("""{"edges": 7}""")
        assertRejected("""{"nodes": [1, 2]}""")
        assertRejected("""{"viewport": "oops"}""")
    }

    // --- Tolerated, deliberately ------------------------------------------------------------

    @Test
    fun `an absent or null collection is an empty canvas, not a corrupt one`() {
        assertEquals(0, CanvasSerializer.fromJson("""{"title": "Empty"}""").nodes.size)
        assertEquals(0, CanvasSerializer.fromJson("""{"nodes": null, "edges": null}""").edges.size)
        assertEquals(1f, CanvasSerializer.fromJson("""{"viewport": null}""").viewport.zoom)
    }

    @Test
    fun `an enum name from a newer build falls back without losing the node`() {
        val parsed = CanvasSerializer.fromJson(
            """{"nodes": [{"id": "a", "x": 5, "y": 6, "shape": "Trapezoid", "color": "Chartreuse"}]}"""
        )

        val node = parsed.nodes.single()
        assertEquals(NodeShape.Card, node.shape)
        assertEquals(NodeColor.Default, node.color)
        // The point of falling back: everything else about the node survives.
        assertEquals("a", node.id)
        assertEquals(5f, node.x)
        assertEquals(6f, node.y)
    }

    @Test
    fun `an integer written with a decimal point still reads`() {
        val parsed = CanvasSerializer.fromJson("""{"nodes": [{"id": "a", "zIndex": 3.0}]}""")
        assertEquals(3, parsed.nodes.single().zIndex)
    }

    @Test
    fun `a non-finite viewport value round-trips instead of writing unreadable JSON`() {
        // JSON has no NaN/Infinity, so the writer substitutes; the test that matters is that
        // whatever it writes can be read back, because the reader is now strict.
        val document = CanvasDocument(
            viewport = CanvasViewport(panX = Float.NaN, panY = 0f, zoom = Float.POSITIVE_INFINITY)
        )

        val parsed = CanvasSerializer.fromJson(CanvasSerializer.toJson(document))
        assertEquals(0f, parsed.viewport.panX)
        assertEquals(1f, parsed.viewport.zoom)
    }

    @Test
    fun `a title containing quotes and control characters round-trips`() {
        val document = CanvasDocument(title = "a \"quoted\"  title\nwith a newline")
        val parsed = CanvasSerializer.fromJson(CanvasSerializer.toJson(document))
        assertEquals(document.title, parsed.title)
    }

    private fun documentWithOneNode() = CanvasDocument(
        title = "One",
        nodes = listOf(CanvasNode(id = "node-1", x = 1f, y = 2f))
    )

    private fun assertRejected(json: String) {
        assertFailsWith<CanvasFormatException>("should have rejected: $json") {
            CanvasSerializer.fromJson(json)
        }
    }
}
