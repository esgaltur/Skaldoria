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
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.skaldoria.markdown.parser.HeadingRules
import com.skaldoria.markdown.parser.MarkdownHighlightTokenizer
import com.skaldoria.markdown.parser.MarkdownTokenKind
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
        val fontSize: TextUnit,
        val lineHeight: TextUnit
    )

    /**
     * One heading authority for source mode, the active visual line, and folded visual lines.
     *
     * H1 used to be 32sp inside the field's fixed 27sp line height, which clipped its glyphs
     * and let adjacent lines collide. A paragraph style is required: font size is a span
     * concern, but vertical metrics belong to the paragraph.
     */
    private fun headingTypography(level: Int): Pair<TextUnit, TextUnit> = when (level) {
        1 -> 32.sp to 42.sp
        2 -> 24.sp to 34.sp
        3 -> 20.sp to 30.sp
        4 -> 18.sp to 28.sp
        5 -> 17.sp to 27.sp
        else -> 16.sp to 27.sp
    }

    /**
     * Visual mode also needs to know how many characters the marker occupies, so it can hide them.
     * Source mode does not, which is why the typography lookup above is separate.
     */
    private fun headingStyle(lineWithoutIndent: String): HeadingVisualStyle? {
        val heading = HeadingRules.heading(lineWithoutIndent) ?: return null
        val whitespaceLength = lineWithoutIndent
            .drop(heading.level)
            .takeWhile(Char::isWhitespace)
            .length
        val (fontSize, lineHeight) = headingTypography(heading.level)
        return HeadingVisualStyle(
            markerLength = heading.level + whitespaceLength,
            fontSize = fontSize,
            lineHeight = lineHeight
        )
    }

    private fun AnnotatedString.Builder.styleHeading(
        start: Int,
        end: Int,
        fontSize: TextUnit,
        lineHeight: TextUnit,
        alpha: Float = 1f
    ) {
        if (start >= end) return
        addStyle(
            SpanStyle(
                color = theme.accent.copy(alpha = alpha),
                fontWeight = FontWeight.Bold,
                fontSize = fontSize
            ),
            start,
            end
        )
        addStyle(ParagraphStyle(lineHeight = lineHeight), start, end)
    }

    private fun AnnotatedString.Builder.styleHeading(
        start: Int,
        end: Int,
        style: HeadingVisualStyle,
        alpha: Float = 1f
    ) = styleHeading(start, end, style.fontSize, style.lineHeight, alpha)

    /**
     * Source mode: colour the markdown without hiding any of it.
     *
     * The token scan is shared with the deck and CV editors via [MarkdownHighlightTokenizer]; only
     * the mapping onto [SkaldoriaTheme] is Writer's. Visual mode below cannot use it — folding
     * needs a non-identity `OffsetMapping`, which is a different problem and stays local.
     *
     * Two things changed when this stopped scanning for itself. Fenced blocks are now respected, so
     * `# comment` and `**` inside ```` ``` ```` are no longer styled as markdown; and the bullet
     * marker comes from the shared rule rather than a `Regex` recompiled on every line of every
     * keystroke.
     */
    private fun highlightSource(originalText: String): AnnotatedString {
        val builder = AnnotatedString.Builder(originalText)

        for (token in MarkdownHighlightTokenizer.tokenize(originalText)) {
            when (token.kind) {
                MarkdownTokenKind.Heading -> {
                    val (fontSize, lineHeight) = headingTypography(token.level)
                    builder.styleHeading(token.start, token.end, fontSize, lineHeight)
                }

                MarkdownTokenKind.Bold -> builder.addStyle(
                    SpanStyle(color = theme.accent, fontWeight = FontWeight.Bold),
                    token.start, token.end
                )

                MarkdownTokenKind.InlineCode -> builder.addStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        color = theme.accent,
                        background = theme.surface
                    ),
                    token.start, token.end
                )

                MarkdownTokenKind.Blockquote -> builder.addStyle(
                    SpanStyle(color = theme.subtext, fontStyle = FontStyle.Italic),
                    token.start, token.end
                )

                MarkdownTokenKind.BulletMarker -> builder.addStyle(
                    SpanStyle(color = theme.accent, fontWeight = FontWeight.Bold),
                    token.start, token.end
                )

                MarkdownTokenKind.Strikethrough -> builder.addStyle(
                    SpanStyle(textDecoration = TextDecoration.LineThrough, color = theme.subtext),
                    token.start, token.end
                )

                // Writer is a prose editor: code interiors, math, tables and directives are left
                // as plain text, the same as before this shared the tokenizer.
                else -> Unit
            }
        }

        return builder.toAnnotatedString()
    }

    override fun filter(text: AnnotatedString): TransformedText {
        val originalText = text.text
        if (originalText.isEmpty()) return TransformedText(text, OffsetMapping.Identity)

        if (!isVisualMode) return TransformedText(highlightSource(originalText), OffsetMapping.Identity)

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

            if (hasCursor) {
                // The caret's line shows its syntax so it can be edited — with the heading marker
                // as the deliberate exception.
                //
                // Revealing on "the caret is somewhere on this line" meant a document opened in
                // visual mode showed `# ` on its title, every time: the caret starts at offset 0
                // and offset 0 is the H1. The user asked for WYSIWYG and got markup on the one
                // line they were most likely looking at.
                //
                // The marker is instead revealed only while the caret is *inside* it, which is the
                // one moment it is being edited. Strictly inside, so that clicking at the start of
                // the title — which maps back to the first visible character — does not make the
                // marker reappear and shove the text sideways under the pointer. Left-arrow from
                // there steps into the marker and reveals it.
                val trimmedLine = line.trimStart()
                val indentationLength = line.length - trimmedLine.length
                val heading = headingStyle(trimmedLine)
                val markerStart = lineStart + indentationLength
                val editingMarker = heading != null &&
                    cursorIndex > markerStart &&
                    cursorIndex < markerStart + heading.markerLength

                if (heading != null && !editingMarker) {
                    if (indentationLength > 0) {
                        appendVisible(line.take(indentationLength))
                    }
                    val contentStart = transIdx
                    appendHidden(heading.markerLength)
                    // Inline markers stay raw: this is still the line being edited.
                    appendVisible(line.substring(indentationLength + heading.markerLength))
                    builder.styleHeading(contentStart, transIdx, heading, alpha)

                    if (isFocusMode) {
                        builder.addStyle(
                            SpanStyle(color = theme.text.copy(alpha = 1f)),
                            contentStart,
                            transIdx
                        )
                    }
                } else {
                    val tStart = transIdx
                    appendVisible(line)
                    heading?.let { style -> builder.styleHeading(tStart, transIdx, style, alpha) }

                    if (isFocusMode) {
                        builder.addStyle(SpanStyle(color = theme.text.copy(alpha = 1f)), tStart, transIdx)
                    }
                }
            } else {
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
