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
import com.skaldoria.core.parser.FenceInfo
import com.skaldoria.core.parser.HeadingRules
import com.skaldoria.core.parser.MathRules
import com.skaldoria.core.parser.ThematicBreakRules
import com.skaldoria.core.parser.FenceRules
import com.skaldoria.theme.AdaptiveContrastEnforcer
import com.skaldoria.theme.PresentationTheme

/**
 * High-performance real-time syntax highlighter for Markdown editor in Skaldoria.
 * Transforms plain markdown text into rich colored syntax tokens without changing cursor offsets.
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
        /**
         * **Category: display only.** These decide what gets coloured, nothing else. They are
         * allowed — expected — to be looser than the parser: this file styles `### Sub` and
         * `#hashtag` as headings, neither of which begins a slide, and that is correct for both.
         *
         * The rules that must *not* diverge are the shared-grammar ones in `:markdown-core`
         * (`FenceRules`), which this file calls rather than reimplements. See
         * `docs/MARKDOWN_UNIFICATION_PLAN.md`, Phase E, for the three that still disagree in ways
         * that are genuine bugs — horizontal rules, table rows and `$$` blocks.
         *
         * PRF-5: compiled once.
         *
         * These three were `Regex(...)` literals *inside* the per-line loop, so every call
         * re-ran `Pattern.compile` once per line — and [filter] is called on every composition
         * of the editor field, which is at least once per keystroke. On an 886-line deck that
         * was ~900 compilations per keystroke for [BULLET] alone, since it sits on the path
         * every ordinary prose line takes.
         */
        private val CODE_WORD = Regex("""\b[a-zA-Z_][a-zA-Z0-9_]*\b""")
        private val CODE_STRING = Regex("""\"[^\"]*\"|'[^']*'""")
        private val BULLET = Regex("""^(\s*[-*+]|\s*\d+\.)\s""")

        private val KEYWORDS = setOf(
            "fun", "val", "var", "class", "object", "interface", "import", "package",
            "return", "if", "else", "when", "for", "while", "try", "catch", "def",
            "async", "await", "const", "let", "function", "public", "private", "override"
        )

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
            // all. See the open question in docs/MARKDOWN_UNIFICATION_PLAN.md, Phase C.
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

                val lines = text.split("\n")
                var currentOffset = 0

                // Phase B: fence state comes from the same authority the parser uses, so the
                // editor cannot colour a block the parser does not consider code. This was a
                // private `startsWith("```")` toggle, which made tilde fences invisible here and
                // disagreed with the parser on any unusual info string.
                var openFence: FenceInfo? = null

                // Phase F: `$$` block state, tracked the same way as fences. Without it the
                // delimiters were styled and the formula body between them fell through to
                // ordinary prose handling.
                var inMathBlock = false

                val baseBg = theme.surface

                // Safe mathematically guaranteed high-contrast colors
                val editorCodeTextColor = AdaptiveContrastEnforcer.ensureContrast(theme.codeText, baseBg, 7.0f)
                val editorKeywordColor = AdaptiveContrastEnforcer.ensureContrast(theme.codeKeyword, baseBg, 4.5f)
                val editorStringColor = AdaptiveContrastEnforcer.ensureContrast(theme.codeString, baseBg, 4.5f)
                val editorCommentColor = AdaptiveContrastEnforcer.ensureContrast(theme.codeComment, baseBg, 4.5f)
                val editorInlineCodeTextColor = AdaptiveContrastEnforcer.ensureContrast(theme.codeKeyword, theme.surfaceVariant, 4.5f)
                val editorInlineCodeBg = theme.surfaceVariant

                for (line in lines) {
                    val lineStart = currentOffset
                    val lineEnd = lineStart + line.length
                    val trimmed = line.trim()

                    // Fence markers: ``` or ~~~, of any length, with any info string.
                    val currentFence = openFence
                    val isFenceMarker = if (currentFence != null) {
                        FenceRules.closes(trimmed, currentFence).also { if (it) openFence = null }
                    } else {
                        FenceRules.openingFence(trimmed)?.also { openFence = it } != null
                    }

                    if (isFenceMarker) {
                        addStyle(
                            SpanStyle(
                                color = theme.accent,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            ),
                            lineStart,
                            lineEnd
                        )
                        currentOffset += line.length + 1
                        continue
                    }

                    if (openFence != null) {
                        // Apply base code styling
                        addStyle(
                            SpanStyle(
                                color = editorCodeTextColor,
                                fontFamily = FontFamily.Monospace
                            ),
                            lineStart,
                            lineEnd
                        )

                        // Highlight comments
                        if (trimmed.startsWith("//") || trimmed.startsWith("#") || trimmed.startsWith("/*")) {
                            addStyle(
                                SpanStyle(color = editorCommentColor, fontStyle = FontStyle.Italic),
                                lineStart,
                                lineEnd
                            )
                        } else {
                            // Token highlighting for code
                            for (match in CODE_WORD.findAll(line)) {
                                if (KEYWORDS.contains(match.value)) {
                                    val start = lineStart + match.range.first
                                    val end = lineStart + match.range.last + 1
                                    addStyle(
                                        SpanStyle(
                                            color = editorKeywordColor,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        ),
                                        start,
                                        end
                                    )
                                }
                            }

                            // Highlight string literals
                            for (match in CODE_STRING.findAll(line)) {
                                val start = lineStart + match.range.first
                                val end = lineStart + match.range.last + 1
                                addStyle(
                                    SpanStyle(color = editorStringColor, fontFamily = FontFamily.Monospace),
                                    start,
                                    end
                                )
                            }
                        }

                        currentOffset += line.length + 1
                        continue
                    }

                    // Math blocks ($$ … $$), including the body between the delimiters.
                    // Policy note: the parser turns this into one MathFormula element; here it is
                    // only a colour. Both defer to MathRules for *what the syntax is*.
                    val isMathLine = when {
                        inMathBlock -> {
                            if (MathRules.closesBlock(trimmed)) inMathBlock = false
                            true
                        }
                        MathRules.isSingleLine(trimmed) -> true
                        MathRules.opensBlock(trimmed) -> {
                            inMathBlock = true
                            true
                        }
                        else -> false
                    }

                    if (isMathLine) {
                        addStyle(
                            SpanStyle(
                                color = theme.primary,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium
                            ),
                            lineStart,
                            lineEnd
                        )
                        currentOffset += line.length + 1
                        continue
                    }

                    // Headers (# Heading)
                    val heading = HeadingRules.heading(trimmed)
                    if (heading != null) {
                        val headerLevel = heading.level
                        val headerColor = when (headerLevel) {
                            1 -> theme.primary
                            2 -> theme.accent
                            3 -> theme.textPrimary
                            else -> theme.textSecondary
                        }
                        val headerWeight = if (headerLevel <= 2) FontWeight.Bold else FontWeight.SemiBold

                        addStyle(
                            SpanStyle(
                                color = headerColor,
                                fontWeight = headerWeight
                            ),
                            lineStart,
                            lineEnd
                        )
                        currentOffset += line.length + 1
                        continue
                    }

                    // Directives & Comments (<!-- ... --> or ::: ...)
                    if (trimmed.startsWith("<!--") || trimmed.startsWith(":::") || trimmed.startsWith("> note:")) {
                        addStyle(
                            SpanStyle(
                                color = editorCommentColor,
                                fontStyle = FontStyle.Italic
                            ),
                            lineStart,
                            lineEnd
                        )
                        currentOffset += line.length + 1
                        continue
                    }

                    // Thematic breaks: ***, --- or ___. Previously only an exact `---` was styled,
                    // so the other forms split the deck with no visual indication.
                    if (ThematicBreakRules.isThematicBreak(trimmed)) {
                        addStyle(
                            SpanStyle(
                                color = theme.accent,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 4.sp
                            ),
                            lineStart,
                            lineEnd
                        )
                        currentOffset += line.length + 1
                        continue
                    }

                    // Blockquotes (> ...)
                    if (trimmed.startsWith(">")) {
                        addStyle(
                            SpanStyle(
                                color = theme.textSecondary,
                                fontStyle = FontStyle.Italic
                            ),
                            lineStart,
                            lineEnd
                        )
                        currentOffset += line.length + 1
                        continue
                    }


                    // Table Rows (| ... |)
                    if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
                        addStyle(
                            SpanStyle(
                                color = theme.textSecondary,
                                fontFamily = FontFamily.Monospace
                            ),
                            lineStart,
                            lineEnd
                        )
                        currentOffset += line.length + 1
                        continue
                    }

                    // Bullet Lists (- or * or + or 1.)
                    val bulletMatch = BULLET.find(line)
                    if (bulletMatch != null) {
                        val bulletEnd = lineStart + bulletMatch.range.last + 1
                        addStyle(
                            SpanStyle(
                                color = theme.primary,
                                fontWeight = FontWeight.Bold
                            ),
                            lineStart,
                            bulletEnd
                        )
                    }

                    // Inline code (`...`)
                    var inlineCodeIdx = 0
                    while (inlineCodeIdx < line.length) {
                        val startTick = line.indexOf('`', inlineCodeIdx)
                        if (startTick == -1) break
                        val endTick = line.indexOf('`', startTick + 1)
                        if (endTick == -1) break

                        addStyle(
                            SpanStyle(
                                color = editorInlineCodeTextColor,
                                fontFamily = FontFamily.Monospace,
                                background = editorInlineCodeBg
                            ),
                            lineStart + startTick,
                            lineStart + endTick + 1
                        )
                        inlineCodeIdx = endTick + 1
                    }

                    // Inline bold (**...**)
                    var boldIdx = 0
                    while (boldIdx < line.length - 1) {
                        val startBold = line.indexOf("**", boldIdx)
                        if (startBold == -1) break
                        val endBold = line.indexOf("**", startBold + 2)
                        if (endBold == -1) break

                        addStyle(
                            SpanStyle(fontWeight = FontWeight.Bold),
                            lineStart + startBold,
                            lineStart + endBold + 2
                        )
                        boldIdx = endBold + 2
                    }

                    currentOffset += line.length + 1
                }

                // Overlay Find & Search Highlight Styles
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
