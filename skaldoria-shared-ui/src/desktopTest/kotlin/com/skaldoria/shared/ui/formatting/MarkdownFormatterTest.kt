package com.skaldoria.shared.ui.formatting

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.test.Test
import kotlin.test.assertEquals

class MarkdownFormatterTest {

    @Test
    fun `test toggleBold surrounds empty selection`() {
        val initial = TextFieldValue("Hello World", TextRange(6, 6))
        val result = MarkdownFormatter.toggleBold(initial)
        assertEquals("Hello ****World", result.text)
        assertEquals(TextRange(8, 8), result.selection)
    }

    @Test
    fun `test toggleBold surrounds word selection`() {
        val initial = TextFieldValue("Hello World", TextRange(6, 11))
        val result = MarkdownFormatter.toggleBold(initial)
        assertEquals("Hello **World**", result.text)
        assertEquals(TextRange(8, 13), result.selection)
    }

    @Test
    fun `test toggleBold removes surrounding bold from selection inside`() {
        val initial = TextFieldValue("Hello **World**", TextRange(8, 13))
        val result = MarkdownFormatter.toggleBold(initial)
        assertEquals("Hello World", result.text)
        assertEquals(TextRange(6, 11), result.selection)
    }

    @Test
    fun `test toggleBold removes surrounding bold from selection outside`() {
        val initial = TextFieldValue("Hello **World**", TextRange(6, 15))
        val result = MarkdownFormatter.toggleBold(initial)
        assertEquals("Hello World", result.text)
        assertEquals(TextRange(6, 11), result.selection)
    }

    @Test
    fun `test toggleHeader1 adds header to start of line`() {
        val initial = TextFieldValue("Hello World", TextRange(6, 11))
        val result = MarkdownFormatter.toggleHeader1(initial)
        assertEquals("# Hello World", result.text)
        assertEquals(TextRange(8, 13), result.selection)
    }

    @Test
    fun `test toggleHeader1 removes header if already present`() {
        val initial = TextFieldValue("# Hello World", TextRange(8, 13))
        val result = MarkdownFormatter.toggleHeader1(initial)
        assertEquals("Hello World", result.text)
        assertEquals(TextRange(6, 11), result.selection)
    }

    @Test
    fun `test toggleList applies to multiline`() {
        val initial = TextFieldValue("Line 1\nLine 2", TextRange(8, 8)) // Cursor on Line 2
        val result = MarkdownFormatter.toggleList(initial)
        assertEquals("Line 1\n- Line 2", result.text)
        assertEquals(TextRange(10, 10), result.selection)
    }
}
