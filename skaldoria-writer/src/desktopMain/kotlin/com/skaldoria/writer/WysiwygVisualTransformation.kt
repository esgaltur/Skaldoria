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
    private fun headingTypography(level: Int): Pair<TextUnit, TextUnit> = WriterTypography.heading(level)

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

        /** Shows [str], or [shown] in its place — always character for character, so offsets map 1:1. */
        fun appendVisible(str: String, shown: String = str) {
            require(shown.length == str.length) { "a substitution must keep the length" }
            for (char in str) {
                origToTrans[origIdx] = transIdx
                transToOrigList.add(origIdx)
                origIdx++
                transIdx++
            }
            builder.append(shown)
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

        // Block structure comes from the shared tokenizer — the grammar authority the source view
        // and the other editors use — keyed by the offset of the line it starts. The fold below
        // used to scan characters for itself and knew nothing of fences: a ``` line was read as
        // inline code, two backticks were hidden and the third was left on screen.
        val lineBlocks = HashMap<Int, MarkdownTokenKind>()
        val bulletMarkerEnds = HashMap<Int, Int>()
        for (token in MarkdownHighlightTokenizer.tokenize(originalText)) {
            when (token.kind) {
                in VERBATIM_LINES, MarkdownTokenKind.ThematicBreak, MarkdownTokenKind.Blockquote ->
                    lineBlocks.putIfAbsent(token.start, token.kind)
                MarkdownTokenKind.BulletMarker -> bulletMarkerEnds[token.start] = token.end
                else -> Unit
            }
        }

        fun styleVerbatim(kind: MarkdownTokenKind, start: Int, end: Int, alpha: Float) {
            if (start >= end) return
            when (kind) {
                MarkdownTokenKind.FenceMarker -> builder.addStyle(
                    SpanStyle(fontFamily = FontFamily.Monospace, color = theme.subtext.copy(alpha = 0.6f * alpha)),
                    start, end
                )
                MarkdownTokenKind.CodeText -> builder.addStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        color = theme.text.copy(alpha = alpha),
                        background = theme.surface
                    ),
                    start, end
                )
                else -> Unit
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
                    lineBlocks[lineStart]?.let { kind -> styleVerbatim(kind, tStart, transIdx, 1f) }

                    if (isFocusMode) {
                        builder.addStyle(SpanStyle(color = theme.text.copy(alpha = 1f)), tStart, transIdx)
                    }
                }
            } else {
                val trimmedLine = line.trimStart()
                val indentationLength = line.length - trimmedLine.length
                val heading = headingStyle(trimmedLine)
                val block = lineBlocks[lineStart]
                val bulletEnd = bulletMarkerEnds[lineStart]

                when {
                    // Code, math, tables and directives are shown exactly as written: folding
                    // emphasis markers inside them would change what they say.
                    heading == null && block != null && block in VERBATIM_LINES -> {
                        val start = transIdx
                        appendVisible(line)
                        styleVerbatim(block, start, transIdx, alpha)
                    }

                    heading == null && block == MarkdownTokenKind.ThematicBreak -> {
                        val start = transIdx
                        appendVisible(line, RULE_CHAR.toString().repeat(line.length))
                        builder.addStyle(SpanStyle(color = theme.subtext.copy(alpha = 0.5f * alpha)), start, transIdx)
                    }

                    heading == null && block == MarkdownTokenKind.Blockquote -> {
                        if (indentationLength > 0) appendVisible(line.take(indentationLength))
                        // The `>` and the one space after it are the marker, as in CommonMark.
                        appendHidden(if (trimmedLine.startsWith("> ")) 2 else 1)
                        val start = transIdx
                        appendFoldedInline(lineEnd, alpha)
                        builder.addStyle(
                            SpanStyle(color = theme.subtext.copy(alpha = alpha), fontStyle = FontStyle.Italic),
                            start, transIdx
                        )
                    }

                    heading == null && bulletEnd != null -> {
                        val marker = originalText.substring(lineStart, bulletEnd)
                        val start = transIdx
                        // `-`, `*` and `+` show as the bullet the preview draws; a number stays a number.
                        appendVisible(marker, marker.replaceFirst(UNORDERED_MARKER, BULLET_CHAR.toString()))
                        builder.addStyle(
                            SpanStyle(color = theme.accent.copy(alpha = alpha), fontWeight = FontWeight.Bold),
                            start, transIdx
                        )
                        appendFoldedInline(lineEnd, alpha)
                    }

                    else -> {
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

/** Lines whose text is shown exactly as written in visual mode. */
private val VERBATIM_LINES = setOf(
    MarkdownTokenKind.FenceMarker,
    MarkdownTokenKind.CodeText,
    MarkdownTokenKind.MathBlock,
    MarkdownTokenKind.TableRow,
    MarkdownTokenKind.Directive
)

private const val BULLET_CHAR = '\u2022'
private const val RULE_CHAR = '\u2500'

/** The first `-`, `*` or `+` in a bullet marker the tokenizer has already identified. */
private val UNORDERED_MARKER = Regex("[-*+]")
