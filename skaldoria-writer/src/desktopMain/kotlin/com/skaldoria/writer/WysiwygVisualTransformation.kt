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
        return if (isVisualMode) VisualFold(originalText).build()
        else highlightSource(originalText)
    }

    /** Runs [action] once per source line, supplying its start/end offsets in the whole text. */
    private inline fun forEachLine(
        text: String,
        action: (line: String, lineStart: Int, lineEnd: Int) -> Unit
    ) {
        var lineStart = 0
        for (line in text.split('\n')) {
            val lineEnd = lineStart + line.length
            action(line, lineStart, lineEnd)
            lineStart = lineEnd + 1
        }
    }

    /** SOURCE MODE: colour markdown syntax in place without hiding any characters. */
    private fun highlightSource(originalText: String): TransformedText {
        val builder = AnnotatedString.Builder(originalText)
        forEachLine(originalText) { line, lineStart, lineEnd ->
            val trimmed = line.trimStart()
            headingStyle(trimmed)?.let { builder.styleHeading(lineStart, lineEnd, it) }
            builder.addInlineStyles(line, lineStart, BOLD_REGEX, sourceBoldStyle)
            builder.addInlineStyles(line, lineStart, CODE_REGEX, sourceCodeStyle)
            builder.addInlineStyles(line, lineStart, STRIKE_REGEX, sourceStrikeStyle)
            builder.addInlineStyles(line, lineStart, BULLET_REGEX, sourceBulletStyle)
            if (trimmed.startsWith(">")) builder.addStyle(sourceQuoteStyle, lineStart, lineEnd)
        }
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }

    /** Applies [style] to every [regex] match on a single line, offset into the whole text. */
    private fun AnnotatedString.Builder.addInlineStyles(
        line: String,
        lineStart: Int,
        regex: Regex,
        style: SpanStyle
    ) {
        for (match in regex.findAll(line)) {
            addStyle(style, lineStart + match.range.first, lineStart + match.range.last + 1)
        }
    }

    private val sourceBoldStyle = SpanStyle(color = theme.accent, fontWeight = FontWeight.Bold)
    private val sourceCodeStyle = SpanStyle(
        fontFamily = FontFamily.Monospace,
        color = theme.accent,
        background = theme.surface
    )
    private val sourceStrikeStyle =
        SpanStyle(textDecoration = TextDecoration.LineThrough, color = theme.subtext)
    private val sourceBulletStyle = SpanStyle(color = theme.accent, fontWeight = FontWeight.Bold)
    private val sourceQuoteStyle = SpanStyle(color = theme.subtext, fontStyle = FontStyle.Italic)

    /**
     * VISUAL MODE (WYSIWYG folding): builds a transformed string where markdown markers are
     * hidden and their effect rendered inline, owning the origin<->transformed offset maps.
     */
    private inner class VisualFold(private val originalText: String) {
        private val builder = AnnotatedString.Builder()
        private val origToTrans = IntArray(originalText.length + 1)
        private val transToOrigList = ArrayList<Int>(originalText.length + 1)
        private var origIdx = 0
        private var transIdx = 0

        fun build(): TransformedText {
            forEachLine(originalText) { line, _, lineEnd ->
                foldLine(line, lineEnd)
                if (lineEnd < originalText.length) appendVisible("\n")
            }
            origToTrans[origIdx] = transIdx
            transToOrigList.add(origIdx)
            return TransformedText(builder.toAnnotatedString(), buildOffsetMapping())
        }

        private fun foldLine(line: String, lineEnd: Int) {
            val alpha = alphaFor(lineEnd - line.length, lineEnd)
            val trimmed = line.trimStart()
            val indentationLength = line.length - trimmed.length
            val heading = headingStyle(trimmed)

            if (indentationLength > 0) appendVisible(line.take(indentationLength))
            val contentStart = transIdx
            if (heading != null) appendHidden(heading.markerLength)
            appendFoldedInline(lineEnd, alpha)
            if (heading != null) builder.styleHeading(contentStart, transIdx, heading, alpha)
        }

        private fun alphaFor(lineStart: Int, lineEnd: Int): Float {
            val hasCursor = cursorIndex in lineStart..lineEnd
            return if (isFocusMode && !hasCursor) 0.3f else 1f
        }

        private fun appendFoldedInline(lineEnd: Int, alpha: Float) {
            while (origIdx < lineEnd) {
                val marker = markerAt(origIdx)
                val closingIndex = marker?.let { closingIndexFor(it, lineEnd) }
                if (marker == null || closingIndex == null) {
                    appendPlainChar(alpha)
                    continue
                }
                appendHidden(marker.length)
                val contentStart = transIdx
                appendVisible(originalText.substring(origIdx, closingIndex))
                builder.addStyle(styleForMarker(marker, alpha), contentStart, transIdx)
                appendHidden(marker.length)
            }
        }

        private fun markerAt(index: Int): String? = when {
            originalText.startsWith("**", index) -> "**"
            originalText.startsWith("~~", index) -> "~~"
            originalText[index] == '`' -> "`"
            originalText[index] == '*' || originalText[index] == '_' -> originalText[index].toString()
            else -> null
        }

        private fun closingIndexFor(marker: String, lineEnd: Int): Int? {
            val searchStart = origIdx + marker.length
            return originalText.indexOf(marker, searchStart).takeIf { it in searchStart until lineEnd }
        }

        private fun styleForMarker(marker: String, alpha: Float): SpanStyle = when (marker) {
            "**" -> SpanStyle(fontWeight = FontWeight.Bold, color = theme.text.copy(alpha = alpha))
            "~~" -> SpanStyle(
                textDecoration = TextDecoration.LineThrough,
                color = theme.text.copy(alpha = alpha)
            )
            "`" -> SpanStyle(
                fontFamily = FontFamily.Monospace,
                color = theme.accent.copy(alpha = alpha),
                background = theme.surface
            )
            else -> SpanStyle(fontStyle = FontStyle.Italic, color = theme.text.copy(alpha = alpha))
        }

        private fun appendPlainChar(alpha: Float) {
            val plainStart = transIdx
            appendVisible(originalText[origIdx].toString())
            if (isFocusMode && alpha < 1f) {
                builder.addStyle(SpanStyle(color = theme.text.copy(alpha = alpha)), plainStart, transIdx)
            }
        }

        private fun appendVisible(str: String) {
            for (char in str) {
                origToTrans[origIdx] = transIdx
                transToOrigList.add(origIdx)
                origIdx++
                transIdx++
            }
            builder.append(str)
        }

        private fun appendHidden(count: Int) {
            repeat(count) {
                origToTrans[origIdx] = transIdx
                origIdx++
            }
        }

        private fun buildOffsetMapping(): OffsetMapping {
            val transToOrig = transToOrigList.toIntArray()
            return object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int = when {
                    offset < 0 -> 0
                    offset > originalText.length -> transIdx
                    else -> origToTrans[offset]
                }

                override fun transformedToOriginal(offset: Int): Int = when {
                    offset < 0 -> 0
                    offset >= transToOrig.size -> originalText.length
                    else -> transToOrig[offset]
                }
            }
        }
    }

    private companion object {
        val BOLD_REGEX = Regex("""\*\*.*?\*\*""")
        val CODE_REGEX = Regex("`[^`]*`")
        val STRIKE_REGEX = Regex("~~.*?~~")
        val BULLET_REGEX = Regex("""^(\s*[-*+]|\s*\d+\.)\s""")
    }
}
