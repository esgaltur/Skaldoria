package com.skaldoria.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange

/**
 * Where the caret is in the source editor, and where something has asked it to go.
 *
 * A cohesive collaborator rather than three more properties on `PresentationState` — ADR-004
 * § "Relationship to ADR-003" requires it. The class ADR-003 calls a god object does not get
 * bigger to fix the editor.
 *
 * **EDT-1 — this holds selection, never text.** The deck's markdown stays the system of record
 * and `currentEditorText` stays a derived read. Holding a `TextFieldValue` here as a second
 * authority for content is the specific mistake that makes the caret jump to the end of the
 * document on every keystroke.
 *
 * **EDT-2 — a reveal is published by explicit navigation only.** [requestReveal] is for
 * `goToSlide`, `next`, `prev`, `findNext`, `findPrevious`. Caret movement goes through
 * [moveCaret], which is deliberately unable to publish one. That asymmetry is what stops
 * forward and reverse synchronisation from driving each other in a loop.
 */
class EditorSession {

    /** The editor's current selection. A collapsed range is a plain caret. */
    var selection by mutableStateOf(TextRange.Zero)
        private set

    /**
     * The range the editor has been asked to scroll to, or null before the first request.
     *
     * Read together with [revealToken]: the target alone cannot distinguish two consecutive
     * reveals of the same offset, which is exactly what pressing "next match" on a
     * single-match document does.
     */
    var revealTarget by mutableStateOf<TextRange?>(null)
        private set

    /** Increments on every [requestReveal]. The composable keys its scroll effect on this. */
    var revealToken by mutableStateOf(0L)
        private set

    /** Whether moving the caret selects the slide it sits in. Phase 5; user-visible toggle. */
    var followCaret by mutableStateOf(true)

    /**
     * Increments when something asks the source field to take keyboard focus.
     *
     * EDT-7: a token rather than a boolean for the same reason as [revealToken] — "focus the
     * editor" is an *event*, and two consecutive requests are indistinguishable as a flag.
     */
    var focusToken by mutableStateOf(0L)
        private set

    /**
     * EDT-7: asks the source field for focus, leaving [selection] alone.
     *
     * Closing the find bar is the caller. [requestReveal] has already put the selection on the
     * match, but an unfocused text field draws neither cursor nor selection highlight, so
     * without this the match is revealed to a field the user cannot see a caret in and has to
     * click before typing — losing the very position that was just found.
     */
    fun requestEditorFocus() {
        focusToken++
    }

    /**
     * Records a caret or selection change made *by the user*.
     *
     * EDT-2: publishes no reveal. The field already shows the caret the user just placed;
     * asking it to scroll there would fight them.
     */
    fun moveCaret(range: TextRange) {
        selection = range
    }

    /**
     * Asks the editor to put [range] on screen, and moves the selection there.
     *
     * Selection and reveal move together because every caller wants both: revealing a match
     * you cannot then type over is half a feature.
     */
    fun requestReveal(range: TextRange) {
        selection = range
        revealTarget = range
        revealToken++
    }

    /**
     * EDT-5: [selection] clamped into a document of [length] characters.
     *
     * Called at the composition site rather than only on write, so the value handed to the
     * field is in range no matter which changed first — swapping to a shorter slide file with
     * the caret near the end of the previous one would otherwise construct an out-of-bounds
     * `TextFieldValue`.
     */
    fun selectionWithin(length: Int): TextRange {
        val safeLength = length.coerceAtLeast(0)
        val start = selection.start.coerceIn(0, safeLength)
        val end = selection.end.coerceIn(0, safeLength)
        return TextRange(start, end)
    }

    /** The reveal target clamped the same way, or null. */
    fun revealTargetWithin(length: Int): TextRange? {
        val target = revealTarget ?: return null
        val safeLength = length.coerceAtLeast(0)
        return TextRange(target.start.coerceIn(0, safeLength), target.end.coerceIn(0, safeLength))
    }
}
