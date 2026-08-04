package com.skaldoria.core

import com.skaldoria.core.models.FollowUpQuestion
import com.skaldoria.core.parser.MarkdownSlideParser
import com.skaldoria.state.PresentationState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Parking lot — deleting a question must stick.
 *
 * Two coupled defects (see docs/PARKING_LOT_DELETE_ANALYSIS.md):
 *  A. delete only mutated the in-memory list, leaving the `<!-- parking-lot: … -->` comment
 *     in the file, so the item returned on reload;
 *  B. `updateMarkdown` re-added *every* directive whenever the list happened to be empty,
 *     and it runs on every keystroke — so deleting the last item undid itself immediately.
 */
class ParkingLotDeleteTest {

    private val deck = """
        # Talk

        <!-- parking-lot: [ ] Why did latency spike at 14:00? | slide:3 -->
        <!-- parking-lot: [ ] Can we shard the write path? -->

        ## Content

        - A point
    """.trimIndent()

    // ---------------------------------------------------------------
    // Defect A — the delete must reach the markdown
    // ---------------------------------------------------------------

    @Test
    fun `deleting a directive item removes the directive from the markdown`() {
        val state = PresentationState()
        state.updateMarkdown(deck)
        assertEquals(2, state.followUpQuestions.size, "both directives should load")

        val target = state.followUpQuestions.first { it.question.contains("latency") }
        state.deleteFollowUpQuestion(target.id)

        assertFalse(
            state.markdownText.contains("latency spike"),
            "Defect A: the parking-lot comment must be removed from the source"
        )
        assertTrue(
            state.markdownText.contains("shard the write path"),
            "the untouched directive must survive"
        )
        assertEquals(1, state.followUpQuestions.size)
    }

    /** The delete has to survive a reload, which is where "it came back" was observed. */
    @Test
    fun `a deleted item does not return when the deck is re-parsed`() {
        val state = PresentationState()
        state.updateMarkdown(deck)

        val target = state.followUpQuestions.first { it.question.contains("latency") }
        state.deleteFollowUpQuestion(target.id)

        val reloaded = MarkdownSlideParser.extractFollowUpQuestions(state.markdownText)
        assertEquals(1, reloaded.size)
        assertFalse(reloaded.any { it.question.contains("latency") })
    }

    // ---------------------------------------------------------------
    // Defect B — re-parsing must not resurrect
    // ---------------------------------------------------------------

    /** The exact reported reproduction: delete, then type one character. */
    @Test
    fun `Defect B a deleted item is not resurrected by the next keystroke`() {
        val state = PresentationState()
        state.updateMarkdown(deck)

        val target = state.followUpQuestions.first { it.question.contains("latency") }
        state.deleteFollowUpQuestion(target.id)

        // Simulate typing: updateMarkdown fires on every character in single-file mode.
        state.updateMarkdown(state.markdownText + "\n")

        assertEquals(1, state.followUpQuestions.size, "Defect B: the whole set snapped back")
        assertFalse(state.followUpQuestions.any { it.question.contains("latency") })
    }

    /** Deleting the *last* item emptied the list, which is what triggered the old guard. */
    @Test
    fun `Defect B deleting every item leaves the list empty across re-parses`() {
        val state = PresentationState()
        state.updateMarkdown(deck)

        state.followUpQuestions.map { it.id }.forEach { state.deleteFollowUpQuestion(it) }
        assertEquals(0, state.followUpQuestions.size)

        state.updateMarkdown(state.markdownText + "\n")
        assertEquals(0, state.followUpQuestions.size, "Defect B: an empty list re-hydrated itself")
    }

    // ---------------------------------------------------------------
    // Behaviour that must not regress
    // ---------------------------------------------------------------

    /**
     * The deck markdown is the app's only storage, so a question captured during a talk has
     * to be written there — otherwise it is lost on close, and there is nothing for a later
     * delete or answer to act on.
     */
    @Test
    fun `a manually captured question is persisted to the markdown`() {
        val state = PresentationState()
        state.updateMarkdown(deck)

        state.addFollowUpQuestion("Typed during the talk", slideIndex = 1)
        assertEquals(3, state.followUpQuestions.size)

        assertTrue(
            state.markdownText.contains("Typed during the talk"),
            "a captured question must reach the deck source"
        )

        // And it round-trips: reloading the file alone reproduces it.
        val reloaded = MarkdownSlideParser.extractFollowUpQuestions(state.markdownText)
        assertTrue(reloaded.any { it.question == "Typed during the talk" }, "must survive a reload")

        state.updateMarkdown(state.markdownText + "\n")
        assertEquals(3, state.followUpQuestions.size, "reconciliation must not duplicate it")
    }

    /** A captured question carries its id into the file, so identity survives the round trip. */
    @Test
    fun `captured questions persist their id and can then be deleted permanently`() {
        val state = PresentationState()
        state.updateMarkdown(deck)
        state.addFollowUpQuestion("Ask about the retry budget")

        val captured = state.followUpQuestions.first { it.question == "Ask about the retry budget" }
        assertTrue(
            state.markdownText.contains("id:${captured.id}"),
            "the id must be written into the directive, not kept only in memory"
        )

        val reloaded = MarkdownSlideParser.extractFollowUpQuestions(state.markdownText)
            .first { it.question == "Ask about the retry budget" }
        assertEquals(captured.id, reloaded.id, "the same question must keep the same id across a reload")
        assertTrue(reloaded.hasPersistedId)

        state.deleteFollowUpQuestion(captured.id)
        state.updateMarkdown(state.markdownText + "\n")

        assertFalse(state.markdownText.contains("retry budget"), "delete must remove it from the source")
        assertFalse(state.followUpQuestions.any { it.question.contains("retry budget") })
    }

    /** Editing the wording is an edit, not a delete-plus-create, once the id is persisted. */
    @Test
    fun `a persisted id keeps identity stable when the question text changes`() {
        val state = PresentationState()
        state.updateMarkdown(deck)
        state.addFollowUpQuestion("Original wording")

        val original = state.followUpQuestions.first { it.question == "Original wording" }
        val edited = state.markdownText.replace("Original wording", "Reworded question")
        state.updateMarkdown(edited)

        val match = state.followUpQuestions.first { it.question == "Reworded question" }
        assertEquals(original.id, match.id, "identity should follow the id, not the text")
        assertFalse(
            state.followUpQuestions.any { it.question == "Original wording" },
            "the old wording must not linger as a separate item"
        )
    }

    /** Comments are metadata; they must never be rendered onto a slide. */
    @Test
    fun `parking-lot directives do not render as slide content`() {
        val slides = MarkdownSlideParser.parse(deck)

        val rendered = slides.flatMap { it.elements }.map { it.toString() }
        assertFalse(
            rendered.any { it.contains("parking-lot") },
            "a directive comment leaked into slide content"
        )
    }

    @Test
    fun `newly authored directives appear without wiping session state`() {
        val state = PresentationState()
        state.updateMarkdown(deck)
        state.addFollowUpQuestion("Manual note")

        state.updateMarkdown(state.markdownText + "\n<!-- parking-lot: [ ] Added later? -->")

        assertTrue(state.followUpQuestions.any { it.question == "Added later?" }, "new directive picked up")
        assertTrue(state.followUpQuestions.any { it.question == "Manual note" }, "manual item preserved")
        assertEquals(4, state.followUpQuestions.size)
    }

    @Test
    fun `answering an item is written back to the directive`() {
        val state = PresentationState()
        state.updateMarkdown(deck)

        val target = state.followUpQuestions.first { it.question.contains("shard") }
        state.updateFollowUpAnswer(target.id, "Yes — planned for Q3")
        state.toggleFollowUpAnswered(target.id)

        assertTrue(state.markdownText.contains("Yes — planned for Q3"), "answer persisted to source")
        assertTrue(state.markdownText.contains("[x] Can we shard"), "checkbox persisted to source")

        // And it survives a re-parse with the answer intact.
        state.updateMarkdown(state.markdownText)
        val reloaded = state.followUpQuestions.first { it.question.contains("shard") }
        assertTrue(reloaded.isAnswered)
        assertEquals("Yes — planned for Q3", reloaded.answerText)
    }

    /** Directives are edited in place so one authored beside a slide stays beside it. */
    @Test
    fun `rewriting preserves directive position and surrounding content`() {
        val items = MarkdownSlideParser.extractFollowUpQuestions(deck)
            .filterNot { it.question.contains("latency") }

        val rewritten = MarkdownSlideParser.rewriteFollowUpDirectives(deck, items)
        val lines = rewritten.lines()

        assertEquals("# Talk", lines[0])
        assertTrue(rewritten.contains("## Content"), "unrelated content untouched")
        assertTrue(rewritten.contains("- A point"), "unrelated content untouched")
        assertFalse(rewritten.contains("latency spike"))
        assertTrue(
            lines.indexOfFirst { it.contains("shard the write path") } < lines.indexOfFirst { it.contains("## Content") },
            "the surviving directive keeps its original position above the section"
        )
    }

    @Test
    fun `rewriting a document with no directives is a no-op`() {
        val plain = "# Title\n\n- point\n"
        assertEquals(plain, MarkdownSlideParser.rewriteFollowUpDirectives(plain, emptyList()))
    }

    @Test
    fun `directive keys ignore case and whitespace noise`() {
        assertEquals(
            FollowUpQuestion.normalizeKey("  Why   did   latency Spike? "),
            FollowUpQuestion.normalizeKey("why did latency spike?")
        )
    }
}
