package com.skaldoria.core.parser

import androidx.compose.ui.text.font.FontFamily
import com.skaldoria.core.models.SlideElement
import com.skaldoria.theme.BuiltinThemes
import com.skaldoria.ui.editor.MarkdownVisualTransformation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Asserts that the parser and the editor's highlighter agree about fenced blocks.
 *
 * Was `FenceLexerDivergenceTest`, which pinned the opposite: four places each decided
 * independently what opened a fence, and two shipped defects fell out of the disagreement —
 * tilde fences invisible everywhere, and an unusual info string losing its language (falling back
 * to `kotlin` in `SectionContext.flushCode`).
 *
 * Phase B replaced all four with [FenceRules]. This test is what proves it, and what would catch
 * a fifth private implementation being added later.
 *
 * It lives in the app module rather than `:markdown-core` because it has to reach the
 * Compose-dependent [MarkdownVisualTransformation]. That is the whole point — the assertion is
 * cross-module by nature.
 */
class FenceLexerAgreementTest {

    // ---------------------------------------------------------------------
    // FenceRules — the single authority
    // ---------------------------------------------------------------------

    @Test
    fun `opens backtick fences carrying any info string`() {
        assertEquals("", FenceRules.openingFence("```")?.language)
        assertEquals("kotlin", FenceRules.openingFence("```kotlin")?.language)
        assertEquals("diff-highlight", FenceRules.openingFence("```diff-highlight")?.language)
        // Previously rejected by the anchored regex, which is how the language got lost.
        assertEquals("js", FenceRules.openingFence("```js {highlight=2}")?.language)
        assertEquals("python", FenceRules.openingFence("""```python title="demo.py"""")?.language)
    }

    @Test
    fun `opens tilde fences`() {
        assertEquals("", FenceRules.openingFence("~~~")?.language)
        assertEquals("python", FenceRules.openingFence("~~~python")?.language)
    }

    @Test
    fun `keeps the line-highlight extension working`() {
        val fence = FenceRules.openingFence("```kotlin [1,3-5]")
        assertEquals("kotlin", fence?.language)
        assertEquals(setOf(1, 3, 4, 5), fence?.highlights)

        // A bare range with no language is a highlight spec, not a language called "[1,3]".
        assertEquals("", FenceRules.openingFence("``` [1,3]")?.language)
    }

    @Test
    fun `requires at least three markers`() {
        assertEquals(null, FenceRules.openingFence("``"))
        assertEquals(null, FenceRules.openingFence("~~"))
        assertEquals(null, FenceRules.openingFence("plain prose"))
    }

    @Test
    fun `a closing fence must match the marker, its length, and carry no info string`() {
        val open = FenceRules.openingFence("````kotlin")!!

        assertTrue(FenceRules.closes("````", open), "same marker, same length")
        assertTrue(FenceRules.closes("`````", open), "longer is allowed")
        assertTrue(!FenceRules.closes("```", open), "shorter must not close")
        assertTrue(!FenceRules.closes("~~~~", open), "a tilde run must not close a backtick fence")
        assertTrue(!FenceRules.closes("````js", open), "a closing fence carries no info string")
    }

    // ---------------------------------------------------------------------
    // The two former defects, now fixed
    // ---------------------------------------------------------------------

    @Test
    fun `an unusual info string keeps its language`() {
        val block = MarkdownSlideParser.parse(MIXED_FENCES)
            .single().elements.filterIsInstance<SlideElement.CodeBlock>()
            .first { it.code.contains("const a = 1") }

        // Was "kotlin": the info string failed to parse, language came through blank, and
        // SectionContext.flushCode defaults blank to "kotlin".
        assertEquals("js", block.language)
    }

    @Test
    fun `tilde fences are code to both the parser and the highlighter`() {
        val markdown = "# Slide\n\n~~~python\nprint(\"tilde\")\n~~~\n"

        val parsedAsCode = MarkdownSlideParser.parse(markdown)
            .single().elements.filterIsInstance<SlideElement.CodeBlock>()
            .any { it.code.contains("""print("tilde")""") }
        assertTrue(parsedAsCode, "parser must treat a tilde block as code")

        // Line 3 is the `print(...)` body.
        assertTrue(
            codeLinesPerHighlighter(markdown).contains(3),
            "highlighter must style a tilde block as code"
        )
    }

    // ---------------------------------------------------------------------
    // Agreement — the property that replaces the old divergence
    // ---------------------------------------------------------------------

    @Test
    fun `parser and highlighter agree on every fence style`() {
        val markdown = """
            # Agreement

            ```js {highlight=2}
            const a = 1
            ```

            Prose between fences.

            ~~~python
            print("tilde")
            ~~~

            ````markdown
            ```
            nested, and must not close the outer fence
            ```
            ````

            Final prose.
        """.trimIndent()

        val highlighterCode = codeLinesPerHighlighter(markdown)
        val lines = markdown.lines()

        // Walk the shared authority to get the parser's view, then require the highlighter to
        // match it line for line — including the fence markers themselves, which it styles.
        var open: FenceInfo? = null
        for ((index, raw) in lines.withIndex()) {
            val line = raw.trim()
            val current = open
            val isMarker: Boolean
            if (current != null) {
                isMarker = FenceRules.closes(line, current)
                if (isMarker) open = null
            } else {
                val opening = FenceRules.openingFence(line)
                isMarker = opening != null
                if (opening != null) open = opening
            }

            val shouldBeCode = isMarker || open != null
            assertEquals(
                shouldBeCode,
                index in highlighterCode,
                "line $index disagrees: \"${raw.take(40)}\" — parser says code=$shouldBeCode"
            )
        }
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
