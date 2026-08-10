package com.skaldoria.core.audience

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * F-08: SEC-5's bounds and ballot integrity, at the unit level.
 *
 * These were only reachable through `PresentationState` — a 132-member class — which is also
 * what the companion server was handed in order to use about a dozen of them.
 * `AudienceLimitsTest` still exercises the same rules through the facade, so the extraction
 * is guarded from both sides.
 */
class AudienceSessionTest {

    private val session = AudienceSession()

    // ---- ballots ----

    @Test
    fun `one device holds one ballot`() {
        repeat(20) { session.recordVote(slideIndex = 0, optionIndex = 1, voterKey = "192.168.1.50") }
        assertEquals(mapOf(1 to 1), session.votesForSlide(0))
    }

    @Test
    fun `a voter can change their mind without inflating the total`() {
        session.recordVote(0, 0, "device-a")
        session.recordVote(0, 2, "device-a")
        assertEquals(mapOf(2 to 1), session.votesForSlide(0))
    }

    @Test
    fun `local votes without a device key each count`() {
        session.recordVote(0, 1, voterKey = null)
        session.recordVote(0, 1, voterKey = null)
        assertEquals(mapOf(1 to 2), session.votesForSlide(0))
    }

    @Test
    fun `ballots are tracked per slide`() {
        session.recordVote(0, 0, "a")
        session.recordVote(1, 1, "a")
        assertEquals(mapOf(0 to 1), session.votesForSlide(0))
        assertEquals(mapOf(1 to 1), session.votesForSlide(1))
    }

    @Test
    fun `resetting a slide clears only that slide`() {
        session.recordVote(0, 0, "a")
        session.recordVote(1, 0, "a")
        session.resetVotes(0)
        assertEquals(emptyMap(), session.votesForSlide(0))
        assertEquals(mapOf(0 to 1), session.votesForSlide(1))
    }

    @Test
    fun `a slide with no ballots tallies empty`() {
        assertEquals(emptyMap(), session.votesForSlide(7))
    }

    // ---- question queue ----

    @Test
    fun `the queue is bounded and evicts the oldest`() {
        repeat(AudienceSession.MAX_QUESTIONS + 25) { session.submit("Asker $it", "Question number $it") }

        assertEquals(AudienceSession.MAX_QUESTIONS, session.questions.size)
        assertTrue(session.questions.none { it.text.endsWith(" 0") }, "oldest must be evicted")
        assertTrue(
            session.questions.any { it.text.endsWith("${AudienceSession.MAX_QUESTIONS + 24}") },
            "newest must survive"
        )
    }

    @Test
    fun `oversized text and author are truncated`() {
        val question = session.submit("A".repeat(500), "Q".repeat(5_000))
        assertEquals(AudienceSession.MAX_QUESTION_LENGTH, question.text.length)
        assertEquals(AudienceSession.MAX_AUTHOR_LENGTH, question.author.length)
    }

    @Test
    fun `a blank author becomes Anonymous`() {
        assertEquals("Anonymous", session.submit("   ", "hello").author)
    }

    @Test
    fun `newest questions come first`() {
        session.submit("a", "first")
        session.submit("b", "second")
        assertEquals("second", session.questions.first().text)
    }

    /** COR-6: identifiers must not collide under concurrent submission. */
    @Test
    fun `question identifiers are unique`() {
        val ids = (1..500).map { session.submit("a", "q$it").id }
        assertEquals(ids.size, ids.distinct().size, "COR-6: identifier collision")
    }

    // ---- moderation ----

    @Test
    fun `upvoting increments only the target`() {
        val a = session.submit("a", "first")
        val b = session.submit("b", "second")
        session.upvote(a.id)
        session.upvote(a.id)

        assertEquals(2, session.questions.single { it.id == a.id }.upvotes)
        assertEquals(0, session.questions.single { it.id == b.id }.upvotes)
    }

    @Test
    fun `marking answered flags only the target`() {
        val a = session.submit("a", "first")
        session.markAnswered(a.id)
        assertTrue(session.questions.single { it.id == a.id }.isAnswered)
    }

    @Test
    fun `dismissing removes the question`() {
        val a = session.submit("a", "first")
        session.dismiss(a.id)
        assertTrue(session.questions.none { it.id == a.id })
    }

    @Test
    fun `moderating an unknown id is a no-op rather than a crash`() {
        session.submit("a", "first")
        session.upvote("nope")
        session.markAnswered("nope")
        session.dismiss("nope")
        assertEquals(1, session.questions.size)
        assertEquals(0, session.questions.first().upvotes)
        assertFalse(session.questions.first().isAnswered)
        assertNull(session.questions.firstOrNull { it.id == "nope" })
    }

    @Test
    fun `a snapshot is an independent copy`() {
        session.submit("a", "first")
        val snapshot = session.snapshot()
        session.submit("b", "second")
        assertEquals(1, snapshot.size, "the snapshot must not observe later mutations")
        assertEquals(2, session.questions.size)
    }
}
