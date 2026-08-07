package com.skaldoria.markdown.parser

import com.skaldoria.markdown.models.SlideElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * F-17: the rule chain's **ordering**, asserted directly.
 *
 * The old parse loop's correctness depended on the order of a nine-branch `if` chain that was
 * nowhere stated. Now the order is a list, and these tests pin the relationships that are
 * load-bearing — so a future reader who reorders the list for tidiness gets a failure instead
 * of a subtly mis-parsed deck.
 */
class BlockRuleOrderTest {

    private fun positionOf(rule: BlockRule) = BLOCK_RULES.indexOf(rule)

    private fun assertBefore(first: BlockRule, second: BlockRule, why: String) {
        assertTrue(
            positionOf(first) in 0 until positionOf(second),
            "${first::class.simpleName} must precede ${second::class.simpleName}: $why"
        )
    }

    @Test
    fun `every rule is registered exactly once`() {
        assertEquals(BLOCK_RULES.distinct().size, BLOCK_RULES.size)
    }

    @Test
    fun `the paragraph fallback is last`() {
        assertEquals(
            ParagraphRule,
            BLOCK_RULES.last(),
            "ParagraphRule accepts any non-blank line, so anything after it is unreachable"
        )
    }

    @Test
    fun `a metric is recognised before it can be mistaken for prose`() =
        assertBefore(MetricRule, ParagraphRule, "otherwise every KPI line renders as body text")

    @Test
    fun `metadata comments are skipped before they can be mistaken for prose`() =
        assertBefore(
            HtmlCommentRule, ParagraphRule,
            "otherwise parking-lot directives render as literal <!-- ... --> on the slide"
        )

    @Test
    fun `table rows are recognised before headings`() =
        assertBefore(TableRule, HeadingRule, "a separator row would otherwise be read as a heading")

    @Test
    fun `an open fence wins over the block rules that would otherwise claim its lines`() {
        assertBefore(InCodeBlockRule, HeadingRule, "a '# comment' inside code is code")
        assertBefore(InCodeBlockRule, ListRule, "a '- item' inside code is code")
        assertBefore(InCodeBlockRule, ParagraphRule, "code is not prose")
        assertBefore(CodeFenceRule, InCodeBlockRule, "the closing fence must close, not accumulate")
    }

    @Test
    fun `an open math block wins over the fence opener`() =
        assertBefore(InMathBlockRule, MathFenceRule, "the closing $$ must close, not reopen")

    // ---- the orderings, demonstrated end to end ----

    @Test
    fun `markdown inside a code fence is preserved verbatim`() {
        val slide = MarkdownSlideParser.parse(
            """
            ## Example
            ```markdown
            # Not a heading
            - not a bullet
            > not a quote
            | not | a table |
            ```
            """.trimIndent()
        ).single()

        val code = slide.elements.filterIsInstance<SlideElement.CodeBlock>().single()
        assertTrue(code.code.contains("# Not a heading"))
        assertTrue(code.code.contains("- not a bullet"))
        assertTrue(code.code.contains("| not | a table |"))
        assertTrue(
            slide.elements.none { it is SlideElement.BulletList },
            "a bullet inside a fence must not become a list"
        )
    }

    @Test
    fun `code indentation survives the trim used for matching`() {
        val slide = MarkdownSlideParser.parse(
            "## Code\n```kotlin\nfun main() {\n    println(\"indented\")\n}\n```"
        ).single()

        val code = slide.elements.filterIsInstance<SlideElement.CodeBlock>().single()
        assertTrue(code.code.contains("    println"), "leading whitespace must be preserved: ${code.code}")
    }

    @Test
    fun `a metric only wins as the first element on its slide`() {
        val slide = MarkdownSlideParser.parse(
            """
            ## Results

            - context first

            42% Growth
            """.trimIndent()
        ).single()

        assertTrue(
            slide.elements.none { it is SlideElement.Metric },
            "a figure after other content must not hijack the layout"
        )
    }

    @Test
    fun `a blank line closes an accumulating list`() {
        val slide = MarkdownSlideParser.parse(
            """
            ## Two lists

            - a
            - b

            Some prose.

            - c
            """.trimIndent()
        ).single()

        val lists = slide.elements.filterIsInstance<SlideElement.BulletList>()
        assertEquals(2, lists.size, "prose between the lists must split them")
        assertEquals(listOf("a", "b"), lists[0].items)
        assertEquals(listOf("c"), lists[1].items)
    }

    @Test
    fun `a quote keeps its attribution`() {
        val slide = MarkdownSlideParser.parse(
            "> Simplicity is prerequisite for reliability.\n> -- Edsger W. Dijkstra"
        ).single()

        val quote = slide.elements.filterIsInstance<SlideElement.Quote>().single()
        assertEquals("Simplicity is prerequisite for reliability.", quote.quote)
        assertEquals("Edsger W. Dijkstra", quote.author)
    }

    @Test
    fun `a single piped line is prose, not a one-row table`() {
        val slide = MarkdownSlideParser.parse("## H\n\nuse the | pipe operator").single()
        assertTrue(slide.elements.none { it is SlideElement.Table })
    }
}
