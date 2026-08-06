package com.skaldoria.core.parser

import androidx.compose.ui.text.font.FontFamily
import com.skaldoria.core.models.SlideElement
import com.skaldoria.theme.BuiltinThemes
import com.skaldoria.ui.editor.MarkdownVisualTransformation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins how each markdown lexer decides what is a code fence — **including where they are wrong**.
 *
 * Skaldoria lexes markdown in three independent places (`MarkdownSlideParser`,
 * `MarkdownVisualTransformation`, `InlineMarkdown`) and nothing in the suite compared them. This
 * test exists because that gap hid live defects; see `docs/MARKDOWN_UNIFICATION_PLAN.md`.
 *
 * **Assertions marked `DEFECT` encode current, incorrect behaviour.** They are asserted rather
 * than fixed so the behaviour is visible, and so Phase 4 has to update them deliberately rather
 * than silently. Do not "fix" a DEFECT assertion on its own — fix the lexer, then update it here.
 */
class FenceLexerDivergenceTest {

    // ---------------------------------------------------------------------
    // What `MarkdownSlideParser` accepts as a fence opening
    // ---------------------------------------------------------------------

    @Test
    fun `parser accepts plain, language, hyphenated and line-range fences`() {
        assertTrue(MarkdownSlideParser.CODE_FENCE_START.matches("```"))
        assertTrue(MarkdownSlideParser.CODE_FENCE_START.matches("```kotlin"))
        assertTrue(MarkdownSlideParser.CODE_FENCE_START.matches("```kotlin [1,3-5]"))
        // The character class is [a-zA-Z0-9_-], so hyphenated language tags do match.
        assertTrue(MarkdownSlideParser.CODE_FENCE_START.matches("```diff-highlight"))
    }

    @Test
    fun `DEFECT - parser rejects fences carrying any other info string`() {
        // CommonMark allows arbitrary text in the info string, and these are ordinary markdown
        // that every other tool accepts. `MarkdownSlideParser` does not.
        assertTrue(!MarkdownSlideParser.CODE_FENCE_START.matches("```js {highlight=2}"))
        assertTrue(!MarkdownSlideParser.CODE_FENCE_START.matches("""```python title="demo.py""""))
    }

    @Test
    fun `DEFECT - neither the parser nor the highlighter recognises tilde fences`() {
        assertTrue(!MarkdownSlideParser.CODE_FENCE_START.matches("~~~"))
        assertTrue(!MarkdownSlideParser.CODE_FENCE_START.matches("~~~python"))
        assertEquals(
            emptySet(),
            codeLinesPerHighlighter("~~~python\nprint(1)\n~~~"),
            "highlighter styles no part of a tilde fence as code"
        )
    }

    // ---------------------------------------------------------------------
    // Same line, opposite answers
    // ---------------------------------------------------------------------

    @Test
    fun `parser and highlighter disagree about what opens a fence`() {
        val line = "```js {highlight=2}"
        assertTrue(
            !MarkdownSlideParser.CODE_FENCE_START.matches(line),
            "parser does not open a fence here"
        )
        assertTrue(
            codeLinesPerHighlighter("$line\nconst a = 1\n```").contains(1),
            "highlighter does open a fence here"
        )
    }

    // ---------------------------------------------------------------------
    // The observable consequence
    // ---------------------------------------------------------------------

    /**
     * Content survives an unmatched info string — `BlockRules` recovers where the bare regex
     * suggests it would not. Pinned because it is the reassuring half of the defect and a
     * refactor must not lose it.
     */
    @Test
    fun `content after an unmatched info string is preserved`() {
        val slides = MarkdownSlideParser.parse(MIXED_FENCES)
        val prose = slides.single().elements.filterIsInstance<SlideElement.Text>()
        assertTrue(
            prose.any { it.content.contains("This prose is AFTER the code block") },
            "prose between two fences must survive"
        )
    }

    /**
     * DEFECT: the language tag is lost and mis-attributed.
     *
     * The first block is JavaScript, written ```` ```js {highlight=2} ````. Because the opening
     * line does not match, the `js` tag is dropped and the block is reported as `kotlin` — so it
     * renders with Kotlin syntax colouring on the slide. Any `[1,3-5]` line-range highlighting on
     * such a fence is silently discarded for the same reason.
     */
    @Test
    fun `DEFECT - unmatched info string loses the language tag`() {
        val blocks = MarkdownSlideParser.parse(MIXED_FENCES)
            .single().elements.filterIsInstance<SlideElement.CodeBlock>()

        val jsBlock = blocks.first { it.code.contains("const a = 1") }
        assertEquals(
            "kotlin", jsBlock.language,
            "DEFECT pinned: a JavaScript block is reported as kotlin. If this fails, the info " +
                "string is being read correctly now — update this assertion and the plan doc."
        )
        assertTrue(jsBlock.highlightedLines.isEmpty(), "line ranges are dropped too")
    }

    // ---------------------------------------------------------------------

    private companion object {
        val MIXED_FENCES = """
            # Slide One

            ```js {highlight=2}
            const a = 1
            ```

            This prose is AFTER the code block and belongs to the slide.

            ```kotlin
            fun x() = 1
            ```
        """.trimIndent()
    }

    /** Line indices the highlighter styled monospace — its notion of "this is code". */
    private fun codeLinesPerHighlighter(text: String): Set<Int> {
        val annotated = MarkdownVisualTransformation.highlightMarkdown(text, BuiltinThemes.SkaldoriaDark)
        val lineStarts = mutableListOf(0)
        text.forEachIndexed { i, c -> if (c == '\n') lineStarts.add(i + 1) }
        fun lineOf(offset: Int): Int {
            var result = 0
            for ((idx, start) in lineStarts.withIndex()) if (offset >= start) result = idx
            return result
        }
        val lines = mutableSetOf<Int>()
        for (span in annotated.spanStyles) {
            if (span.item.fontFamily == FontFamily.Monospace) {
                for (l in lineOf(span.start)..lineOf(maxOf(span.start, span.end - 1))) lines.add(l)
            }
        }
        return lines
    }
}
