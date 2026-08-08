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
     * Orders nodes for linear presentation:
     * 1. Topologically sorts connected subgraphs using in-degree & outgoing edge flow.
     * 2. Places disconnected nodes / independent clusters in spatial order (top-to-bottom, left-to-right).
     */
    fun orderNodes(doc: CanvasDocument): List<CanvasNode> {
        val nodeMap = doc.nodes.associateBy { it.id }
        val adjList = mutableMapOf<String, MutableList<String>>()
        val inDegree = mutableMapOf<String, Int>()

        doc.nodes.forEach {
            adjList[it.id] = mutableListOf()
            inDegree[it.id] = 0
        }

        doc.edges.forEach { edge ->
            if (nodeMap.containsKey(edge.fromNodeId) && nodeMap.containsKey(edge.toNodeId)) {
                adjList[edge.fromNodeId]?.add(edge.toNodeId)
                inDegree[edge.toNodeId] = (inDegree[edge.toNodeId] ?: 0) + 1
            }
        }

        val visited = mutableSetOf<String>()
        val result = mutableListOf<CanvasNode>()

        // Find root nodes (inDegree == 0) sorted spatially
        val rootNodes = doc.nodes
            .filter { (inDegree[it.id] ?: 0) == 0 }
            .sortedWith(compareBy({ it.y }, { it.x }))

        // BFS / topological traversal from each root
        fun traverseFrom(startNodeId: String) {
            val queue = ArrayDeque<String>()
            queue.add(startNodeId)
            visited.add(startNodeId)

            while (queue.isNotEmpty()) {
                val currentId = queue.removeFirst()
                val node = nodeMap[currentId]
                if (node != null) {
                    result.add(node)
                }

                val neighbors = (adjList[currentId] ?: emptyList())
                    .filter { !visited.contains(it) }
                    .sortedBy { nodeMap[it]?.x ?: 0f }

                for (neighbor in neighbors) {
                    visited.add(neighbor)
                    queue.add(neighbor)
                }
            }
        }

        for (root in rootNodes) {
            if (!visited.contains(root.id)) {
                traverseFrom(root.id)
            }
        }

        // Remaining nodes (e.g. cycles or isolated components)
        val remaining = doc.nodes
            .filter { !visited.contains(it.id) }
            .sortedWith(compareBy({ it.y }, { it.x }))

        for (rem in remaining) {
            if (!visited.contains(rem.id)) {
                traverseFrom(rem.id)
            }
        }

        return result
    }
}
