package com.skaldoria.state

import com.skaldoria.PresentationStateTestBase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * AUT-04: undo/redo reaches the deck, not just the history stack.
 *
 * `DeckHistoryTest` proves the stack semantics. This proves the wiring — that a deleted slide
 * actually comes back — which is the part a user would call "undo works". Asserting only the
 * stack would repeat the mistake `EditorFindAndReplaceTest` made for find: a green test beside
 * a feature that never reached the screen.
 */
class StructuralUndoTest : PresentationStateTestBase() {

    private fun deckOfThree() = presentationState().apply {
        updateMarkdown("# Alpha\n\n---\n\n# Beta\n\n---\n\n# Gamma")
    }

    @Test
    fun `a deleted slide comes back`() {
        val state = deckOfThree()
        assertEquals(3, state.slides.size)
        val titlesBefore = state.slides.map { it.title }

        state.deleteSlide(1)
        assertEquals(2, state.slides.size)

        state.undo()

        assertEquals(3, state.slides.size, "undo must restore the deleted slide")
        assertEquals(titlesBefore, state.slides.map { it.title }, "and restore it in place")
    }

    @Test
    fun `redo re-applies the deletion`() {
        val state = deckOfThree()
        state.deleteSlide(1)
        state.undo()

        state.redo()

        assertEquals(2, state.slides.size)
        assertFalse(state.slides.any { it.title == "Beta" })
    }

    @Test
    fun `undo restores a moved slide to its original position`() {
        val state = deckOfThree()
        state.moveSlide(0, 2)
        assertEquals("Alpha", state.slides[2].title)

        state.undo()

        assertEquals("Alpha", state.slides[0].title)
    }

    @Test
    fun `undo removes a duplicated slide`() {
        val state = deckOfThree()
        state.duplicateSlide(0)
        assertEquals(4, state.slides.size)

        state.undo()

        assertEquals(3, state.slides.size)
    }

    @Test
    fun `undo removes an inserted slide`() {
        val state = deckOfThree()
        state.insertSlide(0, com.skaldoria.markdown.models.SlideLayoutType.BIG_QUOTE)
        assertEquals(4, state.slides.size)

        state.undo()

        assertEquals(3, state.slides.size)
    }

    @Test
    fun `several edits unwind in order`() {
        val state = deckOfThree()
        state.deleteSlide(2)
        state.deleteSlide(1)
        assertEquals(1, state.slides.size)

        state.undo()
        assertEquals(2, state.slides.size)
        state.undo()
        assertEquals(3, state.slides.size)
        assertEquals(listOf("Alpha", "Beta", "Gamma"), state.slides.map { it.title })
    }

    @Test
    fun `undo on a fresh deck does nothing rather than throwing`() {
        val state = deckOfThree()
        assertFalse(state.canUndo)
        state.undo()
        assertEquals(3, state.slides.size)
    }

    @Test
    fun `canUndo and canRedo track what is actually available`() {
        val state = deckOfThree()
        assertFalse(state.canUndo)
        assertFalse(state.canRedo)

        state.deleteSlide(1)
        assertTrue(state.canUndo)
        assertFalse(state.canRedo)

        state.undo()
        assertTrue(state.canRedo)
    }

    @Test
    fun `the current slide index survives an undo`() {
        val state = deckOfThree()
        state.goToSlide(2)
        state.deleteSlide(2)

        state.undo()

        assertEquals(2, state.currentSlideIndex, "undo should return the speaker to where they were")
        assertEquals(3, state.slides.size)
    }

    @Test
    fun `opening a different deck drops the history`() {
        val state = deckOfThree()
        state.deleteSlide(1)
        assertTrue(state.canUndo)

        state.loadMarkdownFromFile("/tmp/other.md", "# Only Slide")

        assertFalse(state.canUndo, "undoing across decks would restore one deck over another")
        assertEquals(1, state.slides.size)
    }
}
