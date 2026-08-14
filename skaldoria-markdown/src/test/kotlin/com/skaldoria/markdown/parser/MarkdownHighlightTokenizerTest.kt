package com.skaldoria.markdown.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The tokenizer is the layer the three editors used to write for themselves. Most of what is
 * pinned here is behaviour Presentation already had and the CV editor did not — those cases are
 * marked, because they are the reason this file exists rather than nice-to-haves.
 */
class MarkdownHighlightTokenizerTest {

    private fun kindsOn(text: String, line: Int, frontMatter: Boolean = false): Set<MarkdownTokenKind> {
        val lines = text.split("\n")
        var start = 0
        for (i in 0 until line) start += lines[i].length + 1
        val end = start + lines[line].length

        return MarkdownHighlightTokenizer.tokenize(text, frontMatter)
            .filter { it.start < end && it.end > start }
            .map { it.kind }
            .toSet()
    }

    // ---------------------------------------------------------------------
    // Fenced code — the CV editor tracked none of this
    // ---------------------------------------------------------------------

    @Test
    fun `markdown syntax inside a fence is code, not markdown`() {
        val text = "Intro\n\n```bash\n# not a heading\n**not bold**\n```\n"

        assertEquals(setOf(MarkdownTokenKind.FenceMarker), kindsOn(text, 2))
        assertEquals(
            setOf(MarkdownTokenKind.CodeText, MarkdownTokenKind.CodeComment),
            kindsOn(text, 3),
            "`#` inside a fence is a comment, never a heading"
        )
        assertFalse(
            MarkdownTokenKind.Bold in kindsOn(text, 4),
            "`**` inside a fence must not bold"
        )
    }

    @Test
    fun `a tilde fence is a fence`() {
        val text = "~~~\n# inside\n~~~\n"
        assertEquals(setOf(MarkdownTokenKind.FenceMarker), kindsOn(text, 0))
        assertTrue(MarkdownTokenKind.CodeText in kindsOn(text, 1))
    }

    @Test
    fun `a backtick run inside a tilde block does not close it`() {
        val text = "~~~\n```\nstill code\n~~~\n"
        assertTrue(
            MarkdownTokenKind.CodeText in kindsOn(text, 2),
            "only a matching marker of sufficient length closes a fence"
        )
    }

    // ---------------------------------------------------------------------
    // Headings
    // ---------------------------------------------------------------------

    @Test
    fun `an indented heading is a heading`() {
        // The CV editor anchored `#` to column 0 with a MULTILINE regex, so this was prose there
        // and a heading everywhere else.
        assertEquals(setOf(MarkdownTokenKind.Heading), kindsOn("   ### Skills\n", line = 0))
    }

    @Test
    fun `a hashtag is not a heading`() {
        assertTrue(kindsOn("#hashtag prose\n", line = 0).isEmpty(), "CommonMark requires whitespace")
    }

    @Test
    fun `heading level is carried through`() {
        val levels = MarkdownHighlightTokenizer.tokenize("# a\n## b\n###### f\n")
            .filter { it.kind == MarkdownTokenKind.Heading }
            .map { it.level }
        assertEquals(listOf(1, 2, 6), levels)
    }

    // ---------------------------------------------------------------------
    // Inline emphasis
    // ---------------------------------------------------------------------

    @Test
    fun `bold is not also italic`() {
        val kinds = MarkdownHighlightTokenizer.tokenize("**bold**\n").map { it.kind }
        assertTrue(MarkdownTokenKind.Bold in kinds)
        assertFalse(MarkdownTokenKind.Italic in kinds, "the ** run must be stepped over")
    }

    @Test
    fun `italic survives a bold run earlier on the same line`() {
        // The CV editor's `Regex("\\*(.*?)\\*")` plus a startsWith("**") guard only worked when
        // the lazy match happened to land on a delimiter pair.
        val tokens = MarkdownHighlightTokenizer.tokenize("**bold** then *ital* end\n")
        val italic = tokens.single { it.kind == MarkdownTokenKind.Italic }
        assertEquals("*ital*", "**bold** then *ital* end\n".substring(italic.start, italic.end))
    }

    @Test
    fun `an unclosed emphasis marker does not run away`() {
        val tokens = MarkdownHighlightTokenizer.tokenize("a * dangling\n")
        assertTrue(tokens.none { it.kind == MarkdownTokenKind.Italic })
    }

    @Test
    fun `a link is one span covering text and target`() {
        val source = "Mail [me](mailto:a@b.c) today\n"
        val link = MarkdownHighlightTokenizer.tokenize(source).single { it.kind == MarkdownTokenKind.Link }
        assertEquals("[me](mailto:a@b.c)", source.substring(link.start, link.end))
    }

    @Test
    fun `bullet marker covers only the marker`() {
        val source = "- Item text\n"
        val bullet = MarkdownHighlightTokenizer.tokenize(source)
            .single { it.kind == MarkdownTokenKind.BulletMarker }
        assertEquals("- ", source.substring(bullet.start, bullet.end))
    }

    // ---------------------------------------------------------------------
    // Front matter — the CV dialect only
    // ---------------------------------------------------------------------

    @Test
    fun `front matter is one span and suppresses the thematic break reading`() {
        val text = "---\nname: Ada\n---\n\n# Ada\n"

        val tokens = MarkdownHighlightTokenizer.tokenize(text, frontMatter = true)
        val block = tokens.single { it.kind == MarkdownTokenKind.FrontMatter }
        assertEquals(0, block.start)
        assertEquals(text.indexOf("---\n\n") + 3, block.end, "covers through the closing delimiter")
        assertTrue(
            tokens.none { it.kind == MarkdownTokenKind.ThematicBreak },
            "a leading --- is metadata, not a rule"
        )
    }

    @Test
    fun `a delimiter with trailing whitespace still closes front matter`() {
        // The old regex demanded a literal `\n---`, so this parsed as metadata in
        // CvMarkdownAdapter while showing as unstyled text in the editor.
        val text = "---\nname: Ada\n--- \n\n# Ada\n"
        assertNotNull(
            MarkdownHighlightTokenizer.tokenize(text, frontMatter = true)
                .firstOrNull { it.kind == MarkdownTokenKind.FrontMatter }
        )
    }

    @Test
    fun `unclosed front matter yields no span rather than swallowing the document`() {
        val text = "---\nname: Ada\n\n# Ada\n"
        val tokens = MarkdownHighlightTokenizer.tokenize(text, frontMatter = true)
        assertTrue(tokens.none { it.kind == MarkdownTokenKind.FrontMatter })
        assertTrue(tokens.any { it.kind == MarkdownTokenKind.Heading }, "the rest still tokenizes")
    }

    @Test
    fun `without the CV dialect a leading rule stays a thematic break`() {
        val tokens = MarkdownHighlightTokenizer.tokenize("---\nname: Ada\n---\n")
        assertTrue(tokens.any { it.kind == MarkdownTokenKind.ThematicBreak })
        assertTrue(tokens.none { it.kind == MarkdownTokenKind.FrontMatter })
    }

    @Test
    fun `front matter agrees with the adapter on where it closes`() {
        val lines = "---\nname: Ada\n--- \n\n# Ada\n".split("\n")
        assertEquals(2, FrontMatterRules.closingLineIndex(lines))
        assertNull(FrontMatterRules.closingLineIndex("# Ada\n".split("\n")))
    }

    // ---------------------------------------------------------------------
    // Math and offsets
    // ---------------------------------------------------------------------

    @Test
    fun `a multi-line math block covers its body`() {
        val text = "$$\n\\Delta t\n$$\n\nAfter.\n"
        for (line in 0..2) {
            assertEquals(setOf(MarkdownTokenKind.MathBlock), kindsOn(text, line), "line $line")
        }
        assertTrue(kindsOn(text, 4).isEmpty(), "the block closed")
    }

    @Test
    fun `every token lies inside the document`() {
        val text = "---\nk: v\n---\n# H\n\n- a `b` **c** *d* ~~e~~ [f](g)\n\n```\ncode\n```\n> q\n"
        for (token in MarkdownHighlightTokenizer.tokenize(text, frontMatter = true)) {
            assertTrue(token.start in 0..text.length, "start out of range: $token")
            assertTrue(token.end in 0..text.length, "end out of range: $token")
            assertTrue(token.start < token.end, "empty or inverted span: $token")
        }
    }

    @Test
    fun `repeating a call returns the memoized list`() {
        val text = "# Memo\n\n- one\n"
        assertSame(
            MarkdownHighlightTokenizer.tokenize(text),
            MarkdownHighlightTokenizer.tokenize(text),
            "the pure scan must not repeat"
        )
    }

    @Test
    fun `the dialect flag is part of the memo key`() {
        val text = "---\nk: v\n---\n"
        val asDeck = MarkdownHighlightTokenizer.tokenize(text, frontMatter = false)
        val asCv = MarkdownHighlightTokenizer.tokenize(text, frontMatter = true)
        assertTrue(asDeck.any { it.kind == MarkdownTokenKind.ThematicBreak })
        assertTrue(asCv.any { it.kind == MarkdownTokenKind.FrontMatter })
    }
}
