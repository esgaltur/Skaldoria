package com.skaldoria.ui.editor

import com.skaldoria.theme.BuiltinThemes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

/**
 * The highlighter's memo must return a cached result only when **every** input matches.
 *
 * PRF-6 added it because `VisualTransformation.filter()` runs on every recomposition, not only on
 * text change — so moving the caret re-highlighted the whole document to produce a byte-identical
 * result. The risk a cache introduces is the opposite one: serving a stale result after something
 * relevant changed, which would show as the editor keeping the previous document's colouring.
 *
 * Identity (`assertSame`) is the assertion that matters here. Equal-but-recomputed would pass a
 * structural check while still doing all the work the memo exists to avoid.
 */
class HighlightMemoTest {

    private val theme = BuiltinThemes.SkaldoriaDark
    private val text = "# Heading\n\n- bullet\n\n```kotlin\nfun x() = 1\n```\n"

    @Test
    fun `identical inputs return the very same instance`() {
        val first = MarkdownVisualTransformation.highlightMarkdown(text, theme)
        val second = MarkdownVisualTransformation.highlightMarkdown(text, theme)
        assertSame(first, second, "a repeated call must not recompute")
    }

    @Test
    fun `an equal but distinct string still hits the cache`() {
        val first = MarkdownVisualTransformation.highlightMarkdown(text, theme)
        // Built at runtime so it cannot be the interned literal.
        val copy = StringBuilder(text).toString()
        val second = MarkdownVisualTransformation.highlightMarkdown(copy, theme)
        assertSame(first, second, "equality, not identity, is the contract")
    }

    @Test
    fun `changed text recomputes`() {
        val first = MarkdownVisualTransformation.highlightMarkdown(text, theme)
        val second = MarkdownVisualTransformation.highlightMarkdown(text + "extra\n", theme)
        assertNotSame(first, second)
        assertEquals(text + "extra\n", second.text)
    }

    @Test
    fun `changed theme recomputes`() {
        val other = BuiltinThemes.all.first { it.id != theme.id }
        val first = MarkdownVisualTransformation.highlightMarkdown(text, theme)
        val second = MarkdownVisualTransformation.highlightMarkdown(text, other)
        assertNotSame(first, second, "a theme switch must not keep the old palette")
    }

    @Test
    fun `changed search state recomputes`() {
        val base = MarkdownVisualTransformation.highlightMarkdown(text, theme)

        val withMatch = MarkdownVisualTransformation.highlightMarkdown(text, theme, listOf(2..6), 0)
        assertNotSame(base, withMatch, "adding a search match must recompute")

        val movedActive = MarkdownVisualTransformation.highlightMarkdown(text, theme, listOf(2..6), -1)
        assertNotSame(withMatch, movedActive, "moving the active match must recompute")
    }
}
