package com.skaldoria.canvas.io

import com.skaldoria.canvas.model.CanvasDocument
import com.skaldoria.canvas.model.CanvasReadingOrder

/**
 * Compiles a spatial 2D Canvas graph into linear Skaldoria presentation decks or Markdown documents.
 */
object CanvasExporter {

    /**
     * Traverses the canvas graph and produces a multi-slide Skaldoria presentation (`.md`)
     * where each node becomes a slide separated by `---` thematic breaks.
     */
    fun exportToPresentationDeck(doc: CanvasDocument): String {
        if (doc.nodes.isEmpty()) {
            return "# ${doc.title}\n\n*Empty canvas*"
        }

        val orderedNodes = CanvasReadingOrder.of(doc)
        val sb = StringBuilder()

        orderedNodes.forEachIndexed { index, node ->
            val content = node.markdown.trim()
            if (content.isNotEmpty()) {
                sb.append(content)
            } else {
                sb.append("<!-- Empty Slide -->")
            }

            if (index < orderedNodes.size - 1) {
                sb.append("\n\n---\n\n")
            }
        }

        return sb.toString()
    }

    /**
     * Compiles all cards on the canvas into a continuous structured Markdown document.
     */
    fun exportToMarkdownDocument(doc: CanvasDocument): String {
        if (doc.nodes.isEmpty()) {
            return "# ${doc.title}\n\n*Empty canvas*"
        }

        val orderedNodes = CanvasReadingOrder.of(doc)
        val sb = StringBuilder()
        sb.append("# ${doc.title}\n\n")

        orderedNodes.forEach { node ->
            val content = node.markdown.trim()
            if (content.isNotEmpty()) {
                sb.append(content).append("\n\n")
            }
        }

        return sb.toString().trimEnd()
    }
}
