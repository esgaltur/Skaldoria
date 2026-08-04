package com.skaldoria.ui.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import com.skaldoria.theme.PresentationTheme

/**
 * Converts a single line/run of inline Markdown into a styled [AnnotatedString].
 *
 * Supported inline syntax:
 * - `**bold**` / `__bold__`
 * - `*italic*` / `_italic_`
 * - `` `inline code` ``
 * - `~~strikethrough~~`
 *
 * Emphasis markers may be nested (e.g. `**bold with *italic* inside**`). Inline code
 * is treated literally (no nested markers). Text with no markers is returned unchanged,
 * so this is safe to apply to any user-facing string.
 */
fun inlineMarkdown(raw: String, theme: PresentationTheme): AnnotatedString =
    buildAnnotatedString { appendInlineMarkdown(raw, theme) }

private fun AnnotatedString.Builder.appendInlineMarkdown(text: String, theme: PresentationTheme) {
    var i = 0
    while (i < text.length) {
        val c = text[i]

        // Inline code: literal content between single backticks.
        if (c == '`') {
            val end = text.indexOf('`', i + 1)
            if (end > i) {
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        color = theme.codeText,
                        background = theme.codeBackground
                    )
                ) {
                    append(text.substring(i + 1, end))
                }
                i = end + 1
                continue
            }
        }

        // Bold: ** or __
        if ((c == '*' || c == '_') && i + 1 < text.length && text[i + 1] == c) {
            val marker = "$c$c"
            val end = text.indexOf(marker, i + 2)
            if (end > i + 2) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    appendInlineMarkdown(text.substring(i + 2, end), theme)
                }
                i = end + 2
                continue
            }
        }

        // Italic: * or _ (single marker, non-empty, not part of a bold ** run)
        if ((c == '*' || c == '_') && text.getOrNull(i + 1) != c) {
            val end = text.indexOf(c, i + 1)
            if (end > i + 1) {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    appendInlineMarkdown(text.substring(i + 1, end), theme)
                }
                i = end + 1
                continue
            }
        }

        // Strikethrough: ~~
        if (c == '~' && i + 1 < text.length && text[i + 1] == '~') {
            val end = text.indexOf("~~", i + 2)
            if (end > i + 2) {
                withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                    appendInlineMarkdown(text.substring(i + 2, end), theme)
                }
                i = end + 2
                continue
            }
        }

        append(c)
        i++
    }
}
