package com.skaldoria.canvas

import com.skaldoria.canvas.io.CanvasSerializer
import com.skaldoria.canvas.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

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
}
