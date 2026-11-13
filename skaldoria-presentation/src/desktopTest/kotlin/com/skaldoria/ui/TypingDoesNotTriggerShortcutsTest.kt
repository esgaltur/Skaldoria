package com.skaldoria.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.withKeyDown
import com.skaldoria.PresentationStateTestBase
import com.skaldoria.state.PresentationState
import com.skaldoria.ui.screens.PresenterView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * End to end: typing in the speaker console's Parking Lot must not drive the deck.
 *
 * Compose Desktop text fields take their text from the typed-character event and leave the
 * letter's KeyDown unconsumed, so it bubbled up to the console's deck handler: typing "what"
 * whited the screen out on the `w`, and a space advanced the slide.
 */
@OptIn(ExperimentalTestApi::class)
class TypingDoesNotTriggerShortcutsTest : PresentationStateTestBase() {

    private val talk = "# One\n---\n# Two\n---\n# Three"

    private fun ComposeUiTest.consoleWithParkingLotFieldFocused(): PresentationState {
        val state = presentationState(initialMarkdown = talk)
        setContent { PresenterView(state = state, onClose = {}) }
        onNodeWithText("Parking Lot").performClick()
        onNode(hasSetTextAction()).performClick()
        waitForIdle()
        return state
    }

    @Test
    fun `letters and space typed in the parking lot stay in the field`() = runComposeUiTest {
        val state = consoleWithParkingLotFieldFocused()

        onNode(hasSetTextAction()).performKeyInput {
            listOf(Key.W, Key.B, Key.L, Key.P, Key.M, Key.H, Key.G, Key.Spacebar).forEach { pressKey(it) }
        }

        assertFalse(state.isWhiteoutActive, "W in the parking lot whited out the screen")
        assertFalse(state.isBlackoutActive, "B in the parking lot blacked out the screen")
        assertFalse(state.isLaserPointerActive, "L in the parking lot toggled the laser")
        assertFalse(state.isPenDrawingActive, "P in the parking lot toggled the pen")
        assertFalse(state.isMagnifierActive, "M in the parking lot toggled the magnifier")
        assertFalse(state.gridOverview.isOpenOn(com.skaldoria.core.presentation.DeckSurface.Console), "G opened the grid")
        assertEquals(0, state.currentSlideIndex, "Space in the parking lot advanced the slide")
    }

    @Test
    fun `modifier shortcuts still reach the console while the field has focus`() = runComposeUiTest {
        val state = consoleWithParkingLotFieldFocused()

        onNode(hasSetTextAction()).performKeyInput {
            withKeyDown(Key.CtrlLeft) { pressKey(Key.P) }
        }

        assertTrue(
            state.commandPalette.isOpenOn(com.skaldoria.core.presentation.DeckSurface.Console),
            "Ctrl+P was swallowed by the text field"
        )
    }

    @Test
    fun `typing in the deck's command palette does not drive the deck`() {
        com.skaldoria.RenderEnvironment.requireDisplay()
        runComposeUiTest {
            val state = presentationState(initialMarkdown = talk)
            setContent { com.skaldoria.ui.screens.FullscreenDeck(state) }
            state.commandPalette.open(com.skaldoria.core.presentation.DeckSurface.Deck)
            waitForIdle()
            onNode(hasSetTextAction()).performClick()

            onNode(hasSetTextAction()).performKeyInput { listOf(Key.W, Key.B, Key.Spacebar).forEach { pressKey(it) } }

            assertFalse(state.isWhiteoutActive || state.isBlackoutActive, "typing in the palette blanked the deck")
            assertEquals(0, state.currentSlideIndex, "Space in the palette advanced the slide")
        }
    }

    @Test
    fun `bare letters still drive the deck when no field has focus`() = runComposeUiTest {
        val state = presentationState(initialMarkdown = talk)
        setContent { PresenterView(state = state, onClose = {}) }
        onNodeWithText("Parking Lot").performClick()
        waitForIdle()

        onNodeWithText("Parking Lot").performKeyInput { pressKey(Key.W) }

        assertTrue(state.isWhiteoutActive, "W no longer reaches the console's deck handler")
    }
}
