package com.skaldoria.core.parser

import com.skaldoria.core.layout.SmartLayoutClassifier
import com.skaldoria.core.models.FollowUpQuestion
import com.skaldoria.core.models.Slide
import com.skaldoria.core.models.SlideElement
import com.skaldoria.core.models.SlideLayoutType
import com.skaldoria.core.models.SlideTransition

/**
 * Pure Standard Markdown slide parser with support for directives and smart layout auto-classification.
 * Converts CommonMark / GFM markdown into structured, smart-layout slides.
 */
object MarkdownSlideParser {

    private val HR_REGEX = Regex("""^(\*{3,}|-{3,}|_{3,})\s*$""")
    private val HEADING_1_2_REGEX = Regex("""^(#{1,2})\s+(.+)$""")
    private val CODE_FENCE_START = Regex("""^```([a-zA-Z0-9_-]*)(?:\s*\[([0-9,\-|]+)\])?\s*$""")
    private val IMAGE_REGEX = Regex("""!\[(.*?)\]\((.*?)\)""")
    private val NOTE_COMMENT_REGEX = Regex("""<!--\s*(?:note|speaker):\s*(.*?)\s*-->""", RegexOption.IGNORE_CASE)
    private val NOTE_QUOTE_REGEX = Regex("""^>\s*note:\s*(.+)$""", RegexOption.IGNORE_CASE)
    private val DIRECTIVE_COMMENT_REGEX = Regex("""<!--\s*(layout|bg|background|transition|poll|vote):\s*(.*?)\s*-->""", RegexOption.IGNORE_CASE)
    private val DIRECTIVE_LINE_REGEX = Regex("""^(layout|bg|background|transition|poll|vote):\s*(.+)$""", RegexOption.IGNORE_CASE)

    /**
     * A big-metric value: a signed number, a percentage, an `x` multiplier, a currency
     * amount, or a number with a magnitude suffix — followed by a short label.
     *
     * COR-4: a bare integer is deliberately *not* enough. Requiring a unit is what stops
     * ordinary prose beginning with a year or a count from being promoted to a KPI slide.
     */
    private val METRIC_REGEX = Regex(
        """^([+\-~]\d+(?:[.,]\d+)?[%xX]?|\d+(?:[.,]\d+)?\s*[%]|\d+(?:[.,]\d+)?\s*[xX]|\d+(?:[.,]\d+)?\s*[MBKmbk]\b|[$€£]\s?\d+(?:[.,]\d+)?\s*[MBKmbk]?)\s+(.{3,30})$"""
    )

    /** A slide's raw source lines together with the inclusive range they occupied. */
    private data class RawSection(val lines: List<String>, val range: IntRange)

    fun parse(markdown: String): List<Slide> {
        val lines = markdown.lines()
        val rawSections = mutableListOf<RawSection>()
        var currentLines = mutableListOf<String>()
        var currentStart = -1

        // COR-1: the section boundaries computed here are recorded on each Slide as
        // sourceLineRange, so structural edits never have to re-derive them with a
        // second, divergent splitter.
        fun appendLine(index: Int, line: String) {
            if (currentStart < 0) currentStart = index
            currentLines.add(line)
        }

        fun flushSection(endExclusive: Int) {
            if (currentLines.any { it.isNotBlank() } && currentStart >= 0) {
                rawSections.add(RawSection(currentLines.toList(), currentStart..(endExclusive - 1)))
            }
            currentLines = mutableListOf()
            currentStart = -1
        }

        var inCodeFence = false

        for ((lineIndex, line) in lines.withIndex()) {
            val trimmed = line.trim()

            // Handle code block state
            if (trimmed.startsWith("```")) {
                inCodeFence = !inCodeFence
                appendLine(lineIndex, line)
                continue
            }

            if (inCodeFence) {
                appendLine(lineIndex, line)
                continue
            }

            // Slide split conditions: Horizontal Rule '---' or top-level headings '# ' or '## '
            val isHr = HR_REGEX.matches(trimmed)
            val isHeading1or2 = HEADING_1_2_REGEX.matches(trimmed)

            if (isHr) {
                // The rule itself belongs to no slide; it is regenerated on reassembly.
                flushSection(lineIndex)
                continue
            }

            if (isHeading1or2 && currentLines.any { it.isNotBlank() }) {
                // If the section already has content or a heading, split it
                val hasExistingHeading = currentLines.any { HEADING_1_2_REGEX.matches(it.trim()) }
                if (hasExistingHeading || currentLines.size > 2) {
                    flushSection(lineIndex)
                }
            }

            appendLine(lineIndex, line)
        }

        flushSection(lines.size)

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

        return rawSections.mapIndexed { index, section ->
            parseSlideSection(index, section.lines, isFirst = index == 0, sourceRange = section.range)
        }
    }

    private fun parseSlideSection(
        index: Int,
        lines: List<String>,
        isFirst: Boolean,
        sourceRange: IntRange = IntRange.EMPTY
    ): Slide {
        var title = ""
        var subtitle: String? = null
        val elements = mutableListOf<SlideElement>()
        val notes = mutableListOf<String>()
        var explicitLayout: SlideLayoutType? = null
        var customBackground: String? = null
        var customTransition: SlideTransition? = null

        var currentListItems = mutableListOf<String>()
        var inCodeBlock = false
        var currentCodeLang = "kotlin"
        var currentHighlightedLines = emptySet<Int>()
        var currentCodeLines = mutableListOf<String>()
        var inMathBlock = false
        var currentMathLines = mutableListOf<String>()
        var currentQuoteLines = mutableListOf<String>()
        var currentTableLines = mutableListOf<String>()

        fun flushMath() {
            if (currentMathLines.isNotEmpty()) {
                val fullFormula = currentMathLines.joinToString("\n").trim()
                if (fullFormula.isNotEmpty()) {
                    elements.add(SlideElement.MathFormula(formula = fullFormula, isBlock = true))
                }
                currentMathLines = mutableListOf()
                inMathBlock = false
            }
        }

        fun flushList() {
            if (currentListItems.isNotEmpty()) {
                elements.add(SlideElement.BulletList(currentListItems.toList()))
                currentListItems = mutableListOf()
            }
        }

        fun flushQuote() {
            if (currentQuoteLines.isNotEmpty()) {
                val fullQuote = currentQuoteLines.joinToString("\n")
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
                val fullCode = currentCodeLines.joinToString("\n")
                val lang = currentCodeLang.lowercase().trim()

                when (lang) {
                    "mermaid", "diagram", "flowchart", "sequence", "graph" -> {
                        elements.add(
                            SlideElement.MermaidDiagram(
                                code = fullCode,
                                diagramType = if (lang.isNotBlank()) lang else "flowchart"
                            )
                        )
                    }
                    "math", "latex", "katex", "tex", "equation" -> {
                        elements.add(
                            SlideElement.MathFormula(
                                formula = fullCode,
                                isBlock = true
                            )
                        )
                    }
                    else -> {
                        elements.add(
                            SlideElement.CodeBlock(
                                code = fullCode,
                                language = currentCodeLang.ifBlank { "kotlin" },
                                highlightedLines = currentHighlightedLines
                            )
                        )
                    }
                }
                currentCodeLines = mutableListOf()
                inCodeBlock = false
            }
        }

        for (line in lines) {
            val trimmed = line.trim()

            // 1. Directives in HTML comment: <!-- layout: hero -->, <!-- poll: A | B --> or <!-- bg: #12141f -->
            val directiveCommentMatch = DIRECTIVE_COMMENT_REGEX.find(trimmed)
            if (directiveCommentMatch != null) {
                val key = directiveCommentMatch.groupValues[1].lowercase()
                val value = directiveCommentMatch.groupValues[2].trim()
                if (key == "poll" || key == "vote") {
                    val rawOptions = value.split("|").map { it.trim() }.filter { it.isNotEmpty() }
                    if (rawOptions.isNotEmpty()) {
                        val question = if (title.isNotEmpty()) title else "Audience Poll"
                        elements.add(SlideElement.Poll(question = question, options = rawOptions))
                        explicitLayout = SlideLayoutType.POLL
                    }
                } else {
                    applyDirective(key, value, { explicitLayout = it }, { customBackground = it }, { customTransition = it })
                }
                continue
            }

            // 1b. Directives on direct line if before title (YAML-style)
            val directiveLineMatch = DIRECTIVE_LINE_REGEX.find(trimmed)
            if (directiveLineMatch != null && title.isEmpty()) {
                val key = directiveLineMatch.groupValues[1].lowercase()
                val value = directiveLineMatch.groupValues[2].trim()
                if (key == "poll" || key == "vote") {
                    val rawOptions = value.split("|").map { it.trim() }.filter { it.isNotEmpty() }
                    if (rawOptions.isNotEmpty()) {
                        val question = if (title.isNotEmpty()) title else "Audience Poll"
                        elements.add(SlideElement.Poll(question = question, options = rawOptions))
                        explicitLayout = SlideLayoutType.POLL
                    }
                } else {
                    applyDirective(key, value, { explicitLayout = it }, { customBackground = it }, { customTransition = it })
                }
                continue
            }

            // 1c. Check for Speaker Notes
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
                    flushMath()
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

            // 2b. Math Formula Block ($$)
            if (inMathBlock) {
                if (trimmed == "$$" || (trimmed.endsWith("$$") && !trimmed.startsWith("$$"))) {
                    val content = trimmed.removeSuffix("$$").trim()
                    if (content.isNotEmpty()) {
                        currentMathLines.add(content)
                    }
                    flushMath()
                } else {
                    currentMathLines.add(line)
                }
                continue
            }

            if (trimmed.startsWith("$$")) {
                flushList()
                flushQuote()
                flushTable()
                if (trimmed.endsWith("$$") && trimmed.length > 2) {
                    val formula = trimmed.removeSurrounding("$$").trim()
                    if (formula.isNotEmpty()) {
                        elements.add(SlideElement.MathFormula(formula = formula, isBlock = true))
                    }
                } else {
                    inMathBlock = true
                    currentMathLines = mutableListOf()
                    val rest = trimmed.removePrefix("$$").trim()
                    if (rest.isNotEmpty()) {
                        currentMathLines.add(rest)
                    }
                }
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
            if (headingMatch != null) {
                flushList()
                flushQuote()
                flushTable()
                val headingText = headingMatch.groupValues[2].trim()
                if (title.isEmpty()) {
                    title = headingText
                } else {
                    // COR-5: a second `#`/`##` in one section (possible when the split
                    // heuristic declines to split) used to fall through to the paragraph
                    // branch and render with its literal `##` markers still attached.
                    elements.add(SlideElement.Text(headingText, isLead = true))
                }
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
            //
            // COR-4: the value must carry a unit — a percent sign, a currency prefix, a
            // magnitude suffix, an explicit sign, or an `x` multiplier. Without that,
            // any paragraph opening with a number was captured, so an ordinary line like
            // "2024 Roadmap Overview" rendered as a full-slide KPI.
            val metricMatch = METRIC_REGEX.find(trimmed)
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
        flushMath()

        if (title.isEmpty()) {
            title = if (isFirst) "Presentation Title" else "Slide ${index + 1}"
        }

        val layout = explicitLayout ?: SmartLayoutClassifier.classify(title, elements, isFirst)

        return Slide(
            index = index,
            title = title,
            subtitle = subtitle,
            layoutType = layout,
            elements = elements,
            notes = notes,
            customBackground = customBackground,
            customTransition = customTransition,
            sourceLineRange = sourceRange
        )
    }

    private fun applyDirective(
        key: String,
        value: String,
        setLayout: (SlideLayoutType) -> Unit,
        setBg: (String) -> Unit,
        setTransition: (SlideTransition) -> Unit
    ) {
        when (key) {
            "layout" -> {
                when (value.lowercase().replace("-", "_").trim()) {
                    "hero", "title", "hero_title" -> setLayout(SlideLayoutType.HERO_TITLE)
                    "section", "header", "section_header" -> setLayout(SlideLayoutType.SECTION_HEADER)
                    "bullet", "bullets", "list", "bullet_list" -> setLayout(SlideLayoutType.BULLET_LIST)
                    "split_code", "split_text_code", "code_split" -> setLayout(SlideLayoutType.SPLIT_TEXT_CODE)
                    "split_media", "split_text_media", "media_split", "image_split" -> setLayout(SlideLayoutType.SPLIT_TEXT_MEDIA)
                    "table", "data_table", "grid" -> setLayout(SlideLayoutType.DATA_TABLE)
                    "quote", "big_quote" -> setLayout(SlideLayoutType.BIG_QUOTE)
                    "metric", "big_metric", "kpi" -> setLayout(SlideLayoutType.BIG_METRIC)
                    "code", "full_code", "terminal" -> setLayout(SlideLayoutType.FULL_CODE)
                    "diagram", "flowchart", "architecture", "mermaid" -> setLayout(SlideLayoutType.DIAGRAM)
                    "math", "latex", "formula", "equation" -> setLayout(SlideLayoutType.MATH_FORMULA)
                    "poll", "vote", "survey" -> setLayout(SlideLayoutType.POLL)
                }
            }
            "bg", "background" -> {
                if (value.isNotBlank()) setBg(value)
            }
            "transition" -> {
                when (value.lowercase().replace("-", "_").trim()) {
                    "fade" -> setTransition(SlideTransition.FADE)
                    "slide", "slide_horizontal" -> setTransition(SlideTransition.SLIDE_HORIZONTAL)
                    "zoom" -> setTransition(SlideTransition.ZOOM)
                    "vertical", "vertical_slide" -> setTransition(SlideTransition.VERTICAL_SLIDE)
                }
            }
        }
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

    private val PARKING_LOT_COMMENT_REGEX = Regex("""<!--\s*(?:parking-lot|parking_lot|followup|follow-up):\s*(\[([ xX])\])?\s*(.*?)\s*-->""", RegexOption.IGNORE_CASE)
    private val CHECKBOX_LINE_REGEX = Regex("""^-\s*\[([ xX])\]\s*(.+)$""")

    /**
     * Extracts unanswered questions and parking lot items from presentation markdown.
     * Supports both HTML comment directives (<!-- parking-lot: [ ] Question | Answer | slide:3 -->)
     * and markdown task lists.
     */
    fun extractFollowUpQuestions(markdown: String): List<FollowUpQuestion> {
        val result = mutableListOf<FollowUpQuestion>()
        val lines = markdown.lines()

        for (line in lines) {
            val trimmed = line.trim()

            // 1. Directive comments
            val commentMatch = PARKING_LOT_COMMENT_REGEX.find(trimmed)
            if (commentMatch != null) {
                val checkChar = commentMatch.groupValues[2]
                val isAnswered = checkChar.equals("x", ignoreCase = true)
                val rawBody = commentMatch.groupValues[3]
                val parts = rawBody.split("|").map { it.trim() }

                val question = parts.getOrNull(0) ?: ""
                var answer = ""
                var slideIdx: Int? = null

                if (parts.size >= 2) {
                    val p1 = parts[1]
                    if (p1.startsWith("slide:", ignoreCase = true)) {
                        slideIdx = p1.substringAfter(":").trim().toIntOrNull()
                    } else {
                        answer = p1
                    }
                }
                if (parts.size >= 3) {
                    val p2 = parts[2]
                    if (p2.startsWith("slide:", ignoreCase = true)) {
                        slideIdx = p2.substringAfter(":").trim().toIntOrNull()
                    } else if (answer.isEmpty()) {
                        answer = p2
                    }
                }

                if (question.isNotEmpty()) {
                    result.add(
                        FollowUpQuestion(
                            question = question,
                            isAnswered = isAnswered,
                            answerText = answer,
                            slideIndex = slideIdx
                        )
                    )
                }
                continue
            }

            // 2. Markdown task list lines in follow-up sections
            val checkMatch = CHECKBOX_LINE_REGEX.find(trimmed)
            if (checkMatch != null && (trimmed.contains("?", ignoreCase = true) || trimmed.contains("Answer:", ignoreCase = true) || trimmed.contains("—", ignoreCase = true))) {
                val isAnswered = checkMatch.groupValues[1].equals("x", ignoreCase = true)
                val body = checkMatch.groupValues[2]

                var question = body
                var answer = ""
                var slideIdx: Int? = null

                if (body.contains("—") || body.contains("--")) {
                    val split = body.split(Regex("—|--"))
                    question = split[0].trim()
                    answer = split.getOrNull(1)?.replace(Regex("""^\*?Answer:\*?\s*"""), "")?.trim()?.removeSurrounding("*", "*") ?: ""
                }

                val slideMatch = Regex("""\(Slide\s+(\d+)\)""", RegexOption.IGNORE_CASE).find(question)
                if (slideMatch != null) {
                    slideIdx = slideMatch.groupValues[1].toIntOrNull()?.minus(1)
                    question = question.replace(slideMatch.value, "").trim()
                }

                if (question.isNotEmpty()) {
                    result.add(
                        FollowUpQuestion(
                            question = question,
                            isAnswered = isAnswered,
                            answerText = answer,
                            slideIndex = slideIdx
                        )
                    )
                }
            }
        }

        return result
    }

    /**
     * Serializes follow-up questions to standard markdown directive comments or checklist.
     */
    fun serializeFollowUpQuestions(questions: List<FollowUpQuestion>): String {
        if (questions.isEmpty()) return ""
        val sb = StringBuilder()
        sb.append("\n\n<!-- ========================================= -->\n")
        sb.append("<!-- PRESENTATION PARKING LOT & FOLLOW-UP ITEMS -->\n")
        sb.append("<!-- ========================================= -->\n")
        for (q in questions) {
            val check = if (q.isAnswered) "[x]" else "[ ]"
            val slidePart = if (q.slideIndex != null) " | slide:${q.slideIndex + 1}" else ""
            val answerPart = if (q.answerText.isNotBlank()) " | ${q.answerText.replace("\n", " ")}" else ""
            sb.append("<!-- parking-lot: $check ${q.question}$answerPart$slidePart -->\n")
        }
        return sb.toString()
    }
}
