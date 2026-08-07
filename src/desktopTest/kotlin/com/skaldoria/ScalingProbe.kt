package com.skaldoria

import com.skaldoria.core.parser.MarkdownSlideParser
import com.skaldoria.theme.BuiltinThemes
import com.skaldoria.ui.editor.MarkdownVisualTransformation
import kotlin.test.Test

/**
 * How the per-keystroke cost grows with document length.
 *
 * `PerformanceProbe` answers *"what does a keystroke cost on a real deck?"* at one size.
 * This answers the different question **"where does it stop fitting in a frame?"**, which is what
 * decides whether this editor can host anything larger than a conference talk.
 *
 * The numbers are quoted in [`docs/EDITOR_SCALING_ANALYSIS.md`](../../../../../docs). That document
 * previously cited a sweep that lived in a throwaway file and was deleted after use, so its central
 * claim could not be reproduced from the tree — the gap this class closes.
 *
 * ```
 * ./gradlew desktopTest --tests "*ScalingProbe*" -i
 * ```
 */
class ScalingProbe {

    @Test
    fun `time the per-keystroke paths against document length`() {
        val theme = BuiltinThemes.SkaldoriaDark

        // Warm every path once at a representative size, so the first size measured is not paying
        // for compilation the later ones get for free.
        val warm = Bench.realisticDeck(60)
        repeat(200) {
            Bench.consume(MarkdownSlideParser.parse(warm))
            Bench.consume(MarkdownVisualTransformation.highlightMarkdown(warm, theme))
            Bench.consume(MarkdownSlideParser.extractFollowUpQuestions(warm))
        }

        println()
        println("ScalingProbe — the three passes a keystroke runs, versus document length")
        println("120 FPS is an 8.3 ms budget; 60 FPS is 16.7 ms.")

        for (slides in listOf(34, 68, 135, 270, 540, 1080)) {
            val deck = Bench.realisticDeck(slides)

            // A second deck of identical shape, so alternating them defeats the highlighter's
            // single-entry memo and measures the cold path a keystroke actually pays.
            val variant = deck.replaceFirst("# Section 1", "# Section Z")
            var flip = false

            println()
            println("  --- %,d lines (%,d chars) ---".format(deck.lines().size, deck.length))

            Bench.measure("  parse", 40) { MarkdownSlideParser.parse(deck) }
            Bench.measure("  highlight (cold)", 40) {
                flip = !flip
                MarkdownVisualTransformation.highlightMarkdown(if (flip) deck else variant, theme)
            }
            Bench.measure("  extractFollowUpQuestions", 40) {
                MarkdownSlideParser.extractFollowUpQuestions(deck)
            }
        }
        println()
    }
}
