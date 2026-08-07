package com.skaldoria.core.audience

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.Snapshot
import com.skaldoria.markdown.models.AudienceQuestion
import java.util.UUID

/**
 * Everything the room contributes during a talk: the question queue and the poll ballots.
 *
 * F-08: extracted from `PresentationState`. This is the *only* surface the companion server
 * needs to mutate, and it was previously reachable only by handing a network worker thread
 * the entire application state — file dialogs, theme unlocking and structural slide editing
 * included (ISP).
 *
 * Every bound here is SEC-5. The inputs arrive from untrusted devices on the local network
 * and are re-sent to every other device on each poll, so nothing may grow without limit.
 */
class AudienceSession {

    private val _questions = mutableStateListOf<AudienceQuestion>()

    /** Newest first. Observable by Compose; use [snapshot] when reading off-thread. */
    val questions: List<AudienceQuestion> get() = _questions

    /**
     * SEC-5: `slideIndex -> (voterKey -> chosen option)`.
     *
     * Counts are *derived*, never stored. The original model stored `option -> count` and
     * incremented, so refreshing the audience page and voting again stacked votes without
     * limit — the client-side "already voted" flag was advisory only. Keying by voter both
     * prevents stuffing and lets someone change their mind, which a counter cannot express.
     */
    private val ballots = mutableStateMapOf<Int, Map<String, Int>>()

    // ---- questions ----

    /**
     * Adds a question from the room. Both arguments are untrusted display text: a blank
     * [author] becomes "Anonymous", and both are length-capped.
     */
    fun submit(author: String, text: String): AudienceQuestion {
        val question = AudienceQuestion(
            // COR-6: a millisecond timestamp plus four random digits collides under exactly
            // the concurrent submission this endpoint is built for, and upvote/dismiss then
            // hit the wrong item.
            id = "q_${UUID.randomUUID()}",
            author = author.ifBlank { "Anonymous" }.take(MAX_AUTHOR_LENGTH),
            text = text.trim().take(MAX_QUESTION_LENGTH)
        )
        _questions.add(0, question)

        // SEC-5: the queue grew without limit. Oldest entries fall off the end.
        while (_questions.size > MAX_QUESTIONS) {
            _questions.removeAt(_questions.size - 1)
        }
        return question
    }

    fun upvote(questionId: String) = update(questionId) { it.copy(upvotes = it.upvotes + 1) }

    fun markAnswered(questionId: String) = update(questionId) { it.copy(isAnswered = true) }

    fun dismiss(questionId: String) {
        _questions.removeAll { it.id == questionId }
    }

    fun find(questionId: String): AudienceQuestion? = _questions.firstOrNull { it.id == questionId }

    /**
     * PRF-1: a consistent point-in-time copy, safe to read from an HTTP worker thread.
     *
     * The state endpoint previously did a bare `.toList()` inside a try/catch that swallowed
     * the resulting `ConcurrentModificationException`.
     */
    fun snapshot(): List<AudienceQuestion> = Snapshot.withMutableSnapshot { _questions.toList() }

    private fun update(questionId: String, transform: (AudienceQuestion) -> AudienceQuestion) {
        val index = _questions.indexOfFirst { it.id == questionId }
        if (index != -1) _questions[index] = transform(_questions[index])
    }

    // ---- ballots ----

    /**
     * Records one ballot.
     *
     * @param voterKey stable per audience device. Null means a local in-app vote from the
     *   speaker's own machine, which always counts as a distinct ballot.
     */
    fun recordVote(slideIndex: Int, optionIndex: Int, voterKey: String? = null) {
        val slideBallots = ballots[slideIndex].orEmpty().toMutableMap()
        slideBallots[voterKey ?: "local:${slideBallots.size}"] = optionIndex
        ballots[slideIndex] = slideBallots
    }

    /** Tallies the current ballots into `optionIndex -> count`. */
    fun votesForSlide(slideIndex: Int): Map<Int, Int> =
        ballots[slideIndex].orEmpty().values.groupingBy { it }.eachCount()

    fun resetVotes(slideIndex: Int) {
        ballots.remove(slideIndex)
    }

    companion object {
        /** SEC-5: bounds on audience-supplied content. */
        const val MAX_QUESTIONS = 200
        const val MAX_QUESTION_LENGTH = 500
        const val MAX_AUTHOR_LENGTH = 60
    }
}
