package com.skaldoria.core.parser

import com.skaldoria.core.layout.SmartLayoutClassifier
import com.skaldoria.core.models.FollowUpQuestion
import com.skaldoria.core.models.Slide
import com.skaldoria.core.models.SlideElement
import com.skaldoria.core.models.SlideLayoutType
import com.skaldoria.core.models.SlideTransition
import java.util.UUID

/**
 * Pure Standard Markdown slide parser with support for directives and smart layout auto-classification.
 * Converts CommonMark / GFM markdown into structured, smart-layout slides.
 */
object MarkdownSlideParser {

    internal val HR_REGEX = Regex("""^(\*{3,}|-{3,}|_{3,})\s*$""")
    internal val HEADING_1_2_REGEX = Regex("""^(#{1,2})\s+(.+)$""")
    /**
     * Public, deliberately: this is the seam between the parser and the editor's highlighter.
     *
     * It went from `internal` to public when `:markdown-core` was extracted, because
     * `FenceLexerDivergenceTest` lives in the app module — it has to reach both this and the
     * Compose-dependent `MarkdownVisualTransformation` to assert the two agree.
     *
     * Phase B replaces it with a shared `FenceRules` primitive that both callers use, at which
     * point fence recognition becomes a real public API of this module rather than a regex the
     * highlighter happens to be allowed to see. See `docs/MARKDOWN_UNIFICATION_PLAN.md`.
     */
    val CODE_FENCE_START = Regex("""^```([a-zA-Z0-9_-]*)(?:\s*\[([0-9,\-|]+)\])?\s*$""")
    internal val IMAGE_REGEX = Regex("""!\[(.*?)\]\((.*?)\)""")
    internal val NOTE_COMMENT_REGEX = Regex("""<!--\s*(?:note|speaker):\s*(.*?)\s*-->""", RegexOption.IGNORE_CASE)
    internal val NOTE_QUOTE_REGEX = Regex("""^>\s*note:\s*(.+)$""", RegexOption.IGNORE_CASE)
    internal val DIRECTIVE_COMMENT_REGEX = Regex("""<!--\s*(layout|bg|background|transition|poll|vote):\s*(.*?)\s*-->""", RegexOption.IGNORE_CASE)
    internal val DIRECTIVE_LINE_REGEX = Regex("""^(layout|bg|background|transition|poll|vote):\s*(.+)$""", RegexOption.IGNORE_CASE)

    /**
     * A big-metric value: a signed number, a percentage, an `x` multiplier, a currency
     * amount, or a number with a magnitude suffix — followed by a short label.
     *
     * COR-4: a bare integer is deliberately *not* enough. Requiring a unit is what stops
     * ordinary prose beginning with a year or a count from being promoted to a KPI slide.
     */
    internal val METRIC_REGEX = Regex(
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

    /**
     * Parses one slide section by running each line through [BLOCK_RULES].
     *
     * F-17: this was a 364-line function holding twelve mutable locals, five nested closures
     * and a nine-branch `if … continue` chain whose *ordering* was the specification but was
     * nowhere stated. Every branch also opened with two to four `flushX()` calls, and missing
     * one silently dropped an element.
     *
     * Now: the ordering is [BLOCK_RULES], the accumulating state is [SectionContext], and the
     * flush ritual happens **once, here**, driven by each rule's own declaration of whether
     * it closes open blocks.
     */
    private fun parseSlideSection(
        index: Int,
        lines: List<String>,
        isFirst: Boolean,
        sourceRange: IntRange = IntRange.EMPTY
    ): Slide {
        val context = SectionContext()

        for (raw in lines) {
            val line = raw.trim()
            val rule = BLOCK_RULES.firstOrNull { it.matches(line, context) } ?: continue
            if (rule.flushesPendingBlocks) context.flushPending()
            rule.consume(line, raw, context)
        }

        context.flushAll()

        val title = context.title.ifEmpty { if (isFirst) "Presentation Title" else "Slide ${index + 1}" }

        return Slide(
            index = index,
            title = title,
            subtitle = context.subtitle,
            layoutType = context.directives.layout
                ?: SmartLayoutClassifier.classify(title, context.elements, isFirst),
            elements = context.elements,
            notes = context.notes,
            customBackground = context.directives.background,
            customTransition = context.directives.transition,
            sourceLineRange = sourceRange
        )
    }

    /**
     * The options a `poll:` / `vote:` directive declares, or null when it declares none.
     *
     * F-15: the splitting and filtering below was copy-pasted into both the comment-form and
     * line-form branches of the parse loop.
     */
    private fun parsePollOptions(value: String): List<String>? =
        value.split("|")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .takeIf { it.isNotEmpty() }

    /**
     * Applies one `key: value` directive to [directives].
     *
     * @param pollQuestion the question to attach if this directive declares a poll — the
     *   slide title when it already has one, otherwise a generic label.
     * @return the poll element this directive declares, or null. Returning it keeps the two
     *   call sites (HTML comment and bare line) sharing one implementation; they previously
     *   held identical seven-line copies that could drift apart.
     */
    internal fun applyDirective(
        key: String,
        value: String,
        directives: SectionDirectives,
        pollQuestion: String
    ): SlideElement.Poll? {
        when (key) {
            "poll", "vote" -> {
                val options = parsePollOptions(value) ?: return null
                directives.layout = SlideLayoutType.POLL
                return SlideElement.Poll(question = pollQuestion, options = options)
            }
            "layout" -> {
                val setLayout: (SlideLayoutType) -> Unit = { directives.layout = it }
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
                if (value.isNotBlank()) directives.background = value
            }
            "transition" -> {
                val setTransition: (SlideTransition) -> Unit = { directives.transition = it }
                when (value.lowercase().replace("-", "_").trim()) {
                    "fade" -> setTransition(SlideTransition.FADE)
                    "slide", "slide_horizontal" -> setTransition(SlideTransition.SLIDE_HORIZONTAL)
                    "zoom" -> setTransition(SlideTransition.ZOOM)
                    "vertical", "vertical_slide" -> setTransition(SlideTransition.VERTICAL_SLIDE)
                }
            }
        }
        return null
    }

    internal fun parseLineHighlights(str: String?): Set<Int> {
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
    /**
     * Parses a single `<!-- parking-lot: … -->` line, or null when [trimmedLine] is not one.
     *
     * Shared by extraction and rewriting so both derive [FollowUpQuestion.directiveKey] the
     * same way. Keeping two copies of this logic is exactly how a rewrite ended up unable to
     * match the directives it had just read, silently deleting them.
     *
     * Fields after the question are matched by prefix, not position, so `slide:`, `id:` and
     * the answer can appear in any order and a directive authored without an id still works.
     */
    fun parseFollowUpDirective(trimmedLine: String): FollowUpQuestion? {
        val match = PARKING_LOT_COMMENT_REGEX.find(trimmedLine) ?: return null

        val isAnswered = match.groupValues[2].equals("x", ignoreCase = true)
        val parts = match.groupValues[3].split("|").map { it.trim() }
        val question = parts.firstOrNull().orEmpty()
        if (question.isEmpty()) return null

        var answer = ""
        var slideIdx: Int? = null
        var persistedId: String? = null

        for (part in parts.drop(1)) {
            when {
                part.startsWith("slide:", ignoreCase = true) ->
                    slideIdx = part.substringAfter(":").trim().toIntOrNull()
                part.startsWith("id:", ignoreCase = true) ->
                    persistedId = part.substringAfter(":").trim().ifBlank { null }
                answer.isEmpty() -> answer = part
            }
        }

        return FollowUpQuestion(
            // A persisted id makes identity survive a round trip through the file, so
            // renaming a question is an edit rather than a delete plus a create.
            id = persistedId ?: UUID.randomUUID().toString(),
            question = question,
            isAnswered = isAnswered,
            answerText = answer,
            slideIndex = slideIdx,
            isFromMarkdown = true,
            hasPersistedId = persistedId != null
        )
    }

    fun extractFollowUpQuestions(markdown: String): List<FollowUpQuestion> {
        val result = mutableListOf<FollowUpQuestion>()
        val lines = markdown.lines()

        for (line in lines) {
            val trimmed = line.trim()

            // 1. Directive comments
            val directive = parseFollowUpDirective(trimmed)
            if (directive != null) {
                result.add(directive)
                continue
            }
            if (PARKING_LOT_COMMENT_REGEX.containsMatchIn(trimmed)) continue

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
                            slideIndex = slideIdx,
                            isFromMarkdown = true
                        )
                    )
                }
            }
        }

        return result
    }

    /**
     * Rewrites the `<!-- parking-lot: … -->` directives in [markdown] to match [items].
     *
     * This is the missing list → markdown direction. Extraction was one-way, so deleting a
     * directive-sourced item only changed the in-memory list: the comment stayed in the file
     * and the next re-parse brought the question back.
     *
     * Directives are edited **in place** rather than stripped and re-emitted as a block, so
     * a `<!-- parking-lot: … -->` authored next to the slide it refers to stays there.
     * Matching is by [FollowUpQuestion.directiveKey] — the question text — because the `id`
     * is regenerated on every parse and cannot survive a round trip.
     *
     * A directive with no corresponding item is dropped (the user deleted it); one that is
     * still present is re-emitted so answer text and the checkbox reflect the current state.
     */
    fun rewriteFollowUpDirectives(markdown: String, items: List<FollowUpQuestion>): String {
        val byKey = items.associateBy { it.directiveKey }
        var changed = false

        val rewritten = markdown.lines().mapNotNull { line ->
            // Parsed with the same helper the extractor uses, so the two can never disagree
            // about how a directive maps to a key — they did briefly, and every rewrite
            // silently dropped directives it failed to match.
            val parsed = parseFollowUpDirective(line.trim()) ?: return@mapNotNull line

            val item = byKey[parsed.directiveKey]
            if (item == null) {
                changed = true
                return@mapNotNull null // deleted — drop the directive line entirely
            }

            val indent = line.takeWhile { it.isWhitespace() }
            val updated = indent + directiveLineFor(item)
            if (updated != line) changed = true
            updated
        }

        return if (changed) rewritten.joinToString("\n") else markdown
    }

    /**
     * Single directive comment for [item], matching the form [extractFollowUpQuestions] reads.
     *
     * The `id:` field is always written. Markdown is the only storage this app has, so an
     * identity that lives only in memory cannot be referred to after a reload — persisting it
     * is what lets a question be edited, answered, or deleted and still be the same question.
     */
    fun directiveLineFor(item: FollowUpQuestion): String {
        val check = if (item.isAnswered) "[x]" else "[ ]"
        val answerPart = if (item.answerText.isNotBlank()) " | ${item.answerText.replace("\n", " ")}" else ""
        val slidePart = if (item.slideIndex != null) " | slide:${item.slideIndex + 1}" else ""
        return "<!-- parking-lot: $check ${item.question}$answerPart$slidePart | id:${item.id} -->"
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
