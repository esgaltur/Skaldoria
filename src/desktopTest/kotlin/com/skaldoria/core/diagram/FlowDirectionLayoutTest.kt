package com.skaldoria.core.diagram

import androidx.compose.ui.unit.IntSize
import com.skaldoria.ui.components.MermaidParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * DIA-08 — all four Mermaid directions reach the geometry.
 *
 * The feature was built and threaded end to end — [FlowDirection] parses `LR`/`RL`/`TD`/`BT`,
 * `ParsedDiagram` carries it, `FlowchartGraphView` passes `reversed` into
 * [FlowchartScene.arrange] — and **nothing asserted any of it**. The index still listed DIA-08
 * as proposed, so the shipped state was: implemented, unguarded, and reported as absent.
 *
 * A reversed direction is the *same* layout mirrored, so these assert the mirror rather than
 * re-deriving positions: the axis is unchanged, the order along it is inverted, and the extent
 * is preserved. That holds whatever the layout engine does next, which a golden coordinate
 * would not.
 */
class FlowDirectionLayoutTest {

    private fun sceneFor(mermaid: String): FlowchartScene {
        val diagram = MermaidParser.parse(mermaid)
        val layout = FlowchartLayoutEngine.layout(
            nodeIds = diagram.nodes.map { it.id },
            edges = diagram.edges.map { it.fromId to it.toId }
        )
        return FlowchartScene.arrange(
            layout = layout,
            nodeIds = diagram.nodes.map { it.id },
            nodeSize = diagram.nodes.associate { it.id to IntSize(120, 60) },
            edges = diagram.edges.map { DiagramEdge(it.fromId, it.toId, it.label, it.isDashed) },
            horizontal = diagram.isHorizontal,
            availableBounds = IntSize(1280, 720),
            labelWidths = emptyMap(),
            reversed = diagram.direction.isReversed
        )
    }

    private val chain = "A[First] --> B[Second] --> C[Third]"

    // ---- the header is read correctly ---------------------------------------

    @Test
    fun `every direction keyword is recognised`() {
        assertEquals(FlowDirection.LR, MermaidParser.parse("graph LR\n$chain").direction)
        assertEquals(FlowDirection.RL, MermaidParser.parse("graph RL\n$chain").direction)
        assertEquals(FlowDirection.TD, MermaidParser.parse("graph TD\n$chain").direction)
        // TB is Mermaid's synonym for TD; the enum deliberately has no separate constant.
        assertEquals(FlowDirection.TD, MermaidParser.parse("graph TB\n$chain").direction)
        assertEquals(FlowDirection.BT, MermaidParser.parse("flowchart BT\n$chain").direction)
    }

    @Test
    fun `an unlabelled diagram keeps the historical default`() {
        assertEquals(FlowDirection.LR, MermaidParser.parse("graph\n$chain").direction)
    }

    // ---- the geometry actually reverses -------------------------------------

    @Test
    fun `RL runs the horizontal axis backwards`() {
        val lr = sceneFor("graph LR\n$chain")
        val rl = sceneFor("graph RL\n$chain")

        assertTrue(lr.nodes.getValue("A").rect.left < lr.nodes.getValue("C").rect.left, "LR is not left-to-right")
        assertTrue(
            rl.nodes.getValue("A").rect.left > rl.nodes.getValue("C").rect.left,
            "RL laid out left-to-right — the direction was parsed and then ignored"
        )
    }

    @Test
    fun `BT runs the vertical axis backwards`() {
        val td = sceneFor("graph TD\n$chain")
        val bt = sceneFor("graph BT\n$chain")

        assertTrue(td.nodes.getValue("A").rect.top < td.nodes.getValue("C").rect.top, "TD is not top-down")
        assertTrue(
            bt.nodes.getValue("A").rect.top > bt.nodes.getValue("C").rect.top,
            "BT laid out top-down — the right axis, drawn the wrong way up"
        )
    }

    @Test
    fun `reversing mirrors the scene rather than reshaping it`() {
        val lr = sceneFor("graph LR\n$chain")
        val rl = sceneFor("graph RL\n$chain")

        assertEquals(lr.width, rl.width, "mirroring changed the width")
        assertEquals(lr.height, rl.height, "mirroring changed the height")

        // Same nodes, same sizes — only their order along the axis differs.
        for (id in listOf("A", "B", "C")) {
            assertEquals(
                lr.nodes.getValue(id).rect.width, rl.nodes.getValue(id).rect.width,
                "node $id changed width under reversal"
            )
        }
    }

    @Test
    fun `reversing the cross axis leaves it alone`() {
        // RL flips horizontally; vertical placement must be untouched, or a mirrored diagram
        // silently reorders branches that were deliberately stacked.
        val lr = sceneFor("graph LR\nA --> B\nA --> C")
        val rl = sceneFor("graph RL\nA --> B\nA --> C")

        assertEquals(
            lr.nodes.getValue("B").rect.top < lr.nodes.getValue("C").rect.top,
            rl.nodes.getValue("B").rect.top < rl.nodes.getValue("C").rect.top,
            "the branch order on the cross axis flipped as well"
        )
    }
}
