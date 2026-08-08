package com.skaldoria.writer.parser

import com.skaldoria.markdown.parser.FenceInfo
import com.skaldoria.markdown.parser.FenceRules
import com.skaldoria.markdown.parser.HeadingRules
import com.skaldoria.markdown.parser.MathRules
import com.skaldoria.markdown.parser.ThematicBreakRules

/**
 * A bespoke Document Parser engineered for Skaldoria Writer.
 * 
 * Adheres to SOLID principles:
 * - Single Responsibility: It builds a generic Document AST from lines of text.
 * - Open/Closed: Reuses shared syntax matchers (FenceRules, HeadingRules) without modifying them.
 * - Dependency Inversion: Defers syntax definition to the shared `:skaldoria-markdown` rules.
 */
class DocumentParser {

    fun parse(markdown: String): Document {
        val lines = markdown.lines()
        val blocks = mutableListOf<BlockNode>()
        
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()
            
            // 1. Thematic Break
            if (ThematicBreakRules.isThematicBreak(line)) {
                blocks.add(ThematicBreak(trimmed))
                i++
                continue
            }
            
            // 2. Heading
            val headingInfo = HeadingRules.heading(line)
            if (headingInfo != null) {
                blocks.add(Heading(headingInfo.level, headingInfo.text, parseInline(headingInfo.text)))
                i++
                continue
            }
            
            // 3. Code Blocks
            val fenceOpening = FenceRules.openingFence(trimmed)
            if (fenceOpening != null) {
                val language = fenceOpening.language
                val codeContent = StringBuilder()
                i++
                var isClosed = false
                while (i < lines.size) {
                    val currentTrimmed = lines[i].trim()
                    if (FenceRules.closes(currentTrimmed, fenceOpening)) {
                        isClosed = true
                        i++ // skip closing fence
                        break
                    }
                    codeContent.append(lines[i]).append("\n")
                    i++
                }
                blocks.add(CodeBlock(language, codeContent.toString().removeSuffix("\n")))
                continue
            }

            // 4. Math Blocks
            if (MathRules.opensBlock(line)) {
                val mathContent = StringBuilder()
                i++
                var isClosed = false
                while (i < lines.size) {
                    val currentTrimmed = lines[i].trim()
                    if (MathRules.closesBlock(currentTrimmed)) {
                        isClosed = true
                        i++ // skip closing $$
                        break
                    }
                    mathContent.append(lines[i]).append("\n")
                    i++
                }
                blocks.add(MathBlock(mathContent.toString().removeSuffix("\n")))
                continue
            }
            
            // 5. Single-line Math
            if (MathRules.isSingleLine(line)) {
                // Strip $$
                val content = trimmed.removeSurrounding("$$").trim()
                blocks.add(MathBlock(content))
                i++
                continue
            }
            
            // 6. Blockquotes
            if (trimmed.startsWith(">")) {
                val quoteLines = mutableListOf<String>()
                while (i < lines.size) {
                    val currentTrimmed = lines[i].trim()
                    if (!currentTrimmed.startsWith(">")) break
                    quoteLines.add(currentTrimmed.removePrefix(">").trim())
                    i++
                }
                val innerDoc = parse(quoteLines.joinToString("\n"))
                blocks.add(Blockquote(innerDoc.blocks))
                continue
            }

            // 7. Lists (bullet and ordered)
            val listBulletRegex = Regex("""^[-*+]\s+(.+)$""")
            val listNumberedRegex = Regex("""^\d+\.\s+(.+)$""")
            val firstListMatch = listBulletRegex.find(trimmed) ?: listNumberedRegex.find(trimmed)
            if (firstListMatch != null) {
                val items = mutableListOf<String>()
                val isOrdered = listNumberedRegex.containsMatchIn(trimmed)
                while (i < lines.size) {
                    val currentTrimmed = lines[i].trim()
                    val match = listBulletRegex.find(currentTrimmed) ?: listNumberedRegex.find(currentTrimmed)
                    if (match != null) {
                        items.add(match.groupValues[1].trim())
                        i++
                    } else {
                        break
                    }
                }
                blocks.add(BulletList(items, isOrdered))
                continue
            }
            
            // 8. Blank lines
            if (trimmed.isEmpty()) {
                i++
                continue
            }
            
            // 7. Paragraphs
            val paraText = StringBuilder()
            while (i < lines.size) {
                val currentLine = lines[i]
                val currentTrimmed = currentLine.trim()
                
                // Break paragraph on new block elements
                if (currentTrimmed.isEmpty() || 
                    ThematicBreakRules.isThematicBreak(currentLine) || 
                    HeadingRules.heading(currentLine) != null || 
                    FenceRules.openingFence(currentTrimmed) != null ||
                    MathRules.opensBlock(currentLine) ||
                    MathRules.isSingleLine(currentLine) ||
                    currentTrimmed.startsWith(">") ||
                    Regex("""^[-*+]\s+(.+)$""").matches(currentTrimmed) ||
                    Regex("""^\d+\.\s+(.+)$""").matches(currentTrimmed)
                ) {
                    break
                }
                
                paraText.append(currentLine).append("\n")
                i++
            }
            blocks.add(Paragraph(parseInline(paraText.toString().trimEnd())))
        }
        
        return Document(blocks)
    }
    
    private fun parseInline(text: String): List<InlineNode> {
        val nodes = mutableListOf<InlineNode>()
        val buffer = StringBuilder()
        var i = 0

        fun flushBuffer() {
            if (buffer.isNotEmpty()) {
                nodes.add(Text(buffer.toString()))
                buffer.clear()
            }
        }

        while (i < text.length) {
            val c = text[i]

            // Inline code: `...`
            if (c == '`') {
                val end = text.indexOf('`', i + 1)
                if (end > i) {
                    flushBuffer()
                    nodes.add(Code(text.substring(i + 1, end)))
                    i = end + 1
                    continue
                }
            }

            // Strikethrough: ~~...~~
            if (c == '~' && i + 1 < text.length && text[i + 1] == '~') {
                val end = text.indexOf("~~", i + 2)
                if (end > i + 2) {
                    flushBuffer()
                    nodes.add(Strikethrough(parseInline(text.substring(i + 2, end))))
                    i = end + 2
                    continue
                }
            }

            // Bold: **...** or __...__
            if ((c == '*' || c == '_') && i + 1 < text.length && text[i + 1] == c) {
                val marker = "$c$c"
                val end = text.indexOf(marker, i + 2)
                if (end > i + 2) {
                    flushBuffer()
                    nodes.add(Bold(parseInline(text.substring(i + 2, end))))
                    i = end + 2
                    continue
                }
            }

            // Italic: *...* or _..._ (single marker, not part of bold)
            if ((c == '*' || c == '_') && text.getOrNull(i + 1) != c) {
                val end = text.indexOf(c, i + 1)
                if (end > i + 1) {
                    flushBuffer()
                    nodes.add(Italic(parseInline(text.substring(i + 1, end))))
                    i = end + 1
                    continue
                }
            }

            buffer.append(c)
            i++
        }

        flushBuffer()
        return nodes
    }
}
