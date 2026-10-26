package com.skaldoria.ui.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import com.skaldoria.markdown.parser.EmphasisRules
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

        // Inline Math: $$...$$ or $...$
        if (c == '$') {
            val isDouble = i + 1 < text.length && text[i + 1] == '$'
            val marker = if (isDouble) "$$" else "$"
            val startIdx = if (isDouble) i + 2 else i + 1
            val end = text.indexOf(marker, startIdx)
            if (end > startIdx) {
                val mathRaw = text.substring(startIdx, end)
                val mathAnnotated = buildLatexAnnotatedString(mathRaw, theme, baseFontSize = 16)
                append(mathAnnotated)
                i = end + marker.length
                continue
            }
        }

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

        // Bold (** / __) and italic (* / _), delimited by the shared flanking rules.
        val emphasis = EmphasisRules.match(text, i)
        if (emphasis != null) {
            val style = if (emphasis.strong) SpanStyle(fontWeight = FontWeight.Bold) else SpanStyle(fontStyle = FontStyle.Italic)
            withStyle(style) {
                appendInlineMarkdown(text.substring(emphasis.contentStart, emphasis.contentEnd), theme)
            }
            i = emphasis.end
            continue
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
