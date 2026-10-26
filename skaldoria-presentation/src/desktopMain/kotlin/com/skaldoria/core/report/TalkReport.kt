package com.skaldoria.core.report

import com.skaldoria.core.audience.Reaction
import com.skaldoria.core.audience.ReactionEvent
import com.skaldoria.markdown.models.AudienceQuestion
import com.skaldoria.markdown.models.SlideElement
import kotlin.math.abs
import kotlin.math.roundToInt

/** Everything a finished talk leaves behind, gathered at the moment the report is written. */
data class TalkRecord(
    val title: String,
    val slideTitles: List<String>,
    /** The planned seconds per slide, or null when the talk had no target duration. */
    val plannedSeconds: List<Long>?,
    val actualSeconds: Map<Int, Long>,
    val totalElapsedSeconds: Long,
    val targetTotalSeconds: Long?,
    val questions: List<AudienceQuestion>,
    /** Every poll in the deck with its slide index and final tally (`option -> votes`). */
    val polls: List<PollOutcome>,
    val reactions: List<ReactionEvent>
)

data class PollOutcome(val slideIndex: Int, val poll: SlideElement.Poll, val votes: Map<Int, Int>)

/**
 * Turns a [TalkRecord] into a Markdown report: timing against the plan, where the room reacted,
 * the questions asked, and how the polls came out.
 *
 * Markdown because every Skaldoria app reads it — the report opens in the writer for notes, or
 * pastes straight into a follow-up email. Pure, so it is tested without a window.
 *
 * Reactions are counted as **people per slide**, not taps: one phone tapping "lost me" five times
 * on a slide is one confused person, the same rule the live pulse uses.
 */
object TalkReport {

    fun markdown(record: TalkRecord): String = buildString {
        appendLine("# Talk report — ${inline(record.title)}")
        appendLine()
        appendLine(summary(record))
        appendLine()
        timing(record)
        reactions(record)
        questions(record)
        polls(record)
    }.trimEnd() + "\n"

    private fun summary(record: TalkRecord): String {
        val parts = buildList {
            add(
                "**Delivered in ${clock(record.totalElapsedSeconds)}" +
                    (record.targetTotalSeconds?.let { " of a planned ${clock(it)}" } ?: "") + "**"
            )
            add(plural(record.slideTitles.size, "slide"))
            add(plural(record.questions.size, "question"))
            val people = record.reactions.map { it.voterKey }.distinct().size
            if (people > 0) add("${plural(people, "person", "people")} reacted")
        }
        return parts.joinToString(" · ")
    }

    private fun StringBuilder.timing(record: TalkRecord) {
        appendLine("## Timing")
        appendLine()
        val planned = record.plannedSeconds
        if (planned != null) {
            appendLine("| # | Slide | Planned | Actual | Difference |")
            appendLine("|---|---|---|---|---|")
        } else {
            appendLine("| # | Slide | Time on screen |")
            appendLine("|---|---|---|")
        }
        record.slideTitles.forEachIndexed { index, title ->
            val actual = record.actualSeconds[index] ?: 0L
            if (planned != null) {
                val plan = planned.getOrElse(index) { 0L }
                appendLine("| ${index + 1} | ${cell(title)} | ${clock(plan)} | ${clock(actual)} | ${signedClock(actual - plan)} |")
            } else {
                appendLine("| ${index + 1} | ${cell(title)} | ${clock(actual)} |")
            }
        }
        if (planned != null) {
            val worst = record.slideTitles.indices.maxByOrNull { (record.actualSeconds[it] ?: 0L) - planned.getOrElse(it) { 0L } }
            if (worst != null) {
                val over = (record.actualSeconds[worst] ?: 0L) - planned.getOrElse(worst) { 0L }
                if (over > 0) {
                    appendLine()
                    appendLine("The biggest overrun was slide ${worst + 1}, *${inline(record.slideTitles[worst])}*, at ${signedClock(over)}.")
                }
            }
        }
        appendLine()
    }

    private fun StringBuilder.reactions(record: TalkRecord) {
        appendLine("## Where the room reacted")
        appendLine()
        // slide -> reaction -> distinct people
        val people = record.reactions
            .groupBy { it.slideIndex }
            .mapValues { (_, events) ->
                events.groupBy { it.reaction }.mapValues { (_, byReaction) -> byReaction.map { it.voterKey }.distinct().size }
            }
        if (people.isEmpty()) {
            appendLine("_Nobody sent a reaction._")
            appendLine()
            return
        }
        appendLine("| # | Slide | 🐢 Too fast | 😕 Lost me | 👍 Good |")
        appendLine("|---|---|---|---|---|")
        people.keys.sorted().forEach { slide ->
            val counts = people.getValue(slide)
            val title = record.slideTitles.getOrElse(slide) { "Slide ${slide + 1}" }
            appendLine(
                "| ${slide + 1} | ${cell(title)} | ${counts[Reaction.TooFast] ?: 0} | " +
                    "${counts[Reaction.Lost] ?: 0} | ${counts[Reaction.Good] ?: 0} |"
            )
        }
        val mostLost = people.maxByOrNull { it.value[Reaction.Lost] ?: 0 }
        val lostCount = mostLost?.value?.get(Reaction.Lost) ?: 0
        if (mostLost != null && lostCount > 0) {
            val title = record.slideTitles.getOrElse(mostLost.key) { "Slide ${mostLost.key + 1}" }
            appendLine()
            appendLine(
                "Most people got lost on slide ${mostLost.key + 1}, *${inline(title)}* — " +
                    "${plural(lostCount, "person", "people")}. Worth another pass before the next run."
            )
        }
        appendLine()
    }

    private fun StringBuilder.questions(record: TalkRecord) {
        appendLine("## Questions")
        appendLine()
        if (record.questions.isEmpty()) {
            appendLine("_No questions were asked._")
            appendLine()
            return
        }
        record.questions.sortedByDescending { it.upvotes }.forEachIndexed { index, question ->
            val answered = if (question.isAnswered) " · answered" else ""
            appendLine("${index + 1}. **${inline(question.text)}** — ${inline(question.author)} · 👍 ${question.upvotes}$answered")
        }
        appendLine()
    }

    private fun StringBuilder.polls(record: TalkRecord) {
        if (record.polls.isEmpty()) return
        appendLine("## Polls")
        appendLine()
        record.polls.forEach { outcome ->
            val question = outcome.poll.question.ifBlank { "Poll" }
            appendLine("### ${inline(question)} (slide ${outcome.slideIndex + 1})")
            appendLine()
            val total = outcome.votes.values.sum()
            outcome.poll.options.forEachIndexed { index, option ->
                val votes = outcome.votes[index] ?: 0
                val share = if (total > 0) " (${(votes * 100.0 / total).roundToInt()}%)" else ""
                appendLine("- ${inline(option)} — ${plural(votes, "vote")}$share")
            }
            appendLine()
        }
    }

    // ---- formatting ----

    private fun clock(seconds: Long): String = "%d:%02d".format(seconds / 60, seconds % 60)

    private fun signedClock(seconds: Long): String =
        (if (seconds > 0) "+" else if (seconds < 0) "−" else "±") + clock(abs(seconds))

    private fun plural(count: Int, one: String, many: String = "${one}s") = "$count ${if (count == 1) one else many}"

    /**
     * Audience text is untrusted and arrives raw. Escaped so a question containing `**` or a
     * link cannot restyle the report, and collapsed to one line so it cannot start a heading.
     */
    private fun inline(text: String): String =
        text.replace(Regex("\\s+"), " ").trim().replace(MARKDOWN_SPECIALS) { "\\" + it.value }

    /** As [inline], plus `|`, which would otherwise end the table cell. */
    private fun cell(text: String): String = inline(text).replace("|", "\\|")

    private val MARKDOWN_SPECIALS = Regex("""[\\`*_\[\]<>#]""")
}
