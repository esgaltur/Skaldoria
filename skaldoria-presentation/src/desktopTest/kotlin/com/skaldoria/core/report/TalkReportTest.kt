package com.skaldoria.core.report

import com.skaldoria.core.audience.Reaction
import com.skaldoria.core.audience.ReactionEvent
import com.skaldoria.markdown.models.AudienceQuestion
import com.skaldoria.markdown.models.SlideElement
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TalkReportTest {

    private fun record(
        planned: List<Long>? = listOf(60, 120, 60),
        actual: Map<Int, Long> = mapOf(0 to 50L, 1 to 200L, 2 to 40L),
        questions: List<AudienceQuestion> = emptyList(),
        polls: List<PollOutcome> = emptyList(),
        reactions: List<ReactionEvent> = emptyList()
    ) = TalkRecord(
        title = "Scaling Writes",
        slideTitles = listOf("Intro", "Sharding", "Wrap up"),
        plannedSeconds = planned,
        actualSeconds = actual,
        totalElapsedSeconds = actual.values.sum(),
        targetTotalSeconds = planned?.sum(),
        questions = questions,
        polls = polls,
        reactions = reactions
    )

    private fun react(slide: Int, reaction: Reaction, voter: String) = ReactionEvent(0L, slide, reaction, voter)

    @Test
    fun `timing is set against the plan and the worst overrun is named`() {
        val report = TalkReport.markdown(record())

        assertTrue("**Delivered in 4:50 of a planned 4:00**" in report, report)
        assertTrue("| 2 | Sharding | 2:00 | 3:20 | +1:20 |" in report, report)
        assertTrue("| 1 | Intro | 1:00 | 0:50 | −0:10 |" in report, report)
        assertTrue("The biggest overrun was slide 2, *Sharding*, at +1:20." in report, report)
    }

    @Test
    fun `without a target the report shows time on screen only`() {
        val report = TalkReport.markdown(record(planned = null))

        assertTrue("| # | Slide | Time on screen |" in report, report)
        assertTrue("| 2 | Sharding | 3:20 |" in report, report)
        assertFalse("Planned" in report || "overrun" in report, report)
    }

    @Test
    fun `reactions count people per slide, not taps`() {
        val report = TalkReport.markdown(
            record(
                reactions = listOf(
                    react(1, Reaction.Lost, "a"), react(1, Reaction.Lost, "a"), react(1, Reaction.Lost, "a"),
                    react(1, Reaction.Lost, "b"), react(1, Reaction.Good, "c"), react(0, Reaction.TooFast, "a")
                )
            )
        )

        assertTrue("| 2 | Sharding | 0 | 2 | 1 |" in report, report)
        assertTrue("| 1 | Intro | 1 | 0 | 0 |" in report, report)
        assertTrue("Most people got lost on slide 2, *Sharding* — 2 people." in report, report)
        assertTrue("3 people reacted" in report, report)
    }

    @Test
    fun `questions are ranked by upvotes`() {
        val report = TalkReport.markdown(
            record(
                questions = listOf(
                    AudienceQuestion(id = "1", author = "Ada", text = "Why not Raft?", upvotes = 1),
                    AudienceQuestion(id = "2", author = "Bo", text = "Hot shards?", upvotes = 7, isAnswered = true)
                )
            )
        )

        assertTrue("1. **Hot shards?** — Bo · 👍 7 · answered\n2. **Why not Raft?** — Ada · 👍 1" in report, report)
    }

    @Test
    fun `polls show votes and shares for every option`() {
        val poll = SlideElement.Poll("Which store?", listOf("Postgres", "Cassandra", "Other"))
        val report = TalkReport.markdown(record(polls = listOf(PollOutcome(1, poll, mapOf(0 to 3, 1 to 1)))))

        assertTrue("### Which store? (slide 2)" in report, report)
        assertTrue("- Postgres — 3 votes (75%)" in report, report)
        assertTrue("- Cassandra — 1 vote (25%)" in report, report)
        assertTrue("- Other — 0 votes (0%)" in report, report)
    }

    @Test
    fun `an empty room is said plainly and an absent poll section is left out`() {
        val report = TalkReport.markdown(record())

        assertTrue("_Nobody sent a reaction._" in report, report)
        assertTrue("_No questions were asked._" in report, report)
        assertFalse("## Polls" in report, report)
    }

    @Test
    fun `audience text cannot restyle the report`() {
        val hostile = "**bold** [click](http://x)\n# Heading | cell"
        val report = TalkReport.markdown(
            record(questions = listOf(AudienceQuestion(id = "1", author = "<b>me</b>", text = hostile)))
        )

        val line = report.lines().single { it.startsWith("1. ") }
        assertTrue("\\*\\*bold\\*\\* \\[click\\](http://x) \\# Heading | cell" in line, line)
        assertTrue("\\<b\\>me\\</b\\>" in line, line)
        assertFalse(report.lines().any { it.startsWith("# Heading") }, "a question opened a heading")
    }
}
