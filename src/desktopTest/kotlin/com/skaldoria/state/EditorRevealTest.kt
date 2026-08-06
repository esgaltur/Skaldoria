package com.skaldoria.state

import androidx.compose.ui.text.TextRange
import com.skaldoria.core.document.SlideSourceLocator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * EDT-2 / EDT-4 / EDT-5 guards.
 *
 * `EditorFindAndReplaceTest` asserts that `currentMatchIndex` advances, and it passed for the
 * entire time the find buttons appeared to do nothing — the index was genuinely working and the
 * viewport never moved. Nothing here asserts an index. Every test asks whether the editor was
 * *told to go somewhere*, which is the thing the user experiences.
 */
class EditorRevealTest {

    /** A deck long enough that a match near the end is off screen. */
    private fun longDeck(): String = buildString {
        for (slide in 1..12) {
            appendLine("# Slide $slide")
            appendLine()
            repeat(6) { appendLine("- Point $it on slide $slide") }
            appendLine()
            if (slide < 12) {
                appendLine("---")
                appendLine()
            }
        }
        appendLine("The needle is here, at the very end.")
    }

    // ---- EDT-4: match navigation reveals the match ----

    @Test
    fun `findNext reveals the match it selected`() {
        val state = PresentationState()
        state.updateMarkdown(longDeck())
        state.findQuery = "Point 3"

        val before = state.editorRevealToken
        state.findNext()

        assertNotEquals(before, state.editorRevealToken, "findNext published no reveal request")

        val match = state.findMatches[state.currentMatchIndex]
        assertEquals(
            TextRange(match.first, match.last + 1),
            state.editorRevealTargetWithin(state.currentEditorText.length),
            "the reveal must target the match that is now active"
        )
    }

    @Test
    fun `findPrevious reveals the match it selected`() {
        val state = PresentationState()
        state.updateMarkdown(longDeck())
        state.findQuery = "Point 3"

        val before = state.editorRevealToken
        state.findPrevious()

        assertNotEquals(before, state.editorRevealToken)
        val match = state.findMatches[state.currentMatchIndex]
        assertEquals(match.first, state.editorRevealTargetWithin(state.currentEditorText.length)?.min)
    }

    @Test
    fun `a second reveal of the same match still fires`() {
        // A one-match document: the target does not change, so a target-only signal would be
        // indistinguishable from no signal and the second press would do nothing.
        val state = PresentationState()
        state.updateMarkdown(longDeck())
        state.findQuery = "needle"
        assertEquals(1, state.findMatches.size)

        state.findNext()
        val first = state.editorRevealToken
        state.findNext()

        assertNotEquals(first, state.editorRevealToken)
    }

    @Test
    fun `typing a query jumps to the first match at or after the caret`() {
        val state = PresentationState()
        state.updateMarkdown(longDeck())

        val caret = state.currentEditorText.indexOf("# Slide 9")
        state.onEditorSelectionChanged(TextRange(caret))
        state.updateFindQuery("Point 3")

        val match = state.findMatches[state.currentMatchIndex]
        assertTrue(
            match.first >= caret,
            "search restarted at match 0 instead of resuming from the caret"
        )
    }

    @Test
    fun `finding a match selects the slide the match is on`() {
        // The editor and the deck must not disagree about where the user is: jumping the
        // source pane to slide 12 while the preview and filmstrip stay on slide 1 is the same
        // "half a feature" shape as revealing a match you cannot navigate from.
        val state = PresentationState()
        state.updateMarkdown(longDeck())
        state.findQuery = "Point 2 on slide 9"
        state.findNext()

        assertEquals(8, state.currentSlideIndex)
    }

    // ---- EDT-2: the loop guard ----

    @Test
    fun `moving the caret publishes no reveal`() {
        val state = PresentationState()
        state.updateMarkdown(longDeck())

        val before = state.editorRevealToken
        state.onEditorSelectionChanged(TextRange(state.currentEditorText.indexOf("# Slide 7")))

        assertEquals(
            before,
            state.editorRevealToken,
            "caret movement published a reveal — forward and reverse sync will fight each other"
        )
    }

    @Test
    fun `explicit navigation does publish a reveal`() {
        val state = PresentationState()
        state.updateMarkdown(longDeck())

        val afterGoTo = state.editorRevealToken
        state.goToSlide(7)
        assertNotEquals(afterGoTo, state.editorRevealToken, "goToSlide published no reveal")

        val afterNext = state.editorRevealToken
        state.nextSlide()
        assertNotEquals(afterNext, state.editorRevealToken, "nextSlide published no reveal")

        val afterPrev = state.editorRevealToken
        state.previousSlide()
        assertNotEquals(afterPrev, state.editorRevealToken, "previousSlide published no reveal")
    }

    @Test
    fun `navigation that goes nowhere publishes nothing`() {
        val state = PresentationState()
        state.updateMarkdown(longDeck())

        // Already on the first slide.
        val before = state.editorRevealToken
        state.previousSlide()
        state.goToSlide(-1)
        state.goToSlide(9_999)

        assertEquals(before, state.editorRevealToken)
    }

    // ---- AUT-02: the reveal lands on the slide that was selected ----

    @Test
    fun `selecting a slide reveals that slide's source`() {
        val state = PresentationState()
        val markdown = longDeck()
        state.updateMarkdown(markdown)

        state.goToSlide(8)

        val target = state.editorRevealTargetWithin(markdown.length)!!
        assertTrue(
            markdown.substring(target.min).startsWith("# Slide 9"),
            "expected slide 9's source, got '${markdown.substring(target.min).take(24)}'"
        )
    }

    @Test
    fun `every slide in the deck can be revealed and each lands on its own source`() {
        val state = PresentationState()
        val markdown = longDeck()
        state.updateMarkdown(markdown)

        for (index in state.slides.indices) {
            state.goToSlide(index)
            val target = state.editorRevealTargetWithin(markdown.length)!!
            assertEquals(
                index,
                SlideSourceLocator.slideIndexAtOffset(markdown, state.slides, target.min),
                "revealing slide $index landed inside another slide"
            )
        }
    }

    // ---- Phase 5: the caret selects the slide ----

    @Test
    fun `moving the caret into a slide selects it`() {
        val state = PresentationState()
        val markdown = longDeck()
        state.updateMarkdown(markdown)

        state.onEditorSelectionChanged(TextRange(markdown.indexOf("Point 4 on slide 6")))

        assertEquals(5, state.currentSlideIndex, "the preview did not follow the caret")
    }

    @Test
    fun `follow-caret can be turned off`() {
        val state = PresentationState()
        val markdown = longDeck()
        state.updateMarkdown(markdown)
        state.isFollowCaretEnabled = false

        state.onEditorSelectionChanged(TextRange(markdown.indexOf("Point 4 on slide 6")))

        assertEquals(0, state.currentSlideIndex)
    }

    @Test
    fun `alternating navigation and caret movement does not oscillate`() {
        val state = PresentationState()
        val markdown = longDeck()
        state.updateMarkdown(markdown)

        state.goToSlide(6)
        val tokenAfterJump = state.editorRevealToken

        // The composable applies the revealed selection; the field reports it back. That
        // round trip must settle rather than publish a further reveal.
        state.onEditorSelectionChanged(state.editorSelection)

        assertEquals(6, state.currentSlideIndex, "the reveal round trip moved the slide")
        assertEquals(tokenAfterJump, state.editorRevealToken, "the reveal round trip published another reveal")
    }

    // ---- EDT-1: the caret-jump regression ----

    @Test
    fun `typing in the middle of a long deck leaves the caret where it was`() {
        // The failure mode ADR-004 calls the single most likely way to break the editor: hold
        // TextFieldValue as a second source of truth and the caret snaps to the end of the
        // document on every keystroke.
        //
        // This asserts the state contract the composable is built on — text derived, selection
        // stored — not the field itself. A field that re-seeds a remembered value would still
        // pass here, so the manual script's "type in the middle of a long deck" step is not
        // replaced by this test; it is the floor beneath it.
        val state = PresentationState()
        val markdown = longDeck()
        state.updateMarkdown(markdown)

        val caret = markdown.indexOf("Point 2 on slide 5") + "Point 2".length
        state.onEditorSelectionChanged(TextRange(caret))

        val typed = markdown.substring(0, caret) + "!" + markdown.substring(caret)
        state.updateEditorContent(typed)
        state.onEditorSelectionChanged(TextRange(caret + 1))

        assertEquals(
            caret + 1,
            state.editorSelectionWithin(state.currentEditorText.length).min,
            "the caret did not stay where it was typed"
        )
        assertTrue(
            state.editorSelectionWithin(state.currentEditorText.length).min < state.currentEditorText.length,
            "the caret jumped to the end of the document"
        )
    }

    @Test
    fun `the editor text is never stored, only derived`() {
        // EDT-1 stated as an assertion: nothing the session holds can outvote the deck. After
        // a whole-document replacement the editor shows the new deck, not a remembered buffer.
        val state = PresentationState()
        state.updateMarkdown(longDeck())
        state.onEditorSelectionChanged(TextRange(40))

        state.updateMarkdown("# Replaced\n\nEntirely.")

        assertEquals("# Replaced\n\nEntirely.", state.currentEditorText)
    }

    // ---- EDT-5: clamping ----

    @Test
    fun `a selection past the end of a shorter document is clamped, not thrown`() {
        val state = PresentationState()
        state.updateMarkdown(longDeck())
        state.onEditorSelectionChanged(TextRange(state.currentEditorText.length - 1))

        state.updateMarkdown("# Tiny")

        val clamped = state.editorSelectionWithin(state.currentEditorText.length)
        assertTrue(clamped.max <= state.currentEditorText.length, "selection was not clamped")
    }

    @Test
    fun `a reveal target past the end of a shorter document is clamped`() {
        val state = PresentationState()
        state.updateMarkdown(longDeck())
        state.findQuery = "needle"
        state.findNext()

        state.updateMarkdown("# Tiny")

        val clamped = state.editorRevealTargetWithin(state.currentEditorText.length)!!
        assertTrue(clamped.max <= state.currentEditorText.length)
    }
}
