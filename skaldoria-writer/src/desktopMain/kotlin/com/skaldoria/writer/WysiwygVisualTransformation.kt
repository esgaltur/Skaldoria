package com.skaldoria.writer

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import com.skaldoria.markdown.parser.HeadingRules
import com.skaldoria.shared.ui.theme.SkaldoriaTheme

class WysiwygVisualTransformation(
    private val theme: SkaldoriaTheme,
    private val cursorIndex: Int,
    private val isVisualMode: Boolean,
    private val isFocusMode: Boolean = false
) : VisualTransformation {

    /** Typography and source-marker width for one CommonMark ATX heading line. */
    private data class HeadingVisualStyle(
        val markerLength: Int,
        val fontSize: androidx.compose.ui.unit.TextUnit,
        val lineHeight: androidx.compose.ui.unit.TextUnit
    )

    /**
     * One heading authority for source mode, the active visual line, and folded visual lines.
     *
     * H1 used to be 32sp inside the field's fixed 27sp line height, which clipped its glyphs
     * and let adjacent lines collide. A paragraph style is required: font size is a span
     * concern, but vertical metrics belong to the paragraph.
     */
    private fun headingStyle(lineWithoutIndent: String): HeadingVisualStyle? {
        val heading = HeadingRules.heading(lineWithoutIndent) ?: return null
        val whitespaceLength = lineWithoutIndent
            .drop(heading.level)
            .takeWhile(Char::isWhitespace)
            .length
        val (fontSize, lineHeight) = when (heading.level) {
            1 -> 32.sp to 42.sp
            2 -> 24.sp to 34.sp
            3 -> 20.sp to 30.sp
            4 -> 18.sp to 28.sp
            5 -> 17.sp to 27.sp
            else -> 16.sp to 27.sp
        }
        return HeadingVisualStyle(
            markerLength = heading.level + whitespaceLength,
            fontSize = fontSize,
            lineHeight = lineHeight
        )
    }

    private fun AnnotatedString.Builder.styleHeading(
        start: Int,
        end: Int,
        style: HeadingVisualStyle,
        alpha: Float = 1f
    ) {
        if (start >= end) return
        addStyle(
            SpanStyle(
                color = theme.accent.copy(alpha = alpha),
                fontWeight = FontWeight.Bold,
                fontSize = style.fontSize
            ),
            start,
            end
        )
        addStyle(ParagraphStyle(lineHeight = style.lineHeight), start, end)
    }

    override fun filter(text: AnnotatedString): TransformedText {
        val originalText = text.text
        if (originalText.isEmpty()) return TransformedText(text, OffsetMapping.Identity)

        if (!isVisualMode) {
            // SOURCE MODE: Provide syntax highlighting without hiding characters
            val builder = AnnotatedString.Builder(originalText)
            val lines = originalText.split('\n')
            var lineStart = 0

            for (line in lines) {
                val lineEnd = lineStart + line.length
                val trimmed = line.trimStart()

                headingStyle(trimmed)?.let { style ->
                    builder.styleHeading(lineStart, lineEnd, style)
                }

                // Bold (**text**)
                if (line.contains("**")) {
                    var i = 0
                    while (i < line.length - 1) {
                        if (line[i] == '*' && line[i + 1] == '*') {
                            val closeIdx = line.indexOf("**", i + 2)
                            if (closeIdx != -1) {
                                builder.addStyle(
                                    SpanStyle(color = theme.accent, fontWeight = FontWeight.Bold),
                                    lineStart + i, lineStart + closeIdx + 2
                                )
                                i = closeIdx + 2
                                continue
                            }
                        }
                        i++
                    }
                }

                // Inline code (`...`)
                var codeIdx = 0
                while (codeIdx < line.length) {
                    val startTick = line.indexOf('`', codeIdx)
                    if (startTick == -1) break
                    val endTick = line.indexOf('`', startTick + 1)
                    if (endTick == -1) break
                    builder.addStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            color = theme.accent,
                            background = theme.surface
                        ),
                        lineStart + startTick, lineStart + endTick + 1
                    )
                    codeIdx = endTick + 1
                }

                // Blockquote prefix (> ...)
                if (trimmed.startsWith(">")) {
                    builder.addStyle(
                        SpanStyle(
                            color = theme.subtext,
                            fontStyle = FontStyle.Italic
                        ),
                        lineStart, lineEnd
                    )
                }

                // List bullet prefix (- / * / + / 1.)
                val bulletMatch = Regex("""^(\s*[-*+]|\s*\d+\.)\s""").find(line)
                if (bulletMatch != null) {
                    builder.addStyle(
                        SpanStyle(
                            color = theme.accent,
                            fontWeight = FontWeight.Bold
                        ),
                        lineStart, lineStart + bulletMatch.range.last + 1
                    )
                }

                // Strikethrough (~~...~~)
                var strikeIdx = 0
                while (strikeIdx < line.length - 1) {
                    if (line[strikeIdx] == '~' && line[strikeIdx + 1] == '~') {
                        val closeIdx = line.indexOf("~~", strikeIdx + 2)
                        if (closeIdx != -1) {
                            builder.addStyle(
                                SpanStyle(
                                    textDecoration = TextDecoration.LineThrough,
                                    color = theme.subtext
                                ),
                                lineStart + strikeIdx, lineStart + closeIdx + 2
                            )
                            strikeIdx = closeIdx + 2
                            continue
                        }
                    }
                    strikeIdx++
                }

                lineStart = lineEnd + 1
            }

            return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
        }

        // VISUAL MODE (WYSIWYG folding)
        val builder = AnnotatedString.Builder()
        val origToTrans = IntArray(originalText.length + 1)
        val transToOrigList = mutableListOf<Int>()

        var origIdx = 0
        var transIdx = 0

        fun appendVisible(str: String) {
            for (char in str) {
                origToTrans[origIdx] = transIdx
                transToOrigList.add(origIdx)
                origIdx++
                transIdx++
            }
            builder.append(str)
        }

        fun appendHidden(count: Int) {
            for (i in 0 until count) {
                origToTrans[origIdx] = transIdx
                origIdx++
            }
        }

        fun appendFoldedInline(lineEnd: Int, alpha: Float) {
            while (origIdx < lineEnd) {
                val marker = when {
                    originalText.startsWith("**", origIdx) -> "**"
                    originalText.startsWith("~~", origIdx) -> "~~"
                    originalText[origIdx] == '`' -> "`"
                    originalText[origIdx] == '*' || originalText[origIdx] == '_' ->
                        originalText[origIdx].toString()
                    else -> null
                }
                val closingIndex = marker?.let {
                    originalText.indexOf(it, origIdx + it.length)
                        .takeIf { close -> close in (origIdx + it.length) until lineEnd }
                }

                if (marker == null || closingIndex == null) {
                    val plainStart = transIdx
                    appendVisible(originalText[origIdx].toString())
                    if (isFocusMode && alpha < 1f) {
                        builder.addStyle(
                            SpanStyle(color = theme.text.copy(alpha = alpha)),
                            plainStart,
                            transIdx
                        )
                    }
                    continue
                }

                appendHidden(marker.length)
                val contentStart = transIdx
                appendVisible(originalText.substring(origIdx, closingIndex))
                val style = when (marker) {
                    "**" -> SpanStyle(
                        fontWeight = FontWeight.Bold,
                        color = theme.text.copy(alpha = alpha)
                    )
                    "~~" -> SpanStyle(
                        textDecoration = TextDecoration.LineThrough,
                        color = theme.text.copy(alpha = alpha)
                    )
                    "`" -> SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        color = theme.accent.copy(alpha = alpha),
                        background = theme.surface
                    )
                    else -> SpanStyle(
                        fontStyle = FontStyle.Italic,
                        color = theme.text.copy(alpha = alpha)
                    )
                }
                builder.addStyle(style, contentStart, transIdx)
                appendHidden(marker.length)
            }
        }

        val lines = originalText.split('\n')
        var lineStart = 0

        for (line in lines) {
            val lineEnd = lineStart + line.length
            val hasCursor = cursorIndex in lineStart..lineEnd
            val alpha = if (isFocusMode && !hasCursor) 0.3f else 1f

            // Visual mode is a pure WYSIWYG view: every marker folds away on every line,
            // regardless of the caret. Editing markup is done through the formatting toolbar,
            // while Source mode remains available for raw syntax editing.
            val trimmedLine = line.trimStart()
            val indentationLength = line.length - trimmedLine.length
            val heading = headingStyle(trimmedLine)

            if (indentationLength > 0) {
                appendVisible(line.take(indentationLength))
            }
            val contentStart = transIdx
            if (heading != null) {
                appendHidden(heading.markerLength)
            }
            appendFoldedInline(lineEnd, alpha)

            if (heading != null) {
                builder.styleHeading(contentStart, transIdx, heading, alpha)
            }

            if (lineEnd < originalText.length) {
                appendVisible("\n")
            }
            lineStart = lineEnd + 1
        }

        origToTrans[origIdx] = transIdx
        transToOrigList.add(origIdx)
        val transToOrig = transToOrigList.toIntArray()

        return TransformedText(
            builder.toAnnotatedString(),
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int {
                    if (offset < 0) return 0
                    if (offset > originalText.length) return transIdx
                    return origToTrans[offset]
                }

                override fun transformedToOriginal(offset: Int): Int {
                    if (offset < 0) return 0
                    if (offset >= transToOrig.size) return originalText.length
                    return transToOrig[offset]
                }
            }
        )
    }
}
