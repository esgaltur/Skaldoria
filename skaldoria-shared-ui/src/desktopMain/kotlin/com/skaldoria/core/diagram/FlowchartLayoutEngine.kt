package com.skaldoria.core.diagram

/**
 * Assigns flowchart nodes to layers and orders them within each layer.
 *
 * MMD-1: the renderer used to walk nodes in parse order and emit them as one straight
 * chain, consulting edges only to pick a label. A star (`A-->B`, `A-->C`, `A-->D`) drew as
 * `A → B → C → D`, and forcing a wide graph onto one line is also what made it overflow.
 *
 * A layered (Sugiyama-style) approach: assign each node a layer by longest path from a
 * root, then reduce crossings within each layer with a barycentre heuristic. Pure and
 * Compose-free so the topology is unit testable without a UI harness.
 */
object FlowchartLayoutEngine {

    /** Placement of one node: which layer, and its position within that layer. */
    data class Placement(val id: String, val layer: Int, val order: Int)

    data class Layout(
        val placements: List<Placement>,
        val layerCount: Int,
        /** Widest layer — drives how much room the diagram needs across the flow axis. */
        val maxLayerWidth: Int
    ) {
        fun placementOf(id: String): Placement? = placements.firstOrNull { it.id == id }
        fun layer(index: Int): List<Placement> = placements.filter { it.layer == index }.sortedBy { it.order }
    }

    private const val CROSSING_REDUCTION_PASSES = 4

    fun layout(nodeIds: List<String>, edges: List<Pair<String, String>>): Layout {
        if (nodeIds.isEmpty()) return Layout(emptyList(), 0, 0)

        val known = nodeIds.toSet()
        val valid = edges.filter { it.first in known && it.second in known && it.first != it.second }
        val predecessors = nodeIds.associateWith { id -> valid.filter { it.second == id }.map { it.first } }
        val successors = nodeIds.associateWith { id -> valid.filter { it.first == id }.map { it.second } }

        val layerOf = compactLayers(assignLayers(nodeIds, predecessors))
        val ordered = reduceCrossings(nodeIds, layerOf, predecessors, successors)

        val placements = ordered.map { (id, order) -> Placement(id, layerOf.getValue(id), order) }
        val layerCount = (layerOf.values.maxOrNull() ?: 0) + 1
        val maxLayerWidth = (0 until layerCount).maxOfOrNull { layer -> placements.count { it.layer == layer } } ?: 0

        return Layout(placements, layerCount, maxLayerWidth)
    }

    /**
     * Longest path from a root. Cycles are broken by treating a back edge as if the node
     * were a root — a flowchart with a loop still has to render, and refusing to lay it
     * out would be worse than laying it out imperfectly.
     */
    private fun assignLayers(
        nodeIds: List<String>,
        predecessors: Map<String, List<String>>
    ): Map<String, Int> {
        val layer = mutableMapOf<String, Int>()
        val onStack = mutableSetOf<String>()

        fun depthOf(id: String): Int {
            layer[id]?.let { return it }
            if (!onStack.add(id)) return 0 // back edge — stop descending
            val depth = predecessors[id].orEmpty()
                .maxOfOrNull { depthOf(it) + 1 }
                ?: 0
            onStack.remove(id)
            layer[id] = depth
            return depth
        }

        nodeIds.forEach { depthOf(it) }
        return layer
    }

    /**
     * Removes gaps so layer indices run 0..n-1 with nothing empty in between.
     *
     * Cycle breaking can push a node's longest path beyond the node count — a 3-node loop
     * otherwise lands on layers 1, 2 and 3, reserving four rows of canvas for three nodes.
     * Compacting also guarantees `layerCount <= nodeCount`.
     */
    private fun compactLayers(raw: Map<String, Int>): Map<String, Int> {
        if (raw.isEmpty()) return raw
        val remap = raw.values.distinct().sorted().withIndex().associate { (index, value) -> value to index }
        return raw.mapValues { (_, value) -> remap.getValue(value) }
    }

    /**
     * Barycentre ordering: repeatedly place each node near the average position of its
     * neighbours in the adjacent layer, alternating sweep direction. A handful of passes
     * is enough for the diagram sizes a slide can hold.
     */
    private fun reduceCrossings(
        nodeIds: List<String>,
        layerOf: Map<String, Int>,
        predecessors: Map<String, List<String>>,
        successors: Map<String, List<String>>
    ): Map<String, Int> {
        val layerCount = (layerOf.values.maxOrNull() ?: 0) + 1

        // Seed with declaration order, which keeps simple diagrams looking as authored.
        val order = mutableMapOf<String, Int>()
        for (layerIndex in 0 until layerCount) {
            nodeIds.filter { layerOf[it] == layerIndex }
                .forEachIndexed { position, id -> order[id] = position }
        }

        fun sweep(layerIndex: Int, neighbours: Map<String, List<String>>) {
            val members = nodeIds.filter { layerOf[it] == layerIndex }
            if (members.size < 2) return
            val barycentre = members.associateWith { id ->
                val positions = neighbours[id].orEmpty().mapNotNull { order[it] }
                if (positions.isEmpty()) order.getValue(id).toDouble() else positions.average()
            }
            members.sortedWith(compareBy({ barycentre.getValue(it) }, { order.getValue(it) }))
                .forEachIndexed { position, id -> order[id] = position }
        }

        repeat(CROSSING_REDUCTION_PASSES) {
            for (layerIndex in 1 until layerCount) sweep(layerIndex, predecessors)
            for (layerIndex in layerCount - 2 downTo 0) sweep(layerIndex, successors)
        }

        return order
    }
}
