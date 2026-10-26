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

    @Test
    fun `italic on a bold word makes it bold italic instead of stripping the bold`() {
        val result = MarkdownFormatter.toggleItalic(TextFieldValue("a **word** b", TextRange(4, 8)))
        assertEquals("a ***word*** b", result.text)
        assertEquals(TextRange(5, 9), result.selection)
    }

    @Test
    fun `italic toggles off a bold italic word and leaves it bold`() {
        val result = MarkdownFormatter.toggleItalic(TextFieldValue("a ***word*** b", TextRange(5, 9)))
        assertEquals("a **word** b", result.text)
    }

    @Test
    fun `italic on a selection that includes bold markers wraps rather than unwraps`() {
        val result = MarkdownFormatter.toggleItalic(TextFieldValue("**word**", TextRange(0, 8)))
        assertEquals("***word***", result.text)
    }

    @Test
    fun `italic still toggles off plain italic`() {
        assertEquals("word", MarkdownFormatter.toggleItalic(TextFieldValue("*word*", TextRange(1, 5))).text)
        assertEquals("word", MarkdownFormatter.toggleItalic(TextFieldValue("*word*", TextRange(0, 6))).text)
    }

    @Test
    fun `changing heading level replaces the old prefix`() {
        val result = MarkdownFormatter.toggleHeader1(TextFieldValue("## Title", TextRange(5, 5)))
        assertEquals("# Title", result.text)
        assertEquals(TextRange(4, 4), result.selection)
    }

    @Test
    fun `checklist converts a bullet instead of stacking on it`() {
        assertEquals("- [ ] item", MarkdownFormatter.toggleChecklist(TextFieldValue("- item", TextRange(3, 3))).text)
    }

    @Test
    fun `list converts a checklist item instead of mangling it`() {
        assertEquals("- task", MarkdownFormatter.toggleList(TextFieldValue("- [ ] task", TextRange(8, 8))).text)
    }

    @Test
    fun `a line format applies to every selected line`() {
        val text = "one\ntwo\nthree"
        val result = MarkdownFormatter.toggleList(TextFieldValue(text, TextRange(0, text.length)))
        assertEquals("- one\n- two\n- three", result.text)
        assertEquals(TextRange(2, result.text.length), result.selection)
    }

    @Test
    fun `a line format is removed from every selected line when all carry it`() {
        val text = "- one\n- two"
        assertEquals("one\ntwo", MarkdownFormatter.toggleList(TextFieldValue(text, TextRange(0, text.length))).text)
    }

    @Test
    fun `a selection ending at the start of a line does not pull that line in`() {
        val result = MarkdownFormatter.toggleQuote(TextFieldValue("one\ntwo", TextRange(0, 4)))
        assertEquals("> one\ntwo", result.text)
    }

    @Test
    fun `a caret at the very start of a document whose first line is empty formats that line`() {
        assertEquals("# \nnext", MarkdownFormatter.toggleHeader1(TextFieldValue("\nnext", TextRange(0, 0))).text)
    }
}
