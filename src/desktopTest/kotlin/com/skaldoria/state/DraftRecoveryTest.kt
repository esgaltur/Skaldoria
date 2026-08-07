package com.skaldoria.state

import com.skaldoria.PresentationStateTestBase
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
class DraftRecoveryTest : PresentationStateTestBase() {

    /**
     * COR-14 is what makes this a plain reset rather than a wait.
     *
     * This test reads a process-wide file every `PresentationState` in the suite can write, and
     * a mutation schedules a debounced save that only `dispose()` cancels. While disposal was
     * by convention — 18 of 23 test files skipped it — a job outlived the test that created it
     * and landed here, replacing the draft between the `saveDraft` call and the assertion. The
     * workaround was `Thread.sleep(DRAFT_SAVE_DEBOUNCE_MS + 250)` here, which made the symptom
     * deterministic without removing the cause, and cost the suite a second per test in this
     * class. [com.skaldoria.PresentationStateTestBase] now disposes every state before the next
     * test starts, so there is nothing left in flight to drain.
     */
    @BeforeTest
    fun clearDraftBefore() = ConfigManager.clearDraft()

    @AfterTest
    fun clearDraft() = ConfigManager.clearDraft()

    @Test
    fun `no draft means nothing to recover`() {
        assertNull(presentationState().recoverableDraft())
    }

    @Test
    fun `a saved draft is offered back`() {
        val work = "# Half-written keynote\n\n- a point I had not saved"
        ConfigManager.saveDraft(work)

        assertEquals(work, presentationState().recoverableDraft())
    }

    /** A user who never edited anything must not be prompted about the built-in decks. */
    @Test
    fun `the bundled sample decks are not offered as recovered work`() {
        ConfigManager.saveDraft(SampleDecks.DEFAULT_SAMPLE_MARKDOWN)
        assertNull(presentationState().recoverableDraft(), "the demo deck is not the user's work")

        ConfigManager.saveDraft(SampleDecks.BLANK_STARTER_MARKDOWN)
        assertNull(presentationState().recoverableDraft(), "the blank starter is not the user's work")
    }

    @Test
    fun `a blank draft is not offered`() {
        ConfigManager.saveDraft("   \n  \n")
        assertNull(presentationState().recoverableDraft())
    }

    @Test
    fun `restoring adopts the draft as the working deck`() {
        val work = "# Recovered\n\n- restored content\n\n---\n\n## Second slide"
        ConfigManager.saveDraft(work)

        val state = presentationState()
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
        val state = presentationState()
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
        assertEquals(edited, presentationState().recoverableDraft())
    }
}
