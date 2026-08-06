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
        println("  %-52s %10s / call   (best of 3 x %d)".format(label, perOpText, iterations))
    }

    /**
     * Sink that keeps every benchmarked result observable.
     *
     * PRF-6: without it, this probe discarded every return value, and a JIT that can prove a
     * result is never used is free to elide the work that produced it. That makes a discarding
     * benchmark a *lower bound* rather than a cost — and the effect is not small: the highlighter
     * measured 528 µs when its result escaped nowhere and ~1.1 ms once it did.
     *
     * `identityHashCode` is deliberate. It is cheap and constant-time, and it forces the object
     * to have actually been allocated — unlike `hashCode()`, which on a `List<Slide>` would walk
     * the whole structure and time the sink instead of the subject.
     */
    private var blackhole: Int = 0

    private fun consume(value: Any?) {
        blackhole += System.identityHashCode(value)
    }

    /**
     * Times [block], reporting the **fastest** of [rounds] measured passes.
     *
     * PRF-6: a single timed pass is not reproducible here. Back-to-back runs of an unchanged
     * binary produced 579 µs and 1.27 ms for the same highlighter benchmark — a 2.2x spread,
     * enough to invent regressions that do not exist and to hide ones that do. Three separate
     * hypotheses were chased against that noise before it was recognised as noise.
     *
     * Minimum, not mean: every source of interference here (JIT recompilation, GC pauses, OS
     * scheduling) makes a pass *slower*, never faster, so the fastest pass is the closest estimate
     * of the work itself. It is still an estimate — this is a print-probe, not JMH, and it cannot
     * control compilation tiers or run each subject in a fresh JVM.
     */
    private inline fun bench(label: String, iterations: Int, rounds: Int = 3, block: () -> Any?) {
        repeat(iterations / 2 + 1) { consume(block()) } // warm the JIT

        var best = Duration.INFINITE
        repeat(rounds) {
            val elapsed = measureTime { repeat(iterations) { consume(block()) } }
            if (elapsed < best) best = elapsed
        }
        report(label, iterations, best)
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

        // PRF-6: the highlighter memoises on its inputs, so these two numbers are different
        // questions and both matter. Repeating one text measures only the cache, which is the
        // caret-move and selection-drag path; a keystroke changes the text and pays in full.
        bench("highlightMarkdown (cache hit — caret move)", 200) {
            MarkdownVisualTransformation.highlightMarkdown(deck, theme)
        }

        // Two decks of identical shape, alternated, so the single-entry memo always misses.
        val deckVariant = deck.replaceFirst("# Section 1", "# Section Z")
        var flip = false
        bench("highlightMarkdown (cold — keystroke)", 200) {
            flip = !flip
            MarkdownVisualTransformation.highlightMarkdown(if (flip) deck else deckVariant, theme)
        }

        // Consumes the result, so the JIT cannot discard work nothing observes. This probe
        // discards return values everywhere else, which makes every other figure here a lower
        // bound rather than a cost. See PERFORMANCE_BASELINE.md, "What was not measured".
        var sink = 0
        var flipSink = false
        bench("highlightMarkdown (cold, result consumed)", 200) {
            flipSink = !flipSink
            sink += MarkdownVisualTransformation
                .highlightMarkdown(if (flipSink) deck else deckVariant, theme)
                .spanStyles.size
        }
        check(sink > 0)

        // PRF-6: the guard short-circuits documents with no follow-up items, which is the common
        // case. Both are reported so the guard is not mistaken for a general speed-up.
        bench("extractFollowUpQuestions (no follow-ups — guarded)", 200) {
            MarkdownSlideParser.extractFollowUpQuestions(deck)
        }

        val deckWithFollowUps = deck + "\n<!-- parking-lot: [ ] Why is it slow? | id:probe -->\n"
        bench("extractFollowUpQuestions (has follow-ups — full scan)", 200) {
            MarkdownSlideParser.extractFollowUpQuestions(deckWithFollowUps)
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
