package com.skaldoria.markdown.parser

import com.skaldoria.markdown.layout.SmartLayoutClassifier
import com.skaldoria.markdown.models.FollowUpQuestion
import com.skaldoria.markdown.models.Slide
import com.skaldoria.markdown.models.SlideElement
import com.skaldoria.markdown.models.SlideLayoutType
import com.skaldoria.markdown.models.SlideTransition
import java.util.UUID

/**
 * Pure Standard Markdown slide parser with support for directives and smart layout auto-classification.
 * Converts CommonMark / GFM markdown into structured, smart-layout slides.
 */
object MarkdownSlideParser {

    // ---- Slide-structure policy ----
    //
    // These two answer "where does a slide begin and end", which is *this parser's* question and
    // nobody else's. The editor's highlighter asks a different one — "what colour is this line" —
    // and is expected to answer differently. It colours `### Sub` and `#hashtag`; neither starts a
    // slide. That is two correct answers, not a divergence, and these names now say so.
    //
    // Contrast with `FenceRules`, which is shared grammar: every consumer must agree with it, and
    // `FenceLexerAgreementTest` fails if they stop. See docs/MARKDOWN_UNIFICATION_PLAN.md, Phase E.

    /** The deepest heading level that begins a new slide. Deeper ones are content. */
    internal const val SLIDE_HEADING_MAX_LEVEL = 2

    /**
     * Policy: does this line begin a new slide?
     *
     * The *syntax* — is this an ATX heading, at what level — belongs to [HeadingRules] and is
     * shared with the editor's highlighter. Only the `<= 2` is Skaldoria's own decision, which is
     * why the highlighter is free to colour `### Sub` without this returning true.
     */
    internal fun startsSlide(line: String): Boolean {
        val level = HeadingRules.heading(line)?.level ?: return false
        return level <= SLIDE_HEADING_MAX_LEVEL
    }
    // CODE_FENCE_START was removed in Phase B. It recognised backtick fences only, and only
    // with an info string of the form `lang [1,3-5]`, which is what silently dropped the
    // language on ordinary markdown like ```js {highlight=2}. FenceRules replaced it.
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

        // COR-1: fence state decides whether a `---` splits the deck or is part of a code
        // sample. Tracked through FenceRules so this scan, the block rules and the editor's
        // highlighter cannot drift apart — previously this toggled on any "```" prefix and
        // therefore disagreed with all of them.
        var openFence: FenceInfo? = null

        for ((lineIndex, line) in lines.withIndex()) {
            val trimmed = line.trim()

            val currentFence = openFence
            if (currentFence != null) {
                if (FenceRules.closes(trimmed, currentFence)) openFence = null
                appendLine(lineIndex, line)
                continue
            }

            val opening = FenceRules.openingFence(trimmed)
            if (opening != null) {
                openFence = opening
                appendLine(lineIndex, line)
                continue
            }

            // Slide split conditions: a thematic break, or a heading at slide level.
            val isHr = ThematicBreakRules.isThematicBreak(trimmed)
            val isHeading1or2 = startsSlide(trimmed)

            if (isHr) {
                // The rule itself belongs to no slide; it is regenerated on reassembly.
                flushSection(lineIndex)
                continue
            }

            if (isHeading1or2 && currentLines.any { it.isNotBlank() }) {
                // If the section already has content or a heading, split it
                val hasExistingHeading = currentLines.any { startsSlide(it.trim()) }
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

    /**
     * Whether [markdown] can possibly yield a follow-up item, decided by character scan.
     *
     * PRF-6: this runs on **every deck change** via `ParkingLotStore.reconcile`, and it was 24%
     * of the per-keystroke budget on documents that contain no follow-up items at all — which is
     * most of them, including every deck that has never used the feature.
     *
     * **The guard has to cover both extraction paths, and that is the trap.** A
     * `parking-lot:`-only check looks obvious and silently drops every checkbox-derived item:
     *
     *  1. directive comments — `<!-- parking-lot: … -->` and its aliases, all requiring `<!--`
     *  2. task lists — `CHECKBOX_LINE_REGEX`, `^-\s*\[`, on the *trimmed* line
     *
     * Deliberately a superset of what those two can match: no allocation, no regex, and it
     * cannot exclude anything the real scan would have found.
     */
    private fun mayContainFollowUps(markdown: String): Boolean {
        if (markdown.contains("<!--")) return true

        for (i in markdown.indices) {
            if (markdown[i] != '-') continue
            var j = i + 1
            while (j < markdown.length && (markdown[j] == ' ' || markdown[j] == '\t')) j++
            if (j < markdown.length && markdown[j] == '[') return true
        }
        return false
    }

    fun extractFollowUpQuestions(markdown: String): List<FollowUpQuestion> {
        if (!mayContainFollowUps(markdown)) return emptyList()

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
