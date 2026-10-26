package com.skaldoria.canvas

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.skaldoria.canvas.model.CanvasCamera
import com.skaldoria.canvas.model.CanvasDocument
import com.skaldoria.canvas.model.CanvasEdge
import com.skaldoria.canvas.model.CanvasNode
import com.skaldoria.canvas.model.CanvasRect
import com.skaldoria.canvas.model.CanvasViewport
import com.skaldoria.canvas.state.CanvasState
import com.skaldoria.canvas.state.PresentationStop
import com.skaldoria.canvas.ui.CanvasTestTags
import com.skaldoria.theme.BuiltinThemes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class CanvasPresentationTest {

    private val savedView = CanvasViewport(panX = 37f, panY = -12f, zoom = 0.8f)

    /** `intro → detail`, plus a `side` card with no arrows, laid out so space and arrows disagree. */
    private fun talk() = CanvasState(
        CanvasDocument(
            nodes = listOf(
                CanvasNode(id = "detail", x = 0f, y = 0f, width = 200f, height = 150f, markdown = "# Detail"),
                CanvasNode(id = "intro", x = 600f, y = 400f, width = 200f, height = 150f, markdown = "# Intro"),
                CanvasNode(id = "side", x = 300f, y = 900f, width = 200f, height = 150f, markdown = "# Side")
            ),
            edges = listOf(CanvasEdge(id = "e", fromNodeId = "intro", toNodeId = "detail")),
            viewport = savedView
        )
    )

    // --- Camera geometry ---------------------------------------------------------------------

    @Test
    fun `framing centres a card and fits it inside the screen with room to spare`() {
        val card = CanvasRect(100f, 100f, 420f, 320f)
        val view = CanvasCamera.frame(card, screenWidth = 1280f, screenHeight = 800f)

        val topLeft = view.canvasToScreen(card.topLeft)
        val bottomRight = view.canvasToScreen(card.bottomRight)
        assertEquals(640f, (topLeft.x + bottomRight.x) / 2f, 0.5f)
        assertEquals(400f, (topLeft.y + bottomRight.y) / 2f, 0.5f)
        assertTrue(topLeft.x >= 0f && topLeft.y >= 0f && bottomRight.x <= 1280f && bottomRight.y <= 800f)
        assertEquals(800f * CanvasCamera.FILL, bottomRight.y - topLeft.y, 0.5f)
    }

    @Test
    fun `a flight starts and ends exactly on its endpoints`() {
        val from = CanvasViewport(0f, 0f, 1f)
        val to = CanvasViewport(-2000f, -900f, 2f)

        assertEquals(from, CanvasCamera.fly(from, to, 0f, 1280f, 800f))
        assertEquals(to, CanvasCamera.fly(from, to, 1f, 1280f, 800f))
    }

    @Test
    fun `a long flight pulls back mid-way and a short one barely does`() {
        val here = CanvasCamera.frame(CanvasRect(0f, 0f, 320f, 220f), 1280f, 800f)
        val far = CanvasCamera.frame(CanvasRect(6000f, 0f, 6320f, 220f), 1280f, 800f)
        val near = CanvasCamera.frame(CanvasRect(40f, 0f, 360f, 220f), 1280f, 800f)

        assertTrue(CanvasCamera.fly(here, far, 0.5f, 1280f, 800f).zoom < here.zoom * 0.6f)
        assertTrue(CanvasCamera.fly(here, near, 0.5f, 1280f, 800f).zoom > here.zoom * 0.95f)
    }

    // --- Tour state --------------------------------------------------------------------------

    @Test
    fun `the tour opens on the whole board then follows the arrows like the deck export`() {
        val state = talk()
        state.startPresentation()

        assertEquals(
            listOf(
                PresentationStop.Overview,
                PresentationStop.Card("intro"),
                PresentationStop.Card("detail"),
                PresentationStop.Card("side")
            ),
            state.presentation?.stops
        )
    }

    @Test
    fun `stepping stops at both ends`() {
        val tour = talk().apply { startPresentation() }.presentation!!

        tour.previous()
        assertEquals(0, tour.index)
        repeat(10) { tour.next() }
        assertTrue(tour.isLast)
        tour.first()
        assertTrue(tour.isFirst)
    }

    @Test
    fun `presenting never touches the saved file`() {
        val state = talk()
        state.startPresentation()
        val tour = state.presentation!!
        tour.camera = CanvasViewport(999f, 999f, 2.5f)
        tour.next()

        assertEquals(tour.camera, state.viewport, "the screen follows the camera while presenting")
        assertEquals(savedView, state.document.viewport, "the camera leaked into the document")
        assertFalse(state.isDirty)

        state.stopPresentation()
        assertEquals(savedView, state.viewport)
        assertFalse(state.isDirty)
    }

    @Test
    fun `an empty board has nothing to present`() {
        val state = CanvasState(CanvasDocument())
        state.startPresentation()
        assertNull(state.presentation)
    }

    @Test
    fun `starting clears selection and any edit in progress`() {
        val state = talk().apply {
            selectedNodeIds = setOf("intro")
            editingNodeId = "intro"
        }
        state.startPresentation()

        assertTrue(state.selectedNodeIds.isEmpty())
        assertNull(state.editingNodeId)
    }

    @Test
    fun `clicker keys move the tour and nothing reaches the board`() {
        val state = talk().apply { startPresentation() }
        val tour = state.presentation!!

        tour.handleKey(Key.PageDown, state::stopPresentation)
        tour.handleKey(Key.DirectionRight, state::stopPresentation)
        assertEquals(2, tour.index)
        tour.handleKey(Key.PageUp, state::stopPresentation)
        assertEquals(1, tour.index)

        assertTrue(tour.handleKey(Key.Delete, state::stopPresentation), "Delete must be swallowed")
        assertEquals(3, state.nodes.size)

        tour.handleKey(Key.Escape, state::stopPresentation)
        assertNull(state.presentation)
    }

    // --- On screen ---------------------------------------------------------------------------

    @Test
    fun `Present hides the editing chrome, clicks advance and the camera lands on the card`() = runComposeUiTest {
        val state = talk()
        setContent { CanvasWindowContent(state, BuiltinThemes.SkaldoriaDark, onThemeSelected = {}) }

        onNodeWithTag(CanvasTestTags.PresentButton).performClick()
        waitForIdle()
        onNodeWithTag(CanvasTestTags.PresentationShield).assertIsDisplayed()
        onNodeWithTag(CanvasTestTags.PresentButton).assertDoesNotExist()

        onNodeWithTag(CanvasTestTags.PresentationShield).performClick()
        mainClock.advanceTimeBy(2_000)
        waitForIdle()

        val tour = state.presentation!!
        assertEquals(PresentationStop.Card("intro"), tour.current)
        val size = onNodeWithTag(CanvasTestTags.Workspace).fetchSemanticsNode().size
        val intro = state.nodes.first { it.id == "intro" }
        val centre = state.viewport.canvasToScreen(intro.center)
        assertEquals(size.width / 2f, centre.x, 1f)
        assertEquals(size.height / 2f, centre.y, 1f)
    }

    @Test
    fun `dragging during a presentation moves nothing`() = runComposeUiTest {
        val state = talk()
        setContent { CanvasWindowContent(state, BuiltinThemes.SkaldoriaDark, onThemeSelected = {}) }
        state.startPresentation()
        mainClock.advanceTimeBy(2_000)
        waitForIdle()
        val before = state.document

        onNodeWithTag(CanvasTestTags.PresentationShield).performMouseInput {
            moveTo(center)
            press()
            moveBy(Offset(150f, 90f))
            release()
        }
        waitForIdle()

        assertEquals(before, state.document)
        assertFalse(state.isDirty)
    }
}
