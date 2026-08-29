package com.skaldoria.canvas

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Offset
import com.skaldoria.canvas.model.CanvasPoint
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.skaldoria.canvas.model.CanvasDocument
import com.skaldoria.canvas.model.CanvasNode
import com.skaldoria.canvas.model.CanvasViewport
import com.skaldoria.canvas.model.EdgePort
import com.skaldoria.canvas.state.CanvasState
import com.skaldoria.canvas.state.CanvasTool
import com.skaldoria.canvas.ui.CanvasTestTags
import com.skaldoria.theme.BuiltinThemes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Interaction tests that drive the composed desktop canvas through pointer input. */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class CanvasWorkspaceGestureTest {

    @Test
    fun starterCardsAreActuallyComposed() = runComposeUiTest {
        val state = CanvasState()
        setCanvasContent(state)

        state.nodes.forEach { node ->
            onNodeWithTag(CanvasTestTags.node(node.id)).assertIsDisplayed()
        }
    }

    @Test
    fun panToolDragOnEmptyCanvasMovesViewport() = runComposeUiTest {
        val state = CanvasState(CanvasDocument()).apply { selectTool(CanvasTool.Pan) }
        setCanvasContent(state)

        onNodeWithTag(CanvasTestTags.Workspace).performMouseInput {
            moveTo(Offset(400f, 500f))
            press()
            moveBy(Offset(160f, 100f))
            release()
        }
        waitForIdle()

        assertEquals(160f, state.viewport.panX, 1f)
        assertEquals(100f, state.viewport.panY, 1f)
    }

    @Test
    fun selectToolDragMovesCardInCanvasCoordinates() = runComposeUiTest {
        val state = twoNodeState(CanvasTool.Select)
        val before = state.nodes.first { it.id == "a" }
        setCanvasContent(state)

        onNodeWithTag(CanvasTestTags.node("a")).performMouseInput {
            moveTo(center)
            press()
            moveBy(Offset(90f, 55f))
            release()
        }
        waitForIdle()

        val moved = state.nodes.first { it.id == "a" }
        assertEquals(before.x + 90f, moved.x, 1f)
        assertEquals(before.y + 55f, moved.y, 1f)
        assertEquals(setOf("a"), state.selectedNodeIds)
    }

    @Test
    fun panToolDragOverCardMovesViewportWithoutMovingCard() = runComposeUiTest {
        val state = twoNodeState(CanvasTool.Pan)
        val before = state.nodes.first { it.id == "a" }
        setCanvasContent(state)

        onNodeWithTag(CanvasTestTags.node("a")).performMouseInput {
            moveTo(center)
            press()
            moveBy(Offset(75f, 40f))
            release()
        }
        waitForIdle()

        val after = state.nodes.first { it.id == "a" }
        assertEquals(before.x, after.x)
        assertEquals(before.y, after.y)
        assertEquals(75f, state.viewport.panX, 1f)
        assertEquals(40f, state.viewport.panY, 1f)
    }

    @Test
    fun connectToolDragBetweenCardsCreatesBezierEdge() = runComposeUiTest {
        val state = twoNodeState(CanvasTool.Connect)
        setCanvasContent(state)

        onNodeWithTag(CanvasTestTags.node("a")).performMouseInput {
            moveTo(center)
            press()
            moveBy(Offset(340f, 0f))
            release()
        }
        waitForIdle()

        val edge = state.edges.single()
        assertEquals("a", edge.fromNodeId)
        assertEquals("b", edge.toNodeId)
        assertTrue(state.findEdgeAt(CanvasPoint(350f, 250f), threshold = 24f) != null)
    }

    @Test
    fun connectionPortDragCreatesEdgeWithExplicitSourcePort() = runComposeUiTest {
        val state = twoNodeState(CanvasTool.Select)
        setCanvasContent(state)

        onNodeWithTag(CanvasTestTags.port("a", EdgePort.Right.name)).performMouseInput {
            moveTo(center)
            press()
            moveBy(Offset(250f, 0f))
            release()
        }
        waitForIdle()

        val edge = state.edges.single()
        assertEquals("a", edge.fromNodeId)
        assertEquals("b", edge.toNodeId)
        assertEquals(EdgePort.Right, edge.fromPort)
    }

    @Test
    fun doubleClickOnEmptyCanvasCreatesCardAtPointer() = runComposeUiTest {
        val state = CanvasState(CanvasDocument())
        setCanvasContent(state)

        onNodeWithTag(CanvasTestTags.Workspace).performMouseInput {
            moveTo(Offset(700f, 500f))
            press()
            release()
            advanceEventTime(100L)
            press()
            release()
        }
        waitForIdle()

        assertEquals(1, state.nodes.size)
        assertEquals(700f, state.nodes.single().x, 1f)
        assertEquals(500f, state.nodes.single().y, 1f)
    }

    private fun twoNodeState(tool: CanvasTool): CanvasState = CanvasState(
        CanvasDocument(
            nodes = listOf(
                CanvasNode(id = "a", x = 80f, y = 180f, width = 180f, height = 140f, markdown = "# A"),
                CanvasNode(id = "b", x = 420f, y = 180f, width = 180f, height = 140f, markdown = "# B")
            ),
            viewport = CanvasViewport()
        )
    ).apply { selectTool(tool) }

    private fun androidx.compose.ui.test.ComposeUiTest.setCanvasContent(state: CanvasState) {
        setContent {
            CanvasWindowContent(
                state = state,
                theme = BuiltinThemes.SkaldoriaDark,
                onThemeSelected = {}
            )
        }
        waitForIdle()
        onNodeWithTag(CanvasTestTags.Workspace).assertIsDisplayed()
        assertTrue(onNodeWithTag(CanvasTestTags.Workspace).fetchSemanticsNode().size.width > 0)
    }
}
