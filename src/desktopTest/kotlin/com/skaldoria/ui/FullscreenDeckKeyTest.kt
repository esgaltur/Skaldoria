package com.skaldoria.ui

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import com.skaldoria.PresentationStateTestBase
import com.skaldoria.core.presentation.HudVisibility
import com.skaldoria.state.PresentationState
import com.skaldoria.ui.screens.FullscreenDeck
import com.skaldoria.ui.screens.PresenterView
import kotlinx.coroutines.Job
import kotlin.test.AfterTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * HUD-1 and the rest of the deck's keyboard surface, delivered into a real composition.
 *
 * ADR-004 named this test and it was never written. `HudVisibilityTest` asserts the enum
 * cycles and `PresenterClickerTest` asserts the registry resolves — both pass while a
 * keystroke never reaches the handler at all, which is the same "assert the user-visible
 * outcome, not the intermediate variable" trap this project keeps falling into.
 */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
@Ignore
class FullscreenDeckKeyTest : PresentationStateTestBase() {

    private fun deck(state: PresentationState) = ImageComposeScene(
        width = 1280,
        height = 720,
        density = Density(1f)
    ) {
        FullscreenDeck(state)
    }

    private fun press(scene: ImageComposeScene, key: Key) {
        scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown))
        scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp))
    }

    /**
     * Background jobs owned by this class, cancelled when it finishes.
     *
     * PRF-4 made `backgroundContext` injectable precisely so a test can cancel it. It matters
     * here more than in a fast unit test: these cases render scenes, so they outlive the 750 ms
     * autosave debounce, and a draft written after the test ends lands in whatever
     * `ConfigManager.rootDir` the *next* test has set — which is a shared global.
     */
    private val backgroundJobs = mutableListOf<Job>()

    @AfterTest
    fun cancelBackgroundWork() {
        backgroundJobs.forEach { it.cancel() }
    }

    private fun freshState(): PresentationState {
        val job = Job().also { backgroundJobs += it }
        return presentationState(backgroundContext = job).apply {
            updateMarkdown("# One\n\n- a\n\n---\n\n# Two\n\n- b\n\n---\n\n# Three\n\n- c\n")
            showWelcome = false
            isFullscreen = true
        }
    }

    @Test
    fun `H cycles the toolbar`() {
        val state = freshState()
        val scene = deck(state)
        try {
            scene.render(0L)

            val before = state.hudVisibility
            press(scene, Key.H)

            assertNotEquals(before, state.hudVisibility, "H did not reach the deck's key handler")
        } finally {
            scene.close()
        }
    }

    @Test
    fun `H still cycles the toolbar after the deck has been clicked`() {
        // The reported symptom. Clicking the slide moves or clears focus, and a key handler
        // hung off a focusable Box only sees events while that Box is on the focus path.
        val state = freshState()
        val scene = deck(state)
        try {
            scene.render(0L)

            scene.sendPointerEvent(PointerEventType.Press, Offset(640f, 360f))
            scene.sendPointerEvent(PointerEventType.Release, Offset(640f, 360f))
            scene.render(16_000_000L)

            val before = state.hudVisibility
            press(scene, Key.H)

            assertNotEquals(
                before,
                state.hudVisibility,
                "H was ignored after clicking the deck — the key handler lost focus"
            )
        } finally {
            scene.close()
        }
    }

    @Test
    fun `H returns the toolbar from every state`() {
        // HUD-1: always recoverable without a mouse.
        for (start in HudVisibility.entries) {
            val state = freshState().apply { hudVisibility = start }
            val scene = deck(state)
            try {
                scene.render(0L)
                repeat(HudVisibility.entries.size) { press(scene, Key.H) }
                assertEquals(
                    start,
                    state.hudVisibility,
                    "cycling H once per state did not return to $start"
                )
            } finally {
                scene.close()
            }
        }
    }

    @Test
    fun `the other bare-letter deck bindings arrive too`() {
        val state = freshState()
        val scene = deck(state)
        try {
            scene.render(0L)

            press(scene, Key.B)
            assertEquals(true, state.isBlackoutActive, "B did not reach the handler")

            press(scene, Key.L)
            assertEquals(true, state.isLaserPointerActive, "L did not reach the handler")

            press(scene, Key.P)
            assertEquals(true, state.isPenDrawingActive, "P did not reach the handler")
        } finally {
            scene.close()
        }
    }

    // ------------------------------------------------------------------
    // The speaker console.
    //
    // `PresenterView` had no key handling of any kind, and its window is `alwaysOnTop`. In
    // presenter mode that window is the one the speaker is looking at and the one holding
    // focus, so the entire deck keyboard surface — and therefore a presenter clicker pointed
    // at it — was dead exactly where a speaker most needs it.
    // ------------------------------------------------------------------

    private fun console(state: PresentationState) = ImageComposeScene(
        width = 1280,
        height = 720,
        density = Density(1f)
    ) {
        PresenterView(state = state, onClose = { state.isPresenterModeActive = false })
    }

    @Test
    fun `H cycles the toolbar from the speaker console`() {
        val state = freshState().apply { isPresenterModeActive = true }
        val scene = console(state)
        try {
            scene.render(0L)

            val before = state.hudVisibility
            press(scene, Key.H)

            assertNotEquals(before, state.hudVisibility, "H is ignored in the speaker console")
        } finally {
            scene.close()
        }
    }

    @Test
    fun `the speaker console advances the deck`() {
        val state = freshState().apply { isPresenterModeActive = true }
        val scene = console(state)
        try {
            scene.render(0L)

            press(scene, Key.DirectionRight)
            assertEquals(1, state.currentSlideIndex, "Right Arrow is ignored in the speaker console")

            press(scene, Key.PageDown)
            assertEquals(2, state.currentSlideIndex, "a clicker is ignored in the speaker console")

            press(scene, Key.PageUp)
            assertEquals(1, state.currentSlideIndex)
        } finally {
            scene.close()
        }
    }

    @Test
    fun `the speaker console blanks the screen`() {
        val state = freshState().apply { isPresenterModeActive = true }
        val scene = console(state)
        try {
            scene.render(0L)

            press(scene, Key.B)
            assertEquals(true, state.isBlackoutActive, "B is ignored in the speaker console")
        } finally {
            scene.close()
        }
    }

    @Test
    fun `arrows advance and retreat the deck`() {
        val state = freshState()
        val scene = deck(state)
        try {
            scene.render(0L)

            press(scene, Key.DirectionRight)
            assertEquals(1, state.currentSlideIndex, "Right Arrow did not reach the handler")

            press(scene, Key.DirectionLeft)
            assertEquals(0, state.currentSlideIndex, "Left Arrow did not reach the handler")
        } finally {
            scene.close()
        }
    }
}
