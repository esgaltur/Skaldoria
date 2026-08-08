package com.skaldoria.writer

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import com.skaldoria.shared.ui.theme.SkaldoriaTheme

class WysiwygVisualTransformation(
    private val theme: SkaldoriaTheme,
    private val cursorIndex: Int,
    private val isVisualMode: Boolean,
    private val isFocusMode: Boolean = false
) : VisualTransformation {

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

                // Headers
                if (trimmed.startsWith("# ")) {
                    builder.addStyle(
                        SpanStyle(color = theme.accent, fontWeight = FontWeight.Bold, fontSize = 32.sp),
                        lineStart, lineEnd
                    )
                } else if (trimmed.startsWith("## ")) {
                    builder.addStyle(
                        SpanStyle(color = theme.accent, fontWeight = FontWeight.Bold, fontSize = 24.sp),
                        lineStart, lineEnd
                    )
                } else if (trimmed.startsWith("### ")) {
                    builder.addStyle(
                        SpanStyle(color = theme.accent, fontWeight = FontWeight.Bold, fontSize = 20.sp),
                        lineStart, lineEnd
                    )
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

        val lines = originalText.split('\n')
        var lineStart = 0

        for (line in lines) {
            val lineEnd = lineStart + line.length
            val hasCursor = cursorIndex in lineStart..lineEnd
            val alpha = if (isFocusMode && !hasCursor) 0.3f else 1f

            if (hasCursor) {
                // If cursor is on this line, show it raw so the user can edit syntax
                val tStart = transIdx
                appendVisible(line)
                val trimmedLine = line.trimStart()
                if (trimmedLine.startsWith("# ")) {
                    builder.addStyle(SpanStyle(color = theme.accent.copy(alpha = alpha), fontWeight = FontWeight.Bold, fontSize = 32.sp), tStart, transIdx)
                } else if (trimmedLine.startsWith("## ")) {
                    builder.addStyle(SpanStyle(color = theme.accent.copy(alpha = alpha), fontWeight = FontWeight.Bold, fontSize = 24.sp), tStart, transIdx)
                } else if (trimmedLine.startsWith("### ")) {
                    builder.addStyle(SpanStyle(color = theme.accent.copy(alpha = alpha), fontWeight = FontWeight.Bold, fontSize = 20.sp), tStart, transIdx)
                }

                if (isFocusMode) {
                    builder.addStyle(SpanStyle(color = theme.text.copy(alpha = 1f)), tStart, transIdx)
                }
            } else {
                // HIDDEN SYNTAX MODE (WYSIWYG folding when cursor is elsewhere)
                val tStart = transIdx
                val trimmedLine = line.trimStart()

                if (trimmedLine.startsWith("# ")) {
                    val hashCount = line.length - line.trimStart().length + 2
                    appendHidden(hashCount)
                    appendVisible(line.substring(hashCount))
                    builder.addStyle(
                        SpanStyle(color = theme.accent.copy(alpha = alpha), fontWeight = FontWeight.Bold, fontSize = 32.sp),
                        tStart, transIdx
                    )
                } else if (trimmedLine.startsWith("## ")) {
                    val hashCount = line.length - line.trimStart().length + 3
                    appendHidden(hashCount)
                    appendVisible(line.substring(hashCount))
                    builder.addStyle(
                        SpanStyle(color = theme.accent.copy(alpha = alpha), fontWeight = FontWeight.Bold, fontSize = 24.sp),
                        tStart, transIdx
                    )
                } else if (trimmedLine.startsWith("### ")) {
                    val hashCount = line.length - line.trimStart().length + 4
                    appendHidden(hashCount)
                    appendVisible(line.substring(hashCount))
                    builder.addStyle(
                        SpanStyle(color = theme.accent.copy(alpha = alpha), fontWeight = FontWeight.Bold, fontSize = 20.sp),
                        tStart, transIdx
                    )
                } else if (line.contains("**")) {
                    var i = 0
                    while (i < line.length) {
                        if (i < line.length - 1 && line[i] == '*' && line[i + 1] == '*') {
                            val closeIdx = line.indexOf("**", i + 2)
                            if (closeIdx != -1) {
                                appendHidden(2) // hide opening **
                                val boldStart = transIdx
                                appendVisible(line.substring(i + 2, closeIdx))
                                builder.addStyle(SpanStyle(fontWeight = FontWeight.Bold, color = theme.text.copy(alpha = alpha)), boldStart, transIdx)
                                appendHidden(2) // hide closing **
                                i = closeIdx + 2
                            } else {
                                appendVisible(line[i].toString())
                                i++
                            }
                        } else {
                            appendVisible(line[i].toString())
                            i++
                        }
                    }
                } else if (line.contains('*') || line.contains('_')) {
                    var j = 0
                    while (j < line.length) {
                        val ch = line[j]
                        if ((ch == '*' || ch == '_') && (j + 1 >= line.length || line[j + 1] != ch)) {
                            val closeIdx = line.indexOf(ch, j + 1)
                            if (closeIdx > j + 1) {
                                appendHidden(1) // hide opening marker
                                val italicStart = transIdx
                                appendVisible(line.substring(j + 1, closeIdx))
                                builder.addStyle(SpanStyle(fontStyle = FontStyle.Italic, color = theme.text.copy(alpha = alpha)), italicStart, transIdx)
                                appendHidden(1) // hide closing marker
                                j = closeIdx + 1
                                continue
                            }
                        }
                        appendVisible(line[j].toString())
                        j++
                    }
                } else if (line.contains('`')) {
                    var j = 0
                    while (j < line.length) {
                        if (line[j] == '`') {
                            val closeIdx = line.indexOf('`', j + 1)
                            if (closeIdx > j) {
                                appendHidden(1) // hide opening backtick
                                val codeStart = transIdx
                                appendVisible(line.substring(j + 1, closeIdx))
                                builder.addStyle(
                                    SpanStyle(
                                        fontFamily = FontFamily.Monospace,
                                        color = theme.accent,
                                        background = theme.surface
                                    ),
                                    codeStart, transIdx
                                )
                                appendHidden(1) // hide closing backtick
                                j = closeIdx + 1
                                continue
                            }
                        }
                        appendVisible(line[j].toString())
                        j++
                    }
                } else if (line.contains("~~")) {
                    var j = 0
                    while (j < line.length) {
                        if (j < line.length - 1 && line[j] == '~' && line[j + 1] == '~') {
                            val closeIdx = line.indexOf("~~", j + 2)
                            if (closeIdx > j + 2) {
                                appendHidden(2) // hide opening ~~
                                val strikeStart = transIdx
                                appendVisible(line.substring(j + 2, closeIdx))
                                builder.addStyle(
                                    SpanStyle(
                                        textDecoration = TextDecoration.LineThrough,
                                        color = theme.text.copy(alpha = alpha)
                                    ),
                                    strikeStart, transIdx
                                )
                                appendHidden(2) // hide closing ~~
                                j = closeIdx + 2
                                continue
                            }
                        }
                        appendVisible(line[j].toString())
                        j++
                    }
                } else {
                    val normalStart = transIdx
                    appendVisible(line)
                    if (isFocusMode && !hasCursor) {
                        builder.addStyle(SpanStyle(color = theme.text.copy(alpha = alpha)), normalStart, transIdx)
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
