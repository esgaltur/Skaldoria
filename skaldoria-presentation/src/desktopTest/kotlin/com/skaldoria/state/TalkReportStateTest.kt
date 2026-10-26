package com.skaldoria.state

import com.skaldoria.PresentationStateTestBase
import com.skaldoria.core.audience.Reaction
import com.skaldoria.core.pacing.TalkTimer
import com.skaldoria.core.ports.FileDialogs
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The post-talk report is fed by the talk as it actually ran. */
class TalkReportStateTest : PresentationStateTestBase() {

    private var nanos = 0L
    private fun advance(seconds: Long) { nanos += seconds * 1_000_000_000L }

    private class SavingDialogs(private val failWith: Exception? = null) : FileDialogs {
        var saved: String? = null
        override fun openFileOrProject(onChosen: (File) -> Unit) = error("not used")
        override fun saveMarkdownFile(currentPath: String?, content: String, onSaved: (String) -> Unit) = error("not used")
        override fun saveAsMarkdownFile(content: String, onSaved: (String) -> Unit) {
            failWith?.let { throw it }
            saved = content
            onSaved("/fake/report.md")
        }
    }

    private val talk = """
        # Intro
        ---
        # Survey
        <!-- poll: Postgres | Cassandra -->
        ---
        # Wrap up
    """.trimIndent()

    private fun state(dialogs: FileDialogs = SavingDialogs()) =
        presentationState(initialMarkdown = talk, timer = TalkTimer(nanoTime = { nanos }), fileDialogs = dialogs)

    @Test
    fun `time on each slide follows the speaker's navigation on the talk clock`() {
        val state = state()
        state.toggleTimer()
        advance(30); state.nextSlide()
        advance(90); state.goToSlide(2)
        advance(15); state.previousSlide()
        advance(5)

        val record = state.talkRecord()
        assertEquals(mapOf(0 to 30L, 1 to 95L, 2 to 15L), record.actualSeconds)
        assertEquals(140L, record.totalElapsedSeconds)
    }

    @Test
    fun `resetting the timer starts the timing afresh`() {
        val state = state()
        state.toggleTimer()
        advance(30); state.nextSlide()
        advance(30)
        state.resetTimer()
        state.toggleTimer()
        advance(10)

        assertEquals(mapOf(1 to 10L), state.talkRecord().actualSeconds)
    }

    @Test
    fun `the record gathers the room's contributions`() {
        val state = state()
        state.targetTalkDurationMinutes = 3
        state.audience.submit(author = "Bo", text = "Hot shards?")
        state.audience.recordVote(slideIndex = 1, optionIndex = 0, voterKey = "phone-1")
        state.audience.reactions.react("phone-1", Reaction.Lost, slideIndex = 1)

        val record = state.talkRecord()
        assertEquals(listOf("Intro", "Survey", "Wrap up"), record.slideTitles)
        assertEquals(180L, record.plannedSeconds!!.sum())
        assertEquals("Hot shards?", record.questions.single().text)
        assertEquals(1, record.polls.single().slideIndex)
        assertEquals(mapOf(0 to 1), record.polls.single().votes)
        assertEquals(1, record.reactions.size)
    }

    @Test
    fun `saving writes the report and hands back where it went`() {
        val dialogs = SavingDialogs()
        val state = state(dialogs)
        var savedPath: String? = null

        state.saveTalkReport { savedPath = it }

        assertTrue(dialogs.saved!!.startsWith("# Talk report — Intro"), dialogs.saved)
        assertEquals("/fake/report.md", savedPath)
        assertNull(state.lastError)
    }

    @Test
    fun `a failed save is reported rather than lost`() {
        val state = state(SavingDialogs(failWith = java.io.IOException("disk full")))

        state.saveTalkReport()

        assertTrue(state.lastError.orEmpty().contains("disk full"), state.lastError)
    }
}
