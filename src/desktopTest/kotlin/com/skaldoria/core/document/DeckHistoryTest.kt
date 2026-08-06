package com.skaldoria.core.document

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * AUT-04: undo/redo for structural slide edits.
 *
 * The only undo in the application was `undoStroke()` for annotation strokes. Deleting a slide
 * is a single click on the filmstrip — it rewrites the deck markdown through `SlideDocument`
 * and there was no way back. Move, duplicate and insert were equally final.
 *
 * Generic over the snapshot type because what constitutes "the deck" differs by mode: a single
 * markdown buffer, or the per-file contents of a project.
 */
class DeckHistoryTest {

    @Test
    fun `a fresh history offers nothing to undo or redo`() {
        val history = DeckHistory<String>()
        assertFalse(history.canUndo)
        assertFalse(history.canRedo)
        assertNull(history.undo(current = "a"))
        assertNull(history.redo(current = "a"))
    }

    @Test
    fun `undo returns the recorded state`() {
        val history = DeckHistory<String>()
        history.record("before")
        assertTrue(history.canUndo)
        assertEquals("before", history.undo(current = "after"))
    }

    @Test
    fun `redo returns the state undone from`() {
        val history = DeckHistory<String>()
        history.record("before")
        history.undo(current = "after")

        assertTrue(history.canRedo)
        assertEquals("after", history.redo(current = "before"))
    }

    @Test
    fun `undo walks back through several edits in order`() {
        val history = DeckHistory<String>()
        history.record("v1")
        history.record("v2")
        history.record("v3")

        assertEquals("v3", history.undo(current = "v4"))
        assertEquals("v2", history.undo(current = "v3"))
        assertEquals("v1", history.undo(current = "v2"))
        assertNull(history.undo(current = "v1"))
    }

    @Test
    fun `a new edit invalidates the redo branch`() {
        // Standard editor semantics: undoing and then making a different edit abandons the
        // branch you undid. Keeping it would let redo resurrect content the user replaced.
        val history = DeckHistory<String>()
        history.record("v1")
        history.undo(current = "v2")
        assertTrue(history.canRedo)

        history.record("v1-modified")
        assertFalse(history.canRedo, "recording a new edit must drop the redo branch")
        assertNull(history.redo(current = "whatever"))
    }

    @Test
    fun `recording the same state twice does not create a second entry`() {
        // A structural edit that turns out to be a no-op — moveSlide onto its own index,
        // deleting from a one-slide deck — must not cost the user an undo press that
        // appears to do nothing.
        val history = DeckHistory<String>()
        history.record("same")
        history.record("same")

        assertEquals("same", history.undo(current = "current"))
        assertFalse(history.canUndo, "the duplicate must not have been stored")
    }

    @Test
    fun `history is bounded and drops the oldest entry`() {
        val history = DeckHistory<String>(limit = 3)
        history.record("v1")
        history.record("v2")
        history.record("v3")
        history.record("v4")

        assertEquals("v4", history.undo(current = "v5"))
        assertEquals("v3", history.undo(current = "v4"))
        assertEquals("v2", history.undo(current = "v3"))
        assertNull(history.undo(current = "v2"), "v1 must have been dropped, not retained")
    }

    @Test
    fun `bounding never leaves the stack over its limit`() {
        val history = DeckHistory<String>(limit = 5)
        repeat(100) { history.record("v$it") }

        var depth = 0
        var current = "final"
        while (true) {
            val previous = history.undo(current) ?: break
            current = previous
            depth++
        }
        assertEquals(5, depth)
    }

    @Test
    fun `clear empties both directions`() {
        val history = DeckHistory<String>()
        history.record("v1")
        history.undo(current = "v2")

        history.clear()

        assertFalse(history.canUndo, "opening a different deck must not undo into the old one")
        assertFalse(history.canRedo)
    }

    @Test
    fun `works with a structured snapshot type`() {
        // The project mode case: a snapshot is per-file content, not one string.
        data class Snapshot(val files: Map<String, String>, val index: Int)

        val history = DeckHistory<Snapshot>()
        val before = Snapshot(mapOf("01.md" to "# One", "02.md" to "# Two"), index = 1)
        val after = Snapshot(mapOf("01.md" to "# One"), index = 0)

        history.record(before)
        assertEquals(before, history.undo(current = after))
        assertEquals(after, history.redo(current = before))
    }

    @Test
    fun `a limit below one still behaves`() {
        // Defensive: a misconfigured limit must not throw or retain unbounded history.
        val history = DeckHistory<String>(limit = 0)
        history.record("v1")
        assertNull(history.undo(current = "v2"))
    }
}
