package com.skaldoria.canvas.io

import com.skaldoria.canvas.model.CanvasDocument
import com.skaldoria.canvas.model.CanvasNode

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

        val orderedNodes = orderNodes(doc)
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

        val orderedNodes = orderNodes(doc)
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

    /**
     * Orders nodes for linear presentation: every card after all of the cards that point at it.
     *
     * Kahn's algorithm over the edges. The work-list takes newly-ready children at the *front*,
     * so a chain is read straight through before the next independent root starts, and ties are
     * broken spatially (top-to-bottom, then left-to-right) so the order is deterministic.
     *
     * The previous breadth-first walk never counted incoming edges: with `A → C` and `B → C` it
     * emitted `A, C, B`, putting C in the deck before one of the cards it depends on.
     *
     * A cycle has no valid order. When nothing is ready but cards remain, the spatially-first of
     * them is taken as if it were ready, which breaks the cycle without dropping any card.
     */
    fun orderNodes(doc: CanvasDocument): List<CanvasNode> {
        val spatially = compareBy<CanvasNode>({ it.y }, { it.x })
        val nodeMap = doc.nodes.associateBy { it.id }
        val children = doc.nodes.associate { it.id to mutableListOf<CanvasNode>() }
        val pendingParents = doc.nodes.associate { it.id to 0 }.toMutableMap()

        doc.edges.forEach { edge ->
            val to = nodeMap[edge.toNodeId] ?: return@forEach
            if (edge.fromNodeId == edge.toNodeId || edge.fromNodeId !in nodeMap) return@forEach
            children.getValue(edge.fromNodeId) += to
            pendingParents[to.id] = pendingParents.getValue(to.id) + 1
        }

        val emitted = LinkedHashSet<String>()
        val ready = ArrayDeque(doc.nodes.filter { pendingParents.getValue(it.id) == 0 }.sortedWith(spatially))

        while (emitted.size < doc.nodes.size) {
            val next = ready.removeFirstOrNull()
                ?: doc.nodes.filter { it.id !in emitted }.minWith(spatially)
            if (!emitted.add(next.id)) continue

            val nowReady = mutableListOf<CanvasNode>()
            for (child in children.getValue(next.id)) {
                if (child.id in emitted) continue
                val remaining = pendingParents.getValue(child.id) - 1
                pendingParents[child.id] = remaining
                if (remaining == 0) nowReady += child
            }
            nowReady.sortedWith(spatially).asReversed().forEach(ready::addFirst)
        }

        return emitted.map(nodeMap::getValue)
    }
}
