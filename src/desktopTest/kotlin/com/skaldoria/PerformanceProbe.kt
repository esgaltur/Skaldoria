package com.skaldoria

import com.skaldoria.core.deck.DeckDocument
import com.skaldoria.core.document.SlideSourceLocator
import com.skaldoria.core.models.DeckProject
import com.skaldoria.core.models.SlideFileEntry
import com.skaldoria.core.parser.MarkdownSlideParser
import com.skaldoria.theme.BuiltinThemes
import com.skaldoria.ui.editor.MarkdownVisualTransformation
import kotlin.test.Test
import kotlin.time.Duration
import kotlin.time.measureTime

/**
 * Times the paths that run on **every keystroke**, so a claim about performance is a number.
 *
 * Not an assertion suite: thresholds tuned on one machine become CI flakes, and the project
 * has already learned what a test that measures the wrong thing costs. This prints, in the
 * style of `RenderAllProbe`, and the numbers are quoted in `docs/PERFORMANCE_BASELINE.md`.
 *
 * Run it alone:
 * ```
 * ./gradlew desktopTest --tests "*PerformanceProbe*" -i
 * ```
 */
class PerformanceProbe {

    /** A deck the size of a real conference talk: 60 slides, ~1500 lines, some code. */
    private fun realisticDeck(slides: Int = 60): String = buildString {
        for (slide in 1..slides) {
            appendLine("# Section $slide")
            appendLine()
            appendLine("Some introductory prose for section $slide that runs a little long.")
            appendLine()
            repeat(6) { appendLine("- A bullet point number $it explaining part of section $slide") }
            appendLine()
            if (slide % 4 == 0) {
                appendLine("```kotlin")
                appendLine("fun handler$slide(input: String): Int {")
                appendLine("    val parsed = input.trim().toIntOrNull() ?: return 0")
                appendLine("    return if (parsed > 0) parsed else -parsed")
                appendLine("}")
                appendLine("```")
                appendLine()
            }
            appendLine("---")
            appendLine()
        }
    }

    private fun report(label: String, iterations: Int, elapsed: Duration) {
        val perOp = elapsed.inWholeMicroseconds.toDouble() / iterations
        val perOpText = if (perOp >= 1000) "%.2f ms".format(perOp / 1000) else "%.0f us".format(perOp)
        println("  %-52s %10s / call   (%d calls in %s)".format(label, perOpText, iterations, elapsed))
    }

    private inline fun bench(label: String, iterations: Int, block: () -> Unit) {
        repeat(iterations / 2 + 1) { block() } // warm the JIT
        report(label, iterations, measureTime { repeat(iterations) { block() } })
    }

    @Test
    fun `time the per-keystroke paths`() {
        val deck = realisticDeck()
        val slides = MarkdownSlideParser.parse(deck)
        val theme = BuiltinThemes.SkaldoriaDark

        println()
        println("PerformanceProbe — ${deck.lines().size} lines, ${slides.size} slides")
        println("A keystroke in the editor runs: parse + reconcile + highlight, at minimum.")
        println()

        bench("MarkdownSlideParser.parse (whole deck)", 200) {
            MarkdownSlideParser.parse(deck)
        }

        bench("MarkdownVisualTransformation.highlightMarkdown", 200) {
            MarkdownVisualTransformation.highlightMarkdown(deck, theme)
        }

        bench("MarkdownSlideParser.extractFollowUpQuestions", 200) {
            MarkdownSlideParser.extractFollowUpQuestions(deck)
        }

        println()
        println("Caret paths — these run on every cursor move (AUT-05):")
        bench("SlideSourceLocator.slideIndexAtOffset", 2000) {
            SlideSourceLocator.slideIndexAtOffset(deck, slides, deck.length / 2)
        }
        bench("SlideSourceLocator.offsetOfSlideIndex", 2000) {
            SlideSourceLocator.offsetOfSlideIndex(deck, slides, slides.size / 2)
        }

        println()
        println("Project mode — reading the editor's own text:")
        val files = (1..20).map { index ->
            SlideFileEntry(
                relativePath = "slides/$index.md",
                absolutePath = "/deck/slides/$index.md",
                content = realisticDeck(slides = 3)
            )
        }
        val project = DeckProject(
            name = "Probe",
            rootDir = "/deck",
            manifestPath = null,
            slideFiles = files.toMutableList()
        )
        bench("DeckProject.slideOwnerFileIndices (20 files)", 100) {
            project.slideOwnerFileIndices()
        }

        // The path that actually runs: `currentEditorText` reaches the map through `fileFor`,
        // and is read on every composition. This is what the PRF-5 cache changes; the raw
        // function above is unchanged, because the fix was to stop calling it repeatedly.
        val document = DeckDocument(project.compileCombinedMarkdown()) { }
        document.adopt(project)
        bench("DeckDocument.editorTextFor (cached map)", 2000) {
            document.editorTextFor(30)
        }
        println()
    }
}
