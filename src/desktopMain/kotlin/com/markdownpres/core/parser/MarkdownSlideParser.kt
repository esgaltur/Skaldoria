package com.markdownpres.core.parser

import com.markdownpres.core.layout.SmartLayoutClassifier
import com.markdownpres.core.models.Slide
import com.markdownpres.core.models.SlideElement
import com.markdownpres.core.models.SlideLayoutType

/**
 * Pure Standard Markdown slide parser.
 * Converts CommonMark / GFM markdown into structured, smart-layout slides
 * without requiring any proprietary presentation syntax.
 */
object MarkdownSlideParser {

    private val HR_REGEX = Regex("""^(\*{3,}|-{3,}|_{3,})\s*$""")
    private val HEADING_1_2_REGEX = Regex("""^(#{1,2})\s+(.+)$""")
    private val CODE_FENCE_START = Regex("""^```([a-zA-Z0-9_-]*)(?:\s*\[([0-9,\-|]+)\])?\s*$""")
    private val IMAGE_REGEX = Regex("""!\[(.*?)\]\((.*?)\)""")
    private val NOTE_COMMENT_REGEX = Regex("""<!--\s*(?:note|speaker):\s*(.*?)\s*-->""", RegexOption.IGNORE_CASE)
    private val NOTE_QUOTE_REGEX = Regex("""^>\s*note:\s*(.+)$""", RegexOption.IGNORE_CASE)

    fun parse(markdown: String): List<Slide> {
        val lines = markdown.lines()
        val rawSections = mutableListOf<List<String>>()
        var currentSection = mutableListOf<String>()

        var inCodeFence = false

        for (line in lines) {
            val trimmed = line.trim()

            // Handle code block state
            if (trimmed.startsWith("```")) {
                inCodeFence = !inCodeFence
                currentSection.add(line)
                continue
            }

            if (inCodeFence) {
                currentSection.add(line)
                continue
            }

            // Slide split conditions: Horizontal Rule '---' or top-level headings '# ' or '## '
            val isHr = HR_REGEX.matches(trimmed)
            val isHeading1or2 = HEADING_1_2_REGEX.matches(trimmed)

            if (isHr) {
                if (currentSection.any { it.isNotBlank() }) {
                    rawSections.add(currentSection)
                    currentSection = mutableListOf()
                }
                continue
            }

            if (isHeading1or2 && currentSection.any { it.isNotBlank() }) {
                // If the section already has content or a heading, split it
                val hasExistingHeading = currentSection.any { HEADING_1_2_REGEX.matches(it.trim()) }
                if (hasExistingHeading || currentSection.size > 2) {
                    rawSections.add(currentSection)
                    currentSection = mutableListOf()
                }
            }

            currentSection.add(line)
        }

        if (currentSection.any { it.isNotBlank() }) {
            rawSections.add(currentSection)
        }

        // If markdown was completely empty
        if (rawSections.isEmpty()) {
            return listOf(
                Slide(
                    index = 0,
                    title = "Untitled Presentation",
                    layoutType = SlideLayoutType.HERO_TITLE,
                    elements = listOf(SlideElement.Text("Add content to begin", isLead = true))
                )
            )
        }

        return rawSections.mapIndexed { index, sectionLines ->
            parseSlideSection(index, sectionLines, isFirst = index == 0)
        }
    }

    private fun parseSlideSection(index: Int, lines: List<String>, isFirst: Boolean): Slide {
        var title = ""
        var subtitle: String? = null
        val elements = mutableListOf<SlideElement>()
        val notes = mutableListOf<String>()

        var currentListItems = mutableListOf<String>()
        var inCodeBlock = false
        var currentCodeLang = "kotlin"
        var currentHighlightedLines = emptySet<Int>()
        var currentCodeLines = mutableListOf<String>()
        var currentQuoteLines = mutableListOf<String>()
        var currentTableLines = mutableListOf<String>()

        fun flushList() {
            if (currentListItems.isNotEmpty()) {
                elements.add(SlideElement.BulletList(currentListItems.toList()))
                currentListItems = mutableListOf()
            }
        }

        fun flushQuote() {
            if (currentQuoteLines.isNotEmpty()) {
                val fullQuote = currentQuoteLines.joinToString("\n")
                // Check if there is an author citation at the end (e.g., - Author or -- Author)
                val parts = fullQuote.split(Regex("""\n\s*--?\s*"""))
                if (parts.size >= 2) {
                    elements.add(SlideElement.Quote(parts[0].trim(), parts[1].trim()))
                } else {
                    elements.add(SlideElement.Quote(fullQuote.trim()))
                }
                currentQuoteLines = mutableListOf()
            }
        }

        fun flushTable() {
            if (currentTableLines.size >= 2) {
                val splitCells = { raw: String ->
                    val clean = raw.trim().removeSurrounding("|", "|")
                    clean.split("|").map { it.trim() }
                }
                val rawHeaders = splitCells(currentTableLines[0])
                val rows = mutableListOf<List<String>>()
                for (i in 1 until currentTableLines.size) {
                    val rowLine = currentTableLines[i].trim()
                    if (rowLine.replace(Regex("[|:\\-\\s]"), "").isEmpty()) {
                        continue
                    }
                    rows.add(splitCells(rowLine))
                }
                if (rawHeaders.isNotEmpty()) {
                    elements.add(SlideElement.Table(headers = rawHeaders, rows = rows))
                }
                currentTableLines = mutableListOf()
            } else if (currentTableLines.isNotEmpty()) {
                for (l in currentTableLines) {
                    elements.add(SlideElement.Text(l))
                }
                currentTableLines = mutableListOf()
            }
        }

        fun flushCode() {
            if (currentCodeLines.isNotEmpty() || inCodeBlock) {
                elements.add(
                    SlideElement.CodeBlock(
                        code = currentCodeLines.joinToString("\n"),
                        language = currentCodeLang.ifBlank { "kotlin" },
                        highlightedLines = currentHighlightedLines
                    )
                )
                currentCodeLines = mutableListOf()
                inCodeBlock = false
            }
        }

        for (line in lines) {
            val trimmed = line.trim()

            // 1. Check for Speaker Notes
            val noteCommentMatch = NOTE_COMMENT_REGEX.find(trimmed)
            if (noteCommentMatch != null) {
                notes.add(noteCommentMatch.groupValues[1])
                continue
            }

            val noteQuoteMatch = NOTE_QUOTE_REGEX.find(trimmed)
            if (noteQuoteMatch != null) {
                notes.add(noteQuoteMatch.groupValues[1])
                continue
            }

            // 2. Code Block
            if (trimmed.startsWith("```")) {
                if (inCodeBlock) {
                    flushCode()
                } else {
                    flushList()
                    flushQuote()
                    flushTable()
                    inCodeBlock = true
                    val fenceMatch = CODE_FENCE_START.find(trimmed)
                    currentCodeLang = fenceMatch?.groupValues?.getOrNull(1) ?: ""
                    val lineHighlightsStr = fenceMatch?.groupValues?.getOrNull(2)
                    currentHighlightedLines = parseLineHighlights(lineHighlightsStr)
                    currentCodeLines = mutableListOf()
                }
                continue
            }

            if (inCodeBlock) {
                currentCodeLines.add(line)
                continue
            }

            // 3. Markdown Tables (lines containing pipes)
            val isTableLine = (trimmed.startsWith("|") && trimmed.endsWith("|")) || (trimmed.contains("|") && trimmed.contains("-|-"))
            if (isTableLine) {
                flushList()
                flushQuote()
                currentTableLines.add(trimmed)
                continue
            } else {
                flushTable()
            }

            // 4. Headings
            val headingMatch = HEADING_1_2_REGEX.find(trimmed)
            if (headingMatch != null && title.isEmpty()) {
                flushList()
                flushQuote()
                flushTable()
                title = headingMatch.groupValues[2].trim()
                continue
            }

            val h3Match = Regex("""^###\s+(.+)$""").find(trimmed)
            if (h3Match != null) {
                if (title.isEmpty()) {
                    title = h3Match.groupValues[1].trim()
                } else if (subtitle == null) {
                    subtitle = h3Match.groupValues[1].trim()
                } else {
                    flushList()
                    flushQuote()
                    flushTable()
                    elements.add(SlideElement.Text(h3Match.groupValues[1].trim(), isLead = true))
                }
                continue
            }

            // 5. Bullet lists
            val listMatch = Regex("""^[-*+]\s+(.+)$""").find(trimmed)
                ?: Regex("""^\d+\.\s+(.+)$""").find(trimmed)
            if (listMatch != null) {
                flushQuote()
                flushTable()
                currentListItems.add(listMatch.groupValues[1].trim())
                continue
            } else {
                flushList()
            }

            // 6. Block quotes
            if (trimmed.startsWith(">")) {
                flushTable()
                val quoteText = trimmed.removePrefix(">").trim()
                currentQuoteLines.add(quoteText)
                continue
            } else {
                flushQuote()
            }

            // 7. Standalone Image
            val imageMatch = IMAGE_REGEX.find(trimmed)
            if (imageMatch != null) {
                flushTable()
                val alt = imageMatch.groupValues[1]
                val url = imageMatch.groupValues[2]
                elements.add(SlideElement.Image(url = url, altText = alt))
                continue
            }

            // 8. Standalone Big Metric (e.g. "99.99% Uptime" or "+140% Growth")
            val metricMatch = Regex("""^([+\-~]?\d+(?:\.\d+)?%?|\$\d+(?:\.\d+)?[MBK]?)\s+(.{3,30})$""").find(trimmed)
            if (metricMatch != null && elements.isEmpty()) {
                flushTable()
                elements.add(SlideElement.Metric(metricMatch.groupValues[1], metricMatch.groupValues[2]))
                continue
            }

            // 9. Regular text paragraphs
            if (trimmed.isNotBlank()) {
                flushTable()
                if (title.isEmpty()) {
                    title = trimmed
                } else {
                    elements.add(SlideElement.Text(trimmed))
                }
            }
        }

        // Flush any pending elements
        flushList()
        flushQuote()
        flushTable()
        flushCode()

        if (title.isEmpty()) {
            title = if (isFirst) "Presentation Title" else "Slide ${index + 1}"
        }

        val layout = SmartLayoutClassifier.classify(title, elements, isFirst)

        return Slide(
            index = index,
            title = title,
            subtitle = subtitle,
            layoutType = layout,
            elements = elements,
            notes = notes
        )
    }

    private fun parseLineHighlights(str: String?): Set<Int> {
        if (str.isNullOrBlank()) return emptySet()
        val result = mutableSetOf<Int>()
        for (part in str.split(Regex("[,|]"))) {
            val rangeMatch = Regex("""(\d+)-(\d+)""").find(part.trim())
            if (rangeMatch != null) {
                val start = rangeMatch.groupValues[1].toIntOrNull() ?: continue
                val end = rangeMatch.groupValues[2].toIntOrNull() ?: continue
                for (i in start..end) result.add(i)
            } else {
                part.trim().toIntOrNull()?.let { result.add(it) }
            }
        }
        return result
    }
}
