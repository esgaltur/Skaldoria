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
 * EDT-4 driven the way a user drives it: **state changed while the composition is already live**.
 *
 * `EditorWorkspaceRenderingTest` builds a state, mutates it, and *then* renders it into a fresh
 * scene. Every reveal it tests therefore arrives before the first composition, when
 * `handledRevealToken` is still its initial value and the layout has never been measured. That
 * is not the situation the user is in: they have an editor on screen, and the reveal arrives
 * into a composition that is already running, already measured, and already holding a
 * `handledRevealToken` from a previous reveal.
 *
 * These cases keep one scene open and advance it, so each `findNext()` lands in a live
 * composition — the only configuration that can reproduce a stuck viewport.
 */
@OptIn(ExperimentalComposeUiApi::class)
class FindRevealScrollTest : PresentationStateTestBase() {

    @BeforeTest
    fun requireDisplay() = RenderEnvironment.requireDisplay()

    private val width = 1600
    private val height = 900

    private val paneLeft = 40
    private val paneTop = 170
    private val paneRight = 680
    private val paneBottom = 750

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

    /** 40 slides of filler, with a needle on slides 10, 25 and 39 so "next" genuinely travels. */
    private fun deckWithThreeNeedles(): String = buildString {
        for (slide in 1..40) {
            appendLine("# Slide $slide")
            appendLine()
            repeat(8) { appendLine("- A reasonably long bullet number $it on slide $slide") }
            if (slide == 10 || slide == 25 || slide == 39) appendLine("- The needle is here.")
            appendLine()
            appendLine("---")
            appendLine()
        }
    }

    /**
     * Drives one long-lived scene, capturing the source pane after each step.
     *
     * @param steps actions applied to the state between captures, in order.
     */
    private fun filmOf(
        state: PresentationState,
        label: String,
        steps: List<Pair<String, () -> Unit>>
    ): List<Pair<String, BufferedImage>> {
        val scene = ImageComposeScene(width = width, height = height, density = Density(1f)) {
            EditorWorkspace(state = state)
        }
        val frames = mutableListOf<Pair<String, BufferedImage>>()
        var nanos = 0L
        try {
            fun settle(name: String) {
                var image = scene.render(nanos)
                repeat(FRAMES) {
                    nanos += FRAME_NANOS
                    image = scene.render(nanos)
                }
                File("build/render-check").mkdirs()
                val png = image.encodeToData()?.bytes ?: error("could not encode $name")
                File("build/render-check/${label}_$name.png").writeBytes(png)
                frames += name to (javax.imageio.ImageIO.read(png.inputStream())
                    ?: error("could not decode $name"))
            }

            settle("00_initial")
            for ((index, step) in steps.withIndex()) {
                step.second()
                settle("%02d_%s".format(index + 1, step.first))
            }
        } finally {
            scene.close()
        }
        return frames
    }

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

    /**
     * The reported defect, driven live: open find, type, then press next twice.
     *
     * Each press moves to a match on a different slide, so a working reveal must repaint the
     * pane every time. A pane that is identical between two presses is the stuck viewport.
     */
    @Test
    fun `each next match scrolls the pane in a live composition`() {
        val state = deckState(deckWithThreeNeedles())

        val frames = filmOf(
            state,
            "find_live",
            listOf(
                "open_and_type" to {
                    state.toggleFind()
                    state.updateFindQuery("the needle is here")
                },
                "next_1" to { state.findNext() },
                "next_2" to { state.findNext() },
                "next_3" to { state.findNext() }
            )
        )

        val byName = frames.toMap()
        val moves = listOf(
            "open_and_type" to "next_1",
            "next_1" to "next_2",
            "next_2" to "next_3"
        ).map { (from, to) ->
            val a = byName.entries.first { it.key.endsWith(from) }.value
            val b = byName.entries.first { it.key.endsWith(to) }.value
            "$from -> $to" to paneDifference(a, b)
        }

        val stuck = moves.filter { it.second <= 0.02 }
        assertTrue(
            stuck.isEmpty(),
            "the source pane did not move for: " +
                stuck.joinToString { "${it.first} (${"%.1f".format(it.second * 100)}%)" } +
                " — all steps: " + moves.joinToString { "${it.first}=${"%.1f".format(it.second * 100)}%" }
        )
    }

    private companion object {
        const val FRAME_NANOS = 16_000_000L
        const val FRAMES = 30
    }
}
