package com.skaldoria.cv

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import com.skaldoria.markdown.parser.MarkdownHighlightTokenizer
import com.skaldoria.markdown.parser.MarkdownTokenKind
import com.skaldoria.theme.AdaptiveContrastEnforcer

/**
 * Syntax highlighting for the CV editor's Markdown source field.
 *
 * **What lives where.** Deciding *what a span is* belongs to [MarkdownHighlightTokenizer] in
 * `:skaldoria-markdown`, shared with the deck and Writer editors. This file only maps those kinds
 * onto the app's [ColorScheme].
 *
 * This used to be five whole-document regexes that shared nothing with the rest of Skaldoria but a
 * class name, and it was wrong in ways the shared grammar had already fixed elsewhere: fenced code
 * was not tracked at all, so `# comment` inside a ```` ```bash ```` block coloured as a heading and
 * `**` inside code still bolded; indented headings were missed because the pattern anchored `#` to
 * column 0; and blockquotes, tables, thematic breaks, math and inline code went unstyled. Front
 * matter was matched with `^---\n.*?\n---`, which disagreed with `CvMarkdownAdapter` — the
 * authority that actually reads it — on trailing whitespace and on CRLF files.
 *
 * See `skaldoria-markdown/docs/MARKDOWN_UNIFICATION_PLAN.md`, Phase G.
 */
class MarkdownVisualTransformation(
    private val colors: ColorScheme
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText =
        TransformedText(highlight(text.text, colors), OffsetMapping.Identity)

    private companion object {

        fun highlight(text: String, colors: ColorScheme): AnnotatedString = buildAnnotatedString {
            append(text)

            val surface = colors.surface

            // The old palette was hardcoded hex — `0xFF1976D2` headings and `0xFF757575` front
            // matter — which failed contrast the moment the app ran against a dark scheme. These
            // are enforced against the field's own background instead.
            val headingColor = AdaptiveContrastEnforcer.ensureContrast(colors.primary, surface, 4.5f)
            val accentColor = AdaptiveContrastEnforcer.ensureContrast(colors.secondary, surface, 4.5f)
            val mutedColor = AdaptiveContrastEnforcer.ensureContrast(colors.onSurfaceVariant, surface, 4.5f)
            val linkColor = AdaptiveContrastEnforcer.ensureContrast(colors.tertiary, surface, 4.5f)
            val codeColor =
                AdaptiveContrastEnforcer.ensureContrast(colors.onSurfaceVariant, colors.surfaceVariant, 4.5f)

            // The CV dialect has YAML front matter; decks and Writer documents do not. Passing it
            // here is what stops a leading `---` from colouring as a thematic break.
            for (token in MarkdownHighlightTokenizer.tokenize(text, frontMatter = true)) {
                val style = when (token.kind) {
                    MarkdownTokenKind.FrontMatter -> SpanStyle(
                        color = mutedColor,
                        fontStyle = FontStyle.Italic
                    )

                    MarkdownTokenKind.Heading -> SpanStyle(
                        color = if (token.level <= 2) headingColor else accentColor,
                        fontWeight = if (token.level <= 2) FontWeight.Bold else FontWeight.SemiBold
                    )

                    MarkdownTokenKind.FenceMarker -> SpanStyle(
                        color = accentColor,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )

                    // A CV has no syntax-highlighted code, so every line in a fence gets the same
                    // treatment and the keyword/string/comment kinds stay unmapped below.
                    MarkdownTokenKind.CodeText -> SpanStyle(
                        color = codeColor,
                        fontFamily = FontFamily.Monospace
                    )

                    MarkdownTokenKind.InlineCode -> SpanStyle(
                        color = codeColor,
                        fontFamily = FontFamily.Monospace,
                        background = colors.surfaceVariant
                    )

                    MarkdownTokenKind.Blockquote,
                    MarkdownTokenKind.Directive -> SpanStyle(
                        color = mutedColor,
                        fontStyle = FontStyle.Italic
                    )

                    MarkdownTokenKind.ThematicBreak -> SpanStyle(
                        color = mutedColor,
                        fontWeight = FontWeight.ExtraBold
                    )

                    MarkdownTokenKind.TableRow -> SpanStyle(
                        color = mutedColor,
                        fontFamily = FontFamily.Monospace
                    )

                    MarkdownTokenKind.BulletMarker -> SpanStyle(
                        color = accentColor,
                        fontWeight = FontWeight.Bold
                    )

                    MarkdownTokenKind.Bold -> SpanStyle(fontWeight = FontWeight.Bold)

                    MarkdownTokenKind.Italic -> SpanStyle(fontStyle = FontStyle.Italic)

                    MarkdownTokenKind.Strikethrough -> SpanStyle(
                        color = mutedColor,
                        textDecoration = TextDecoration.LineThrough
                    )

                    MarkdownTokenKind.Link -> SpanStyle(
                        color = linkColor,
                        textDecoration = TextDecoration.Underline
                    )

                    // Not part of the CV editor's appearance; see the comment on CodeText.
                    MarkdownTokenKind.CodeKeyword,
                    MarkdownTokenKind.CodeString,
                    MarkdownTokenKind.CodeComment,
                    MarkdownTokenKind.MathBlock -> null
                }

                if (style != null) addStyle(style, token.start, token.end)
            }
        }
    }
}
