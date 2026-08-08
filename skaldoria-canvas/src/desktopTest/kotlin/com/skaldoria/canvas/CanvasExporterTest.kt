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
}
