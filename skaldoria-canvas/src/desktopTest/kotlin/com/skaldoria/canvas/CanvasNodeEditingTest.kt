package com.skaldoria.canvas

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import com.skaldoria.canvas.model.CanvasDocument
import com.skaldoria.canvas.model.CanvasNode
import com.skaldoria.canvas.model.CanvasViewport
import com.skaldoria.canvas.model.NodeShape
import com.skaldoria.canvas.state.CanvasState
import com.skaldoria.canvas.ui.CanvasNodeCard
import com.skaldoria.canvas.ui.CanvasTestTags
import com.skaldoria.theme.BuiltinThemes
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Writing in a shaped node, and what a node's markdown turns into.
 *
 * A circle, diamond or cylinder confined its source to the rectangle inscribed in the outline —
 * a quarter of a diamond's area — and cut it off after a line or two. Lists lost their numbers
 * and tables vanished, because the card renderer ended in `else -> {}`.
 */
@OptIn(ExperimentalTestApi::class)
class CanvasNodeEditingTest {

    private val theme = BuiltinThemes.SkaldoriaDark
    private val source = "## Payment service\n\nValidates the card\n- retries 3x\n- writes ledger\n- emits events"

    private fun editing(shape: NodeShape, zoom: Float): Pair<CanvasState, CanvasNode> {
        val node = CanvasNode(x = 0f, y = 0f, width = 300f, height = 200f, markdown = source, shape = shape)
        val state = CanvasState(CanvasDocument(nodes = listOf(node), viewport = CanvasViewport(zoom = zoom)))
        state.editingNodeId = node.id
        return state to node
    }

    private fun near(expected: Float, actual: Dp, density: Density) {
        val px = with(density) { actual.toPx() }
        assertTrue(abs(px - expected) <= 1.5f, "expected $expected px, was $px px")
    }

    @Test
    fun `a shaped node edits in a box the size of the whole node, at any zoom`() {
        for (zoom in listOf(0.5f, 1f, 1.5f)) for (shape in listOf(NodeShape.Circle, NodeShape.Diamond, NodeShape.Cylinder)) {
            runComposeUiTest {
                val (state, node) = editing(shape, zoom)
                setContent { Box(Modifier.fillMaxSize()) { CanvasNodeCard(node = node, state = state, theme = theme) } }

                val bounds = onNodeWithTag(CanvasTestTags.nodeEditor(node.id)).getBoundsInRoot()
                near(node.width * zoom, bounds.right - bounds.left, density)
                near(node.height * zoom, bounds.bottom - bounds.top, density)
            }
        }
    }

    @Test
    fun `a card keeps editing in place`() = runComposeUiTest {
        val (state, node) = editing(NodeShape.Card, 1f)
        setContent { Box(Modifier.fillMaxSize()) { CanvasNodeCard(node = node, state = state, theme = theme) } }

        onAllNodesWithTag(CanvasTestTags.nodeEditor(node.id)).assertCountEquals(0)
    }

    @Test
    fun `numbered lists keep their numbers and tables are drawn`() = runComposeUiTest {
        val node = CanvasNode(
            x = 0f, y = 0f, width = 400f, height = 320f,
            markdown = "## Plan\n\n1. Draft\n2. Review\n\n| Owner | Due |\n|---|---|\n| Ada | Friday |"
        )
        val state = CanvasState(CanvasDocument(nodes = listOf(node)))
        setContent { Box(Modifier.fillMaxSize()) { CanvasNodeCard(node = node, state = state, theme = theme) } }

        onNodeWithText("2. ").assertExists()
        onNodeWithText("Owner").assertExists()
        onNodeWithText("Friday").assertExists()
    }
}
