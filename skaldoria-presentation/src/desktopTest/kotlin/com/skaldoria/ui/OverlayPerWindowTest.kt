package com.skaldoria.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.skaldoria.PresentationStateTestBase
import com.skaldoria.RenderEnvironment
import com.skaldoria.core.presentation.DeckSurface
import com.skaldoria.core.presentation.SurfaceOverlay
import com.skaldoria.ui.components.SLIDE_GRID_OVERVIEW_TAG
import com.skaldoria.ui.screens.FullscreenDeck
import com.skaldoria.ui.screens.PresenterView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Overlays open in the window they were asked for.
 *
 * The deck, the speaker console and the studio all render one `PresentationState`. The grid
 * overview and the command palette were plain booleans on it, so opening the grid on the
 * console drew it on the projector too — every upcoming slide, in front of the room.
 */
@OptIn(ExperimentalTestApi::class, InternalComposeUiApi::class)
class OverlayPerWindowTest : PresentationStateTestBase() {

    private val talk = "# One\n---\n# Two\n---\n# Three"

    @Test
    fun `an overlay toggles on its own window and moves when another asks for it`() {
        val overlay = SurfaceOverlay()

        overlay.toggle(DeckSurface.Console)
        assertTrue(overlay.isOpenOn(DeckSurface.Console))
        assertFalse(overlay.isOpenOn(DeckSurface.Deck))

        overlay.toggle(DeckSurface.Deck)
        assertTrue(overlay.isOpenOn(DeckSurface.Deck), "asking from the deck moves it there")
        assertFalse(overlay.isOpenOn(DeckSurface.Console))

        overlay.toggle(DeckSurface.Deck)
        assertNull(overlay.openOn)
    }

    @Test
    fun `G opens the grid only in the window that received it`() {
        val state = presentationState(initialMarkdown = talk)
        val g = KeyEvent(Key.G, KeyEventType.KeyDown)

        DeckKeyHandler.handle(g, state, DeckSurface.Console) {}
        assertEquals(DeckSurface.Console, state.gridOverview.openOn)

        DeckKeyHandler.handle(KeyEvent(Key.P, KeyEventType.KeyDown, isCtrlPressed = true), state, DeckSurface.Console) {}
        assertEquals(DeckSurface.Console, state.commandPalette.openOn)
    }

    @Test
    fun `the grid opened on the speaker console never appears on the projected deck`() {
        // The projected deck reaches the window system (its pointer); a headless Linux gate has none.
        RenderEnvironment.requireDisplay()
        runComposeUiTest {
            val state = presentationState(initialMarkdown = talk)
            setContent {
                Row {
                    FullscreenDeck(state, Modifier.width(400.dp).fillMaxHeight())
                    PresenterView(state = state, onClose = {}, modifier = Modifier.width(1400.dp).fillMaxHeight())
                }
            }

            onNodeWithText("Grid Overview (G)").performClick()

            assertTrue(state.gridOverview.isOpenOn(DeckSurface.Console))
            onAllNodesWithTag(SLIDE_GRID_OVERVIEW_TAG).assertCountEquals(1)
        }
    }

    @Test
    fun `the deck draws only its own overlays`() {
        // The projected deck reaches the window system (its pointer); a headless Linux gate has none.
        RenderEnvironment.requireDisplay()
        runComposeUiTest {
            val state = presentationState(initialMarkdown = talk)
            setContent { FullscreenDeck(state) }
            val paletteHint = "Jump to slide by title, code, or keyword..."

            state.gridOverview.open(DeckSurface.Console)
            state.commandPalette.open(DeckSurface.Console)
            waitForIdle()
            onAllNodesWithTag(SLIDE_GRID_OVERVIEW_TAG).assertCountEquals(0)
            onAllNodesWithText(paletteHint).assertCountEquals(0)

            state.gridOverview.open(DeckSurface.Deck)
            waitForIdle()
            onAllNodesWithTag(SLIDE_GRID_OVERVIEW_TAG).assertCountEquals(1)
        }
    }

    @Test
    fun `the speaker console draws its own command palette`() = runComposeUiTest {
        val state = presentationState(initialMarkdown = talk)
        setContent { PresenterView(state = state, onClose = {}) }

        state.commandPalette.open(DeckSurface.Console)
        waitForIdle()

        onAllNodesWithText("Jump to slide by title, code, or keyword...").assertCountEquals(1)
    }
}
