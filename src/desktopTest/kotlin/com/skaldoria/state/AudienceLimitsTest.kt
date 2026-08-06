package com.skaldoria.state

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * SEC-5 — vote deduplication and bounds on audience-supplied content, exercised through
 * `PresentationState`.
 *
 * `AudienceSessionTest` covers the same rules at the unit level; this keeps the assertions
 * on the object the application actually wires together.
 */
class AudienceLimitsTest {

    @Test
    fun `a voter cannot stack votes by voting repeatedly`() {
        val state = PresentationState()

        repeat(20) { state.recordVote(slideIndex = 0, optionIndex = 1, voterKey = "192.168.1.50") }

        assertEquals(
            mapOf(1 to 1),
            state.getVotesForSlide(0),
            "SEC-5: twenty submissions from one device must count once"
        )
    }

    @Test
    fun `a voter can change their mind without inflating the total`() {
        val state = PresentationState()

        state.recordVote(0, optionIndex = 0, voterKey = "device-a")
        state.recordVote(0, optionIndex = 2, voterKey = "device-a")

        assertEquals(mapOf(2 to 1), state.getVotesForSlide(0), "the later choice replaces the earlier one")
    }

    @Test
    fun `distinct voters are counted separately`() {
        val state = PresentationState()

        state.recordVote(0, 0, voterKey = "a")
        state.recordVote(0, 0, voterKey = "b")
        state.recordVote(0, 1, voterKey = "c")

        assertEquals(mapOf(0 to 2, 1 to 1), state.getVotesForSlide(0))
    }

    /** In-app votes come from the speaker's own machine and have no device key. */
    @Test
    fun `local votes without a key each count`() {
        val state = PresentationState()

        state.recordVote(0, 1)
        state.recordVote(0, 1)

        assertEquals(mapOf(1 to 2), state.getVotesForSlide(0))
    }

    @Test
    fun `votes are tracked per slide`() {
        val state = PresentationState()

        state.recordVote(0, 0, voterKey = "a")
        state.recordVote(1, 1, voterKey = "a")

        assertEquals(mapOf(0 to 1), state.getVotesForSlide(0))
        assertEquals(mapOf(1 to 1), state.getVotesForSlide(1))
    }

    @Test
    fun `the question queue is bounded and drops the oldest`() {
        val state = PresentationState()

        repeat(PresentationState.MAX_AUDIENCE_QUESTIONS + 25) { index ->
            state.audience.submit("Asker $index", "Question number $index")
        }

        assertEquals(PresentationState.MAX_AUDIENCE_QUESTIONS, state.audience.questions.size)
        assertTrue(
            state.audience.questions.none { it.text.endsWith(" 0") },
            "SEC-5: the oldest questions must be evicted, not the newest"
        )
        assertTrue(
            state.audience.questions.any { it.text.contains("${PresentationState.MAX_AUDIENCE_QUESTIONS + 24}") },
            "the most recent question must survive"
        )
    }

    @Test
    fun `oversized question text and author are truncated`() {
        val state = PresentationState()

        val question = state.audience.submit("A".repeat(500), "Q".repeat(5_000))

        assertEquals(PresentationState.MAX_QUESTION_LENGTH, question.text.length)
        assertEquals(PresentationState.MAX_AUTHOR_LENGTH, question.author.length)
    }

    @Test
    fun `blank author falls back to Anonymous`() {
        val state = PresentationState()
        assertEquals("Anonymous", state.audience.submit("   ", "hello").author)
    }
}
