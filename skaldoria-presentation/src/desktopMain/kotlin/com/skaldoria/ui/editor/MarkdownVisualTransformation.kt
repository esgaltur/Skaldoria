package com.skaldoria.ui.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.sp
import com.skaldoria.markdown.parser.MarkdownHighlightTokenizer
import com.skaldoria.markdown.parser.MarkdownTokenKind
import com.skaldoria.theme.AdaptiveContrastEnforcer
import com.skaldoria.theme.PresentationTheme

/**
 * High-performance real-time syntax highlighter for the Markdown editor in Skaldoria.
 * Transforms plain markdown text into rich coloured syntax tokens without changing cursor offsets.
 *
 * **What lives where.** Deciding *what a span is* belongs to
 * [MarkdownHighlightTokenizer] in `:skaldoria-markdown`, which the Writer and CV editors call too.
 * This file only maps those kinds onto a [PresentationTheme] — the part that is genuinely
 * per-application and should never be shared. See
 * `skaldoria-markdown/docs/MARKDOWN_UNIFICATION_PLAN.md`, Phase G.
 *
 * Kinds this palette leaves unmapped (`Italic`, `Link`, `FrontMatter`) produce no span, which is
 * how the deck editor keeps its current appearance while the tokenizer serves dialects that do
 * want them.
 */
class MarkdownVisualTransformation(
    private val theme: PresentationTheme,
    private val searchMatches: List<IntRange> = emptyList(),
    private val activeMatchIndex: Int = -1
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val styled = highlightMarkdown(raw, theme, searchMatches, activeMatchIndex)
        return TransformedText(styled, OffsetMapping.Identity)
    }

    companion object {

        /** The inputs [highlightMarkdown] is a pure function of, plus what it produced. */
        private class Memo(
            val text: String,
            val theme: PresentationTheme,
            val searchMatches: List<IntRange>,
            val activeMatchIndex: Int,
            val result: AnnotatedString
        )

        /**
         * Single-entry memo, and it has to live *here* rather than on the instance.
         *
         * `EditorWorkspace` constructs a fresh [MarkdownVisualTransformation] on every
         * composition, so a field on the object would never survive to be read. The natural-looking
         * alternative — remembering the `TextFieldValue` at the call site — is forbidden by EDT-1:
         * a remembered value re-seeded from the deck text is what makes the caret jump to the end
         * of the document on every keystroke.
         *
         * **What this buys:** `filter()` runs on every recomposition, not only on text change, so
         * moving the caret or dragging a selection used to re-highlight the whole document and
         * produce a byte-identical result. Those now cost a few comparisons. The text check is
         * effectively free in that case because Compose hands back the same `String` instance and
         * `String.equals` short-circuits on reference identity.
         *
         * The tokenizer keeps a memo of its own over the pure scan. This one still earns its place
         * on top of that: it also avoids re-allocating the thousands of `SpanStyle` objects a large
         * deck produces, which the tokenizer cannot do because it runs below the palette.
         *
         * Not thread-safe, deliberately: it is driven from the UI thread, the same convention
         * `DeckHistory` documents.
         */
        private var memo: Memo? = null

        fun highlightMarkdown(
            text: String,
            theme: PresentationTheme,
            searchMatches: List<IntRange> = emptyList(),
            activeMatchIndex: Int = -1
        ): AnnotatedString {
            // Cheapest comparisons first; `theme` is a data class, so its equals walks the palette.
            memo?.let { cached ->
                if (cached.activeMatchIndex == activeMatchIndex &&
                    cached.text == text &&
                    cached.searchMatches == searchMatches &&
                    cached.theme == theme
                ) {
                    return cached.result
                }
            }

            // Release the stale result before rebuilding, so the previous AnnotatedString — which
            // holds thousands of SpanStyle objects on a large deck — is collectable while its
            // replacement is being allocated.
            //
            // Measured as making no difference on `PerformanceProbe`, and kept anyway: it costs
            // one field write and it bounds peak live memory, which the probe does not measure at
            // all. See the open question in skaldoria-markdown/docs/MARKDOWN_UNIFICATION_PLAN.md, Phase C.
            memo = null

            val result = buildHighlight(text, theme, searchMatches, activeMatchIndex)
            memo = Memo(text, theme, searchMatches, activeMatchIndex, result)
            return result
        }

        private fun buildHighlight(
            text: String,
            theme: PresentationTheme,
            searchMatches: List<IntRange>,
            activeMatchIndex: Int
        ): AnnotatedString {
            return buildAnnotatedString {
                append(text)

                val baseBg = theme.surface

                // Mathematically guaranteed high-contrast colours.
                val codeTextColor = AdaptiveContrastEnforcer.ensureContrast(theme.codeText, baseBg, 7.0f)
                val keywordColor = AdaptiveContrastEnforcer.ensureContrast(theme.codeKeyword, baseBg, 4.5f)
                val stringColor = AdaptiveContrastEnforcer.ensureContrast(theme.codeString, baseBg, 4.5f)
                val commentColor = AdaptiveContrastEnforcer.ensureContrast(theme.codeComment, baseBg, 4.5f)
                val inlineCodeTextColor =
                    AdaptiveContrastEnforcer.ensureContrast(theme.codeKeyword, theme.surfaceVariant, 4.5f)

                for (token in MarkdownHighlightTokenizer.tokenize(text)) {
                    val style = when (token.kind) {
                        MarkdownTokenKind.FenceMarker -> SpanStyle(
                            color = theme.accent,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )

                        MarkdownTokenKind.CodeText -> SpanStyle(
                            color = codeTextColor,
                            fontFamily = FontFamily.Monospace
                        )

                        MarkdownTokenKind.CodeComment -> SpanStyle(
                            color = commentColor,
                            fontStyle = FontStyle.Italic
                        )

                        MarkdownTokenKind.CodeKeyword -> SpanStyle(
                            color = keywordColor,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )

                        MarkdownTokenKind.CodeString -> SpanStyle(
                            color = stringColor,
                            fontFamily = FontFamily.Monospace
                        )

                        MarkdownTokenKind.MathBlock -> SpanStyle(
                            color = theme.primary,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium
                        )

                        MarkdownTokenKind.Heading -> SpanStyle(
                            color = when (token.level) {
                                1 -> theme.primary
                                2 -> theme.accent
                                3 -> theme.textPrimary
                                else -> theme.textSecondary
                            },
                            fontWeight = if (token.level <= 2) FontWeight.Bold else FontWeight.SemiBold
                        )

                        MarkdownTokenKind.Directive -> SpanStyle(
                            color = commentColor,
                            fontStyle = FontStyle.Italic
                        )

                        MarkdownTokenKind.ThematicBreak -> SpanStyle(
                            color = theme.accent,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 4.sp
                        )

                        MarkdownTokenKind.Blockquote -> SpanStyle(
                            color = theme.textSecondary,
                            fontStyle = FontStyle.Italic
                        )

                        MarkdownTokenKind.TableRow -> SpanStyle(
                            color = theme.textSecondary,
                            fontFamily = FontFamily.Monospace
                        )

                        MarkdownTokenKind.BulletMarker -> SpanStyle(
                            color = theme.primary,
                            fontWeight = FontWeight.Bold
                        )

                        MarkdownTokenKind.InlineCode -> SpanStyle(
                            color = inlineCodeTextColor,
                            fontFamily = FontFamily.Monospace,
                            background = theme.surfaceVariant
                        )

                        MarkdownTokenKind.Bold -> SpanStyle(fontWeight = FontWeight.Bold)

                        // Not part of the deck editor's appearance; see the class KDoc.
                        MarkdownTokenKind.Italic,
                        MarkdownTokenKind.Strikethrough,
                        MarkdownTokenKind.Link,
                        MarkdownTokenKind.FrontMatter -> null
                    }

                    if (style != null) addStyle(style, token.start, token.end)
                }

                // Overlay Find & Search highlight styles.
                for ((idx, matchRange) in searchMatches.withIndex()) {
                    if (matchRange.first >= 0 && matchRange.last < text.length && matchRange.first <= matchRange.last) {
                        val isActive = idx == activeMatchIndex
                        addStyle(
                            SpanStyle(
                                background = if (isActive) Color(0xFFF59E0B) else Color(0x66F59E0B),
                                color = if (isActive) Color(0xFF000000) else if (theme.isDark) Color(0xFFFEF3C7) else Color(0xFF78350F),
                                fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Bold
                            ),
                            matchRange.first,
                            matchRange.last + 1
                        )
                    }
                }
            }
        }
    }
}
