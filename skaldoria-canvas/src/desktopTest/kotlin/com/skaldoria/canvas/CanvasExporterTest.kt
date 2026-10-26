package com.skaldoria.canvas

import com.skaldoria.canvas.io.CanvasExporter
import com.skaldoria.canvas.model.CanvasDocument
import com.skaldoria.canvas.model.CanvasEdge
import com.skaldoria.canvas.model.CanvasNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CanvasExporterTest {

    @Test
    fun testLinearPresentationDeckCompilation() {
        val n1 = CanvasNode(id = "1", x = 0f, y = 0f, markdown = "# Slide 1: Intro\n\nIntroductory points.")
        val n2 = CanvasNode(id = "2", x = 400f, y = 0f, markdown = "## Slide 2: Architecture\n\nSystem breakdown.")
        val n3 = CanvasNode(id = "3", x = 800f, y = 0f, markdown = "### Slide 3: Conclusion\n\nFinal remarks.")

        val e1 = CanvasEdge(id = "e1", fromNodeId = "1", toNodeId = "2")
        val e2 = CanvasEdge(id = "e2", fromNodeId = "2", toNodeId = "3")

        val doc = CanvasDocument(
            title = "Architecture Deck",
            nodes = listOf(n1, n2, n3),
            edges = listOf(e1, e2)
        )

        val exportedDeck = CanvasExporter.exportToPresentationDeck(doc)
        val slides = exportedDeck.split("\n\n---\n\n")

        assertEquals(3, slides.size)
        assertEquals("# Slide 1: Intro\n\nIntroductory points.", slides[0])
        assertEquals("## Slide 2: Architecture\n\nSystem breakdown.", slides[1])
        assertEquals("### Slide 3: Conclusion\n\nFinal remarks.", slides[2])
    }

    @Test
    fun testExportToMarkdownDocument() {
        val n1 = CanvasNode(id = "1", x = 0f, y = 0f, markdown = "## Step 1\n\nFirst step.")
        val n2 = CanvasNode(id = "2", x = 0f, y = 200f, markdown = "## Step 2\n\nSecond step.")

        val doc = CanvasDocument(
            title = "Process Guide",
            nodes = listOf(n1, n2)
        )

        val exportedDoc = CanvasExporter.exportToMarkdownDocument(doc)
        assertTrue(exportedDoc.startsWith("# Process Guide"))
        assertTrue(exportedDoc.contains("## Step 1\n\nFirst step."))
        assertTrue(exportedDoc.contains("## Step 2\n\nSecond step."))
    }

    private fun ids(doc: CanvasDocument) = CanvasExporter.orderNodes(doc).map { it.id }

    @Test
    fun `a card with two parents comes after both of them`() {
        val doc = CanvasDocument(
            nodes = listOf(
                CanvasNode(id = "A", x = 0f, y = 0f),
                CanvasNode(id = "B", x = 0f, y = 400f),
                CanvasNode(id = "C", x = 400f, y = 200f)
            ),
            edges = listOf(
                CanvasEdge(id = "ac", fromNodeId = "A", toNodeId = "C"),
                CanvasEdge(id = "bc", fromNodeId = "B", toNodeId = "C")
            )
        )

        assertEquals(listOf("A", "B", "C"), ids(doc))
    }

    @Test
    fun `a chain is read straight through before the next independent one`() {
        val doc = CanvasDocument(
            nodes = listOf(
                CanvasNode(id = "A1", x = 0f, y = 0f),
                CanvasNode(id = "A2", x = 0f, y = 600f),
                CanvasNode(id = "B1", x = 400f, y = 300f)
            ),
            edges = listOf(CanvasEdge(id = "a", fromNodeId = "A1", toNodeId = "A2"))
        )

        assertEquals(listOf("A1", "A2", "B1"), ids(doc))
    }

    @Test
    fun `a cycle still exports every card exactly once`() {
        val doc = CanvasDocument(
            nodes = listOf(
                CanvasNode(id = "X", x = 0f, y = 0f),
                CanvasNode(id = "Y", x = 400f, y = 0f),
                CanvasNode(id = "Z", x = 800f, y = 0f)
            ),
            edges = listOf(
                CanvasEdge(id = "xy", fromNodeId = "X", toNodeId = "Y"),
                CanvasEdge(id = "yz", fromNodeId = "Y", toNodeId = "Z"),
                CanvasEdge(id = "zx", fromNodeId = "Z", toNodeId = "X")
            )
        )

        assertEquals(listOf("X", "Y", "Z"), ids(doc))
    }

    @Test
    fun `edges to missing cards and self loops are ignored`() {
        val doc = CanvasDocument(
            nodes = listOf(CanvasNode(id = "A", x = 0f, y = 0f), CanvasNode(id = "B", x = 0f, y = 300f)),
            edges = listOf(
                CanvasEdge(id = "ghost", fromNodeId = "missing", toNodeId = "A"),
                CanvasEdge(id = "self", fromNodeId = "B", toNodeId = "B")
            )
        )

        assertEquals(listOf("A", "B"), ids(doc))
    }
}
