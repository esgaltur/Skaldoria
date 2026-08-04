package com.markdownpres.ui.components

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.markdownpres.theme.BuiltinThemes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InlineMarkdownTest {

    private val theme = BuiltinThemes.NordDark

    @Test
    fun testPlainTextIsUnchanged() {
        val result = inlineMarkdown("Just a plain sentence.", theme)
        assertEquals("Just a plain sentence.", result.text)
        assertTrue(result.spanStyles.isEmpty())
    }

    @Test
    fun testBoldAsterisksStripsMarkersAndAppliesWeight() {
        val result = inlineMarkdown("A **bold** word", theme)
        assertEquals("A bold word", result.text)
        val span = result.spanStyles.single()
        assertEquals(FontWeight.Bold, span.item.fontWeight)
        assertEquals("bold", result.text.substring(span.start, span.end))
    }

    @Test
    fun testBoldUnderscores() {
        val result = inlineMarkdown("__strong__", theme)
        assertEquals("strong", result.text)
        assertEquals(FontWeight.Bold, result.spanStyles.single().item.fontWeight)
    }

    @Test
    fun testItalic() {
        val result = inlineMarkdown("an *emphasised* term", theme)
        assertEquals("an emphasised term", result.text)
        assertEquals(FontStyle.Italic, result.spanStyles.single().item.fontStyle)
    }

    @Test
    fun testNestedItalicInsideBold() {
        val result = inlineMarkdown("**bold and *italic* end**", theme)
        assertEquals("bold and italic end", result.text)
        assertTrue(result.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
        assertTrue(result.spanStyles.any { it.item.fontStyle == FontStyle.Italic })
    }

    @Test
    fun testInlineCodeIsLiteral() {
        val result = inlineMarkdown("call `render(**x**)` now", theme)
        // Markers inside code are preserved literally.
        assertEquals("call render(**x**) now", result.text)
    }

    @Test
    fun testUnterminatedMarkerIsLeftAsIs() {
        val result = inlineMarkdown("2 ** 3 = 8", theme)
        assertEquals("2 ** 3 = 8", result.text)
        assertTrue(result.spanStyles.isEmpty())
    }
}
