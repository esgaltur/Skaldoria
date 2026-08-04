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
        private val KEYWORDS = setOf(
            "fun", "val", "var", "class", "object", "interface", "import", "package",
            "return", "if", "else", "when", "for", "while", "try", "catch", "def",
            "async", "await", "const", "let", "function", "public", "private", "override"
        )

        fun highlightMarkdown(
            text: String,
            theme: PresentationTheme,
            searchMatches: List<IntRange> = emptyList(),
            activeMatchIndex: Int = -1
        ): AnnotatedString {
            return buildAnnotatedString {
                append(text)

                val lines = text.split("\n")
                var currentOffset = 0
                var insideCodeFence = false

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

                    // Code Fences (```)
                    if (trimmed.startsWith("```")) {
                        insideCodeFence = !insideCodeFence
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

                    if (insideCodeFence) {
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
                            val wordRegex = Regex("\\b[a-zA-Z_][a-zA-Z0-9_]*\\b")
                            for (match in wordRegex.findAll(line)) {
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
                            val strRegex = Regex("\"[^\"]*\"|'[^']*'")
                            for (match in strRegex.findAll(line)) {
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

                    // Headers (# Heading)
                    if (trimmed.startsWith("#")) {
                        val headerLevel = trimmed.takeWhile { it == '#' }.length
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

                    // Slide Delimiters (---)
                    if (trimmed == "---" || trimmed.startsWith("--- ")) {
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

                    // Math Formulas ($$...$$)
                    if (trimmed.startsWith("$$") || trimmed.endsWith("$$")) {
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
                    val bulletMatch = Regex("^(\\s*[-*+]|\\s*\\d+\\.)\\s").find(line)
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
