package com.skaldoria.ui

import androidx.compose.ui.input.key.KeyEvent
import com.skaldoria.core.command.AppCommands
import com.skaldoria.core.command.CommandScope
import com.skaldoria.state.PresentationState

/**
 * What a `DECK`-scope key does, in one place.
 *
 * **KEY-1.** This existed twice by omission rather than by duplication: the fullscreen deck
 * had the whole `when` block and the speaker console had *nothing at all*. Since the console's
 * window is `alwaysOnTop`, it is the window holding focus for most of a talk — so the entire
 * deck keyboard surface, and any presenter clicker pointed at it, was dead exactly where a
 * speaker most needs it. `H` did nothing, and neither did the arrows.
 *
 * F-19 put the *bindings* in one table; this puts the *dispatch* in one place too, so a window
 * that hosts the deck cannot silently support a different subset of it.
 *
 * Escape is the caller's, because unwinding means something different per window: the deck
 * closes its overlays and then leaves fullscreen, and the console just closes.
 */
object DeckKeyHandler {

    /**
     * Runs whatever [event] resolves to in [CommandScope.DECK].
     *
     * @param onEscape how this window unwinds. Not called for anything else.
     * @return true when the event was consumed, for `onKeyEvent`'s contract.
     */
    fun handle(event: KeyEvent, state: PresentationState, onEscape: () -> Unit): Boolean {
        when (KeyBindings.resolve(event, CommandScope.DECK)) {
            AppCommands.NEXT_SLIDE -> state.next()
            AppCommands.PREVIOUS_SLIDE -> state.prev()
            AppCommands.FIRST_SLIDE -> state.goToSlide(0)
            AppCommands.LAST_SLIDE -> state.goToSlide((state.slides.size - 1).coerceAtLeast(0))
            AppCommands.CYCLE_THEME -> state.cycleTheme()
            AppCommands.TOGGLE_HUD -> state.cycleHudVisibility()
            AppCommands.BLACKOUT -> state.toggleBlackout()
            AppCommands.WHITEOUT -> state.toggleWhiteout()
            AppCommands.GRID_OVERVIEW -> state.toggleGridOverview()
            AppCommands.LASER_POINTER -> state.toggleLaserPointer()
            AppCommands.PEN_DRAWING -> state.togglePenDrawing()
            AppCommands.CLEAR_ANNOTATIONS -> state.clearAnnotations()
            AppCommands.UNDO_STROKE -> state.undoStroke()
            AppCommands.COMMAND_PALETTE -> state.openCommandPalette()
            AppCommands.EXIT_FULLSCREEN -> onEscape()
            else -> return false
        }
        return true
    }
}
