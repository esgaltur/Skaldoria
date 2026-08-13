package com.skaldoria.ui

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyCode
import com.skaldoria.core.command.AppCommand
import com.skaldoria.core.command.AppCommands
import com.skaldoria.core.command.CommandScope
import java.awt.event.KeyEvent as AwtKeyEvent
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * AUD-08: an off-the-shelf presenter clicker drives the deck.
 *
 * A USB or Bluetooth clicker is a keyboard as far as the OS is concerned — it pairs as an HID
 * device and emits ordinary key codes, so no code in this application knows or needs to know
 * that a clicker exists. What that makes testable is the only thing that can actually break:
 * whether the codes those devices emit resolve to the commands the speaker expects.
 *
 * The codes below are the PowerPoint/Keynote presenter convention every mainstream clicker
 * ships with — Logitech's R400/R500/Spotlight line, Kensington's, and the generic 2.4 GHz
 * dongles — because that is the compatibility target the hardware is built against.
 *
 * **What this does not prove.** No radio is involved here. This asserts the key-code contract;
 * that a specific device pairs and emits these codes is a hardware claim, and the manual script
 * in the improvement plan is where it gets checked with a physical clicker.
 *
 * Synthesising a [KeyEvent] needs Compose's internal constructor — there is no public way to
 * build one outside a running composition. Confined to this test, where the alternative is
 * asserting nothing about the seam that matters.
 */
@OptIn(InternalComposeUiApi::class)
class PresenterClickerTest {

    private fun resolve(key: Key): AppCommand? =
        KeyBindings.resolve(KeyEvent(key, KeyEventType.KeyDown), CommandScope.DECK)

    /**
     * The hardware claim, stated once: the keys bound below carry the AWT virtual key codes an
     * HID device actually emits.
     *
     * Without this the rest of the class would only restate the registry in Compose's own
     * vocabulary — true, and no evidence at all that a clicker works.
     */
    @Test
    fun `the bound keys are the virtual key codes a clicker emits`() {
        assertEquals(AwtKeyEvent.VK_PAGE_DOWN, Key.PageDown.nativeKeyCode)
        assertEquals(AwtKeyEvent.VK_PAGE_UP, Key.PageUp.nativeKeyCode)
        assertEquals(AwtKeyEvent.VK_PERIOD, Key.Period.nativeKeyCode)
        assertEquals(AwtKeyEvent.VK_COMMA, Key.Comma.nativeKeyCode)
        assertEquals(AwtKeyEvent.VK_ESCAPE, Key.Escape.nativeKeyCode)
    }

    @Test
    fun `the forward button advances the deck`() {
        // Page Down is what every clicker's large forward button sends.
        assertEquals(AppCommands.NEXT_SLIDE, resolve(Key.PageDown))
        // Some models are switchable to arrow-key mode instead.
        assertEquals(AppCommands.NEXT_SLIDE, resolve(Key.DirectionRight))
        assertEquals(AppCommands.NEXT_SLIDE, resolve(Key.DirectionDown))
    }

    @Test
    fun `the back button retreats the deck`() {
        assertEquals(AppCommands.PREVIOUS_SLIDE, resolve(Key.PageUp))
        assertEquals(AppCommands.PREVIOUS_SLIDE, resolve(Key.DirectionLeft))
        assertEquals(AppCommands.PREVIOUS_SLIDE, resolve(Key.DirectionUp))
    }

    @Test
    fun `the blank-screen button blanks the screen`() {
        // The convention is a pair per colour: `B` or `.` for black, `W` or `,` for white.
        // Clickers send the punctuation, not the letter — a letter would type into whatever
        // had focus if the deck were not presenting, so the hardware avoids them.
        assertEquals(AppCommands.BLACKOUT, resolve(Key.Period))
        assertEquals(AppCommands.BLACKOUT, resolve(Key.B))
        assertEquals(AppCommands.WHITEOUT, resolve(Key.Comma))
        assertEquals(AppCommands.WHITEOUT, resolve(Key.W))
    }

    @Test
    fun `the stop button leaves the presentation`() {
        assertEquals(AppCommands.EXIT_FULLSCREEN, resolve(Key.Escape))
    }

    @Test
    fun `a clicker keystroke resolves in the deck scope only`() {
        // The studio window must not advance the deck behind the speaker's back while they are
        // typing; Page Down there belongs to the editor.
        assertEquals(null, KeyBindings.resolve(KeyEvent(Key.PageDown, KeyEventType.KeyDown), CommandScope.STUDIO))
    }
}
