package com.skaldoria.core.parser

import com.skaldoria.markdown.models.SlideElement
import com.skaldoria.markdown.parser.MarkdownSlideParser
import com.skaldoria.theme.BuiltinThemes
import com.skaldoria.ui.editor.MarkdownVisualTransformation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Cross-checks the parser and the editor's highlighter on the line rules that are **supposed to
 * agree**, and pins the three places they currently do not.
 *
 * The useful question is not "do these two rules look similar" but **"are they asking the same
 * question and getting different answers?"** Most pairs are not: `SLIDE_HEADING` asks *does this
 * start a slide*, the highlighter asks *what colour is this line*, and `### Sub` is correctly
 * coloured and correctly not a slide break. Those are excluded here deliberately.
 *
 * What remains is three rules where both sides *are* asking the same question and answering
 * differently — see `docs/MARKDOWN_UNIFICATION_PLAN.md`, Phase E.
 *
 * **`DEFECT` assertions encode current, wrong behaviour** so a fix has to update them rather than
 * change the editor silently. None of these are the dangerous class: unlike the fence bug, whose
 * state carried forward and corrupted the rest of the document, each of these is one wrongly
 * styled line.
 */
class LineRuleAgreementTest {

    private val theme = BuiltinThemes.SkaldoriaDark

    // ---------------------------------------------------------------------
    // Horizontal rules
    // ---------------------------------------------------------------------

    @Test
    fun `the dash rule splits the deck and is styled - the agreeing case`() {
        val markdown = "# One\n\nAlpha.\n\n---\n\n# Two\n\nBeta.\n"
        assertEquals(2, MarkdownSlideParser.parse(markdown).size, "`---` splits")
        assertTrue(isStyled(markdown, lineIndex = 4), "`---` is styled")
    }

    @Test
    fun `every thematic break form both splits the deck and is styled`() {
        // Was a DEFECT: only an exact `---` was styled, so `***` and `___` broke a slide with no
        // visual indication. Both sides now defer to ThematicBreakRules.
        for (rule in listOf("---", "***", "___", "----", "* * *")) {
            val markdown = "# One\n\nAlpha.\n\n$rule\n\n# Two\n\nBeta.\n"

            val splits = MarkdownSlideParser.parse(markdown).size == 2
            val styled = isStyled(markdown, lineIndex = 4)

            assertEquals(
                splits, styled,
                "`$rule`: parser splits=$splits but editor styles=$styled — they must agree"
            )
        }
    }

    // ---------------------------------------------------------------------
    // Table rows
    // ---------------------------------------------------------------------

    @Test
    fun `a fully piped table row is a table to both - the agreeing case`() {
        val markdown = "# T\n\n| a | b |\n| - | - |\n| 1 | 2 |\n"
        assertTrue(
            MarkdownSlideParser.parse(markdown).single().elements.any { it is SlideElement.Table },
            "parser builds a table"
        )
        assertTrue(isStyled(markdown, lineIndex = 2), "the row is styled")
    }

    /**
     * Not a divergence — a **shared gap**, which is why it belongs here rather than in the DEFECT
     * list. Tables written without outer pipes are ordinary GFM and neither side supports them.
     *
     * `TableRule` matches only the separator line, through its `contains("-|-")` clause; the
     * header and body rows fail both clauses and stay prose, so no table is assembled. The
     * highlighter independently refuses all three, needing a leading *and* trailing pipe. The two
     * agree, and both are equally wrong — so fixing this is a feature, not a convergence task.
     */
    @Test
    fun `a table written without outer pipes is supported by neither side`() {
        val markdown = "# T\n\na | b\n---|---\n1 | 2\n"

        assertTrue(
            MarkdownSlideParser.parse(markdown).single().elements.none { it is SlideElement.Table },
            "parser assembles no table"
        )
        for (line in 2..4) {
            assertTrue(!isStyled(markdown, lineIndex = line), "line $line is not styled as a table")
        }
    }

    // ---------------------------------------------------------------------
    // Math blocks
    // ---------------------------------------------------------------------

    @Test
    fun `a multi-line math block is math to both, body included`() {
        // Was a DEFECT: the highlighter tracked no `$$` state, so it styled the two delimiters and
        // left the formula between them as ordinary prose.
        val markdown = "# M\n\n$$\n\\Delta t = t_1 - t_0\n$$\n\nAfter the block.\n"

        assertTrue(
            MarkdownSlideParser.parse(markdown).single().elements.any { it is SlideElement.MathFormula },
            "parser builds one math element from the block"
        )
        for (line in 2..4) {
            assertTrue(isStyled(markdown, lineIndex = line), "line $line is part of the math block")
        }
        assertTrue(
            !isStyled(markdown, lineIndex = 6),
            "the block closed — prose after it is not math"
        )
    }

    @Test
    fun `a single-line math formula does not open a block`() {
        val markdown = "# M\n\n$$ x = 1 $$\n\nOrdinary prose.\n"

        assertTrue(isStyled(markdown, lineIndex = 2), "the formula is styled")
        assertTrue(
            !isStyled(markdown, lineIndex = 4),
            "a one-line `$$ … $$` must not leave a block open and swallow what follows"
        )
    }

    // ---------------------------------------------------------------------

    /** Whether the highlighter applied any span at all to [lineIndex]. */
    private fun isStyled(text: String, lineIndex: Int): Boolean {
        val annotated = MarkdownVisualTransformation.highlightMarkdown(text, theme)

        var start = 0
        for (i in 0 until lineIndex) start += text.lines()[i].length + 1
        val end = start + text.lines()[lineIndex].length

        return annotated.spanStyles.any { it.start < end && it.end > start }
    }
}
