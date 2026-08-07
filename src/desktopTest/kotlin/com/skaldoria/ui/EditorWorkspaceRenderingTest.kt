package com.skaldoria.ui

import com.skaldoria.RenderEnvironment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.skaldoria.PresentationStateTestBase
import com.skaldoria.state.PresentationState
import com.skaldoria.ui.screens.EditorWorkspace
import kotlinx.coroutines.Job
import java.awt.image.BufferedImage
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The source pane draws, and navigation actually moves it.
 *
 * Two failure modes are covered, both of which a green unit suite is blind to.
 *
 * **It draws.** AUT-05 replaced the Material `TextField` with a `BasicTextField` scrolled by an
 * explicit `Modifier.verticalScroll`, which hands the field an *unbounded* height. Unbounded
 * main-axis constraints are what caused OVF-1, the regression that blanked every slide while
 * every test passed.
 *
 * **It moves.** EDT-4 is the whole point of ADR-004: `findNext()` advancing an index while the
 * viewport stays put is the defect, not the feature. `EditorRevealTest` asserts that a reveal
 * is *published*; only a render can show that the pane in front of the user changed.
 */
@OptIn(ExperimentalComposeUiApi::class)
class EditorWorkspaceRenderingTest : PresentationStateTestBase() {

    @BeforeTest
    fun requireDisplay() = RenderEnvironment.requireDisplay()

    private val width = 1600
    private val height = 900

    /** The source pane's rectangle, from the studio layout: left column, below the header. */
    private val paneLeft = 40
    private val paneTop = 130
    private val paneRight = 680
    private val paneBottom = 750

    /**
     * Background jobs owned by this class, cancelled when it finishes.
     *
     * These cases render scenes, so they outlive the 750 ms autosave debounce — and a draft
     * written after the test ends lands in whatever `ConfigManager.rootDir` the *next* test has
     * set, which is a shared global. PRF-4 made `backgroundContext` injectable for this.
     */
    private val backgroundJobs = mutableListOf<Job>()

    @AfterTest
    fun cancelBackgroundWork() {
        backgroundJobs.forEach { it.cancel() }
    }

    private fun deckState(markdown: String): PresentationState {
        val job = Job().also { backgroundJobs += it }
        return presentationState(backgroundContext = job).apply {
            updateMarkdown(markdown)
            showWelcome = false
        }
    }

    private fun longDeck(): String = buildString {
        for (slide in 1..40) {
            appendLine("# Slide $slide")
            appendLine()
            repeat(8) { appendLine("- A reasonably long bullet number $it on slide $slide") }
            appendLine()
            appendLine("---")
            appendLine()
        }
    }

    /**
     * Renders the studio window over enough frames for a scroll animation to settle.
     *
     * A single `render()` composes and runs effects but never advances the clock, so
     * `animateScrollTo` would still be at frame zero — a one-frame render can only ever show
     * the pane *before* the reveal, which is precisely the bug under test.
     */
    private fun renderSettled(state: PresentationState, dumpAs: String): BufferedImage {
        val scene = ImageComposeScene(width = width, height = height, density = Density(1f)) {
            EditorWorkspace(state = state)
        }
        try {
            var image = scene.render(0L)
            var nanos = 0L
            repeat(FRAMES) {
                nanos += FRAME_NANOS
                image = scene.render(nanos)
            }

            File("build/render-check").mkdirs()
            val png = image.encodeToData()?.bytes ?: error("could not encode the rendered editor")
            File("build/render-check/$dumpAs.png").writeBytes(png)

            return javax.imageio.ImageIO.read(png.inputStream())
                ?: error("could not decode the rendered editor")
        } finally {
            scene.close()
        }
    }

    /** Pixels differing from the most common (background) colour, sampled on a grid. */
    private fun contentPixels(image: BufferedImage): Int {
        val histogram = HashMap<Int, Int>()
        for (y in 0 until image.height step 2) {
            for (x in 0 until image.width step 2) {
                val color = image.getRGB(x, y)
                histogram[color] = (histogram[color] ?: 0) + 1
            }
        }
        return histogram.values.sum() - (histogram.maxByOrNull { it.value }?.value ?: 0)
    }

    /** Fraction of sampled pixels inside the source pane that differ between two renders. */
    private fun paneDifference(a: BufferedImage, b: BufferedImage): Double {
        var differing = 0
        var sampled = 0
        for (y in paneTop until paneBottom step 2) {
            for (x in paneLeft until paneRight step 2) {
                sampled++
                if (a.getRGB(x, y) != b.getRGB(x, y)) differing++
            }
        }
        return differing.toDouble() / sampled
    }

    @Test
    fun `the studio window draws its source pane`() {
        val state = deckState(longDeck())

        val content = contentPixels(renderSettled(state, "editor_workspace"))

        // A blank editor still draws chrome — the top bar, the filmstrip, the preview — so the
        // floor sits well above "some pixels". A pane full of monospace markdown contributes
        // tens of thousands.
        assertTrue(
            content > 20_000,
            "the studio window drew only $content content pixels — the source pane is likely empty"
        )
    }

    @Test
    fun `selecting a slide scrolls the source pane to it`() {
        // AUT-02, as the user experiences it. Before this work the pane was identical in both
        // renders: the preview moved to slide 40 and the source still showed line 1.
        val first = renderSettled(deckState(longDeck()), "editor_workspace_slide_1")

        val atEnd = deckState(longDeck()).apply { goToSlide(slides.size - 1) }
        val last = renderSettled(atEnd, "editor_workspace_slide_40")

        val difference = paneDifference(first, last)
        assertTrue(
            difference > 0.05,
            "the source pane is ${"%.1f".format(difference * 100)}% different after jumping to " +
                "slide 40 — it did not scroll"
        )
    }

    @Test
    fun `finding a match late in the deck scrolls it into view`() {
        // EDT-4. The needle appears once, near the end; the pane must move to it.
        val withNeedle = longDeck() + "\n# Needle\n\n- The needle is here.\n"
        val before = renderSettled(deckState(withNeedle), "editor_workspace_before_find")

        val searched = deckState(withNeedle).apply {
            findQuery = "needle is here"
            findNext()
        }
        val after = renderSettled(searched, "editor_workspace_after_find")

        val difference = paneDifference(before, after)
        assertTrue(
            difference > 0.05,
            "the source pane is ${"%.1f".format(difference * 100)}% different after finding a " +
                "match on the last slide — the match was never revealed"
        )
    }

    private companion object {
        /** ~60 fps for half a second: comfortably longer than the reveal animation. */
        const val FRAME_NANOS = 16_000_000L
        const val FRAMES = 30
    }
}
