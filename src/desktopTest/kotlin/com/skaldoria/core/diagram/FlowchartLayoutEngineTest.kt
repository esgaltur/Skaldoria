package com.skaldoria.core.diagram

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** MMD-1 — layered flowchart layout. */
class FlowchartLayoutEngineTest {

    private fun layerOf(layout: FlowchartLayoutEngine.Layout, id: String): Int =
        assertNotNull(layout.placementOf(id), "missing $id").layer

    @Test
    fun `a linear chain occupies one node per layer`() {
        val layout = FlowchartLayoutEngine.layout(
            listOf("A", "B", "C"),
            listOf("A" to "B", "B" to "C")
        )

        assertEquals(3, layout.layerCount)
        assertEquals(1, layout.maxLayerWidth)
        assertEquals(0, layerOf(layout, "A"))
        assertEquals(1, layerOf(layout, "B"))
        assertEquals(2, layerOf(layout, "C"))
    }

    /**
     * The defining case. Previously drawn as `A → B → C → D`; the branches must share a
     * layer, not queue up behind each other.
     */
    @Test
    fun `a star fans its branches into one shared layer`() {
        val layout = FlowchartLayoutEngine.layout(
            listOf("A", "B", "C", "D"),
            listOf("A" to "B", "A" to "C", "A" to "D")
        )

        assertEquals(2, layout.layerCount, "a star is two layers deep, not four")
        assertEquals(3, layout.maxLayerWidth, "all three branches sit side by side")
        assertEquals(0, layerOf(layout, "A"))
        assertEquals(listOf(1, 1, 1), listOf("B", "C", "D").map { layerOf(layout, it) })
        assertEquals(setOf(0, 1, 2), layout.layer(1).map { it.order }.toSet(), "branch order is unique")
    }

    /** The app's own DIAGRAM template: a chain that then branches. */
    @Test
    fun `the built-in template lays out as chain then branch`() {
        val layout = FlowchartLayoutEngine.layout(
            listOf("A", "B", "C", "D", "E"),
            listOf("A" to "B", "B" to "C", "C" to "D", "C" to "E")
        )

        assertEquals(4, layout.layerCount)
        assertEquals(layerOf(layout, "D"), layerOf(layout, "E"), "both outcomes share a layer")
        assertEquals(2, layout.maxLayerWidth)
    }

    @Test
    fun `a merge places the join after its deepest input`() {
        val layout = FlowchartLayoutEngine.layout(
            listOf("A", "B", "C", "D"),
            listOf("A" to "B", "B" to "D", "A" to "C", "C" to "D")
        )

        assertEquals(2, layerOf(layout, "D"), "longest path wins, so D sits below both branches")
        assertEquals(layerOf(layout, "B"), layerOf(layout, "C"))
    }

    /** A diagram with a loop must still lay out rather than hang or throw. */
    @Test
    fun `cycles are broken instead of looping forever`() {
        val layout = FlowchartLayoutEngine.layout(
            listOf("A", "B", "C"),
            listOf("A" to "B", "B" to "C", "C" to "A")
        )

        assertEquals(3, layout.placements.size)
        assertTrue(layout.layerCount in 1..3)
        assertTrue(layout.placements.all { it.layer >= 0 })
    }

    @Test
    fun `self edges and dangling edges are ignored`() {
        val layout = FlowchartLayoutEngine.layout(
            listOf("A", "B"),
            listOf("A" to "A", "A" to "B", "A" to "GHOST", "GHOST" to "B")
        )

        assertEquals(2, layout.placements.size)
        assertEquals(0, layerOf(layout, "A"))
        assertEquals(1, layerOf(layout, "B"))
    }

    @Test
    fun `disconnected nodes all start at the first layer`() {
        val layout = FlowchartLayoutEngine.layout(listOf("A", "B", "C"), emptyList())

        assertEquals(1, layout.layerCount)
        assertEquals(3, layout.maxLayerWidth)
        assertEquals(listOf(0, 1, 2), layout.layer(0).map { it.order })
    }

    @Test
    fun `orders within a layer are unique and contiguous`() {
        val layout = FlowchartLayoutEngine.layout(
            listOf("R", "A", "B", "C", "D"),
            listOf("R" to "A", "R" to "B", "R" to "C", "R" to "D")
        )

        val orders = layout.layer(1).map { it.order }
        assertEquals(listOf(0, 1, 2, 3), orders.sorted())
    }

    @Test
    fun `empty input is handled`() {
        val layout = FlowchartLayoutEngine.layout(emptyList(), emptyList())
        assertEquals(0, layout.layerCount)
        assertTrue(layout.placements.isEmpty())
    }
}
