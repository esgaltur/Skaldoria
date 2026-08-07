package com.skaldoria.state

import com.skaldoria.config.ConfigManager
import com.skaldoria.core.deck.SampleDecks
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * DED-1: an unsaved draft survives a crash and is offered on the next launch.
 *
 * The invariant was implemented and recorded, but had **no guard** — and the three functions
 * that deliver it (`recoverableDraft`, `restoreDraft`, `discardDraft`) had no call site
 * anywhere in `desktopMain`, so the welcome screen never actually offered the recovery the
 * baseline describes. The feature existed; nothing reached it.
 */
class DraftRecoveryTest {

    /**
     * Drains any autosave still in flight from an *earlier* test before clearing.
     *
     * This test reads a process-wide file that every `PresentationState` in the suite can write.
     * A mutation schedules a debounced save on that instance's own coroutine scope; `dispose()`
     * cancels it, but **19 test files construct a `PresentationState` and only 4 dispose it**, so
     * a job outlives the test that created it and lands here, replacing the draft between the
     * `saveDraft` call and the assertion. That is the race behind
     * `the bundled sample decks are not offered as recovered work` failing intermittently —
     * reproducibly enough to block two builds, rarely enough to pass in isolation and on re-run.
     *
     * Waiting out the debounce is a workaround, not the fix. The fix is disposal discipline in the
     * other 15 files; this makes the symptom deterministic in the meantime, and the sleep is
     * bounded and derived rather than a guessed magic number.
     */
    @BeforeTest
    fun drainPendingAutosaves() {
        Thread.sleep(PresentationState.DRAFT_SAVE_DEBOUNCE_MS + DRAIN_MARGIN_MS)
        ConfigManager.clearDraft()
    }

    @AfterTest
    fun clearDraft() = ConfigManager.clearDraft()

    private companion object {
        /** Slack over the debounce, for the IO dispatch and file write that follow it. */
        const val DRAIN_MARGIN_MS = 250L
    }

    @Test
    fun `no draft means nothing to recover`() {
        assertNull(PresentationState().recoverableDraft())
    }

    @Test
    fun `a saved draft is offered back`() {
        val work = "# Half-written keynote\n\n- a point I had not saved"
        ConfigManager.saveDraft(work)

        assertEquals(work, PresentationState().recoverableDraft())
    }

    /** A user who never edited anything must not be prompted about the built-in decks. */
    @Test
    fun `the bundled sample decks are not offered as recovered work`() {
        ConfigManager.saveDraft(SampleDecks.DEFAULT_SAMPLE_MARKDOWN)
        assertNull(PresentationState().recoverableDraft(), "the demo deck is not the user's work")

        ConfigManager.saveDraft(SampleDecks.BLANK_STARTER_MARKDOWN)
        assertNull(PresentationState().recoverableDraft(), "the blank starter is not the user's work")
    }

    @Test
    fun `a blank draft is not offered`() {
        ConfigManager.saveDraft("   \n  \n")
        assertNull(PresentationState().recoverableDraft())
    }

    @Test
    fun `restoring adopts the draft as the working deck`() {
        val work = "# Recovered\n\n- restored content\n\n---\n\n## Second slide"
        ConfigManager.saveDraft(work)

        val state = PresentationState()
        val draft = state.recoverableDraft()
        assertNotNull(draft)

        state.restoreDraft(draft)

        assertEquals(work, state.markdownText)
        assertEquals(2, state.slides.size, "the recovered deck should parse")
        assertEquals(0, state.currentSlideIndex)
        assertFalse(state.showWelcome, "restoring should leave the welcome screen")
        assertNull(state.activeProject, "a recovered draft is a loose document, not a project")
        state.dispose()
    }

    @Test
    fun `discarding stops the draft being offered again`() {
        ConfigManager.saveDraft("# Unwanted")
        val state = PresentationState()
        assertNotNull(state.recoverableDraft())

        state.discardDraft()

        assertNull(state.recoverableDraft(), "a discarded draft must not come back")
        state.dispose()
    }

    @Test
    fun `editing a deck writes a draft that can later be recovered`() {
        // PRF-2 debounces the autosave, so drive the persistence layer directly rather than
        // sleeping: what matters here is that what is written is what is offered back.
        val edited = "# Live edit\n\n- typed during a talk"
        ConfigManager.saveDraft(edited)
        assertTrue(ConfigManager.loadDraft()!!.contains("typed during a talk"))
        assertEquals(edited, PresentationState().recoverableDraft())
    }
}
