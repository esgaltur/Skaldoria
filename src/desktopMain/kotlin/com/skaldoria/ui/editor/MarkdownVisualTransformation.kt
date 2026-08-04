package com.skaldoria.ui.editor

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.skaldoria.theme.PresentationTheme

/**
 * High-performance real-time syntax highlighter for Markdown editor in Skaldoria.
 * Transforms plain markdown text into rich colored syntax tokens without changing cursor offsets.
 */
class MarkdownVisualTransformation(
    private val theme: PresentationTheme
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val styled = highlightMarkdown(raw, theme)
        return TransformedText(styled, OffsetMapping.Identity)
    }

    companion object {
        fun highlightMarkdown(text: String, theme: PresentationTheme): AnnotatedString {
            return buildAnnotatedString {
                append(text)

                val lines = text.split("\n")
                var currentOffset = 0
                var insideCodeFence = false

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
                        addStyle(
                            SpanStyle(
                                color = theme.codeText,
                                fontFamily = FontFamily.Monospace
                            ),
                            lineStart,
                            lineEnd
                        )
                        currentOffset += line.length + 1
                        continue
                    }

                    // Slide Dividers (---)
                    if (trimmed == "---" || trimmed.startsWith("--- ")) {
                        addStyle(
                            SpanStyle(
                                color = theme.primary,
                                fontWeight = FontWeight.ExtraBold
                            ),
                            lineStart,
                            lineEnd
                        )
                        currentOffset += line.length + 1
                        continue
                    }

                    // Directives / Comments (<!-- ... -->)
                    if (trimmed.startsWith("<!--") && trimmed.endsWith("-->")) {
                        addStyle(
                            SpanStyle(
                                color = theme.textMuted,
                                fontStyle = FontStyle.Italic
                            ),
                            lineStart,
                            lineEnd
                        )
                        currentOffset += line.length + 1
                        continue
                    }

                    // Headers (#, ##, ###)
                    if (trimmed.startsWith("#")) {
                        val headerLevel = trimmed.takeWhile { it == '#' }.length
                        if (headerLevel in 1..6 && trimmed.getOrNull(headerLevel) == ' ') {
                            addStyle(
                                SpanStyle(
                                    color = if (headerLevel == 1) theme.primary else theme.accent,
                                    fontWeight = FontWeight.Bold
                                ),
                                lineStart,
                                lineEnd
                            )
                            currentOffset += line.length + 1
                            continue
                        }
                    }

                    // Blockquotes (>)
                    if (trimmed.startsWith(">")) {
                        addStyle(
                            SpanStyle(
                                color = theme.accent.copy(alpha = 0.85f),
                                fontStyle = FontStyle.Italic
                            ),
                            lineStart,
                            lineEnd
                        )
                    }

                    // Bullet Lists (-, *, 1.)
                    if (trimmed.startsWith("- ") || trimmed.startsWith("* ") || Regex("^\\d+\\.\\s").containsMatchIn(trimmed)) {
                        val bulletMatch = Regex("^(\\s*([-*]|\\d+\\.))\\s").find(line)
                        if (bulletMatch != null) {
                            val bulletEnd = lineStart + bulletMatch.range.last
                            addStyle(
                                SpanStyle(
                                    color = theme.primary,
                                    fontWeight = FontWeight.Bold
                                ),
                                lineStart,
                                bulletEnd
                            )
                        }
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
                                color = theme.codeText,
                                fontFamily = FontFamily.Monospace,
                                background = theme.codeBackground
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
            }
        }
    }
}
