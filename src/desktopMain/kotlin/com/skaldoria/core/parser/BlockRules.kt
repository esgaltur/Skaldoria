package com.skaldoria.core.parser

import com.skaldoria.core.models.SlideElement

/**
 * One kind of markdown block, and how to consume it.
 *
 * F-17: the parse loop was a nine-branch `if (…) { … continue }` chain inside a 364-line
 * function, where correctness depended on **implicit** ordering — tables before headings,
 * the metric rule before the paragraph fallback, the comment skip before paragraphs — that
 * was nowhere named or asserted. It was simply the order the `if`s happened to appear in,
 * and several recorded defects came from exactly that.
 *
 * As a list, the ordering *is* the specification: readable in one place, and asserted by
 * `BlockRuleOrderTest`.
 */
internal interface BlockRule {

    /** Whether this rule handles [line] in the current [context]. */
    fun matches(line: String, context: SectionContext): Boolean

    /**
     * Consumes [line].
     *
     * @param raw the original line, indentation intact — code blocks need it.
     */
    fun consume(line: String, raw: String, context: SectionContext)

    /**
     * Whether the dispatcher closes open list/quote/table blocks before consuming.
     *
     * Almost every rule wants this. The exceptions are the rules that *are* the accumulating
     * block (a bullet line must not close the list it belongs to) and the ones that consume
     * lines inside a fence.
     */
    val flushesPendingBlocks: Boolean get() = true
}

/**
 * The rule chain, in priority order. **The order is load-bearing.**
 *
 * It reproduces the original `if`-chain exactly. Two orderings are worth calling out because
 * they look arbitrary:
 *
 *  - [MetricRule] must precede [ParagraphRule], or every KPI line renders as prose.
 *  - [HtmlCommentRule] must precede [ParagraphRule], or unrecognised comments render as
 *    literal `<!-- … -->` text on the slide — acute now that captured parking-lot questions
 *    are written back into the deck.
 *
 * Known wart, preserved deliberately rather than fixed inside a refactor: the directive and
 * note rules sit *above* [InCodeBlockRule], so a `<!-- note: … -->` line inside a fenced
 * block is still consumed as a directive instead of as code. Changing that is a behaviour
 * change and belongs in its own commit.
 */
internal val BLOCK_RULES: List<BlockRule> = listOf(
    DirectiveRule,
    NoteCommentRule,
    NoteQuoteRule,
    CodeFenceRule,
    InCodeBlockRule,
    InMathBlockRule,
    MathFenceRule,
    TableRule,
    HeadingRule,
    SubheadingRule,
    ListRule,
    QuoteRule,
    ImageRule,
    MetricRule,
    HtmlCommentRule,
    ParagraphRule
)

// ---------------------------------------------------------------------------
// Directives and notes
// ---------------------------------------------------------------------------

/**
 * `<!-- layout: hero -->` anywhere, or a bare `layout: hero` line before the title.
 *
 * The line form is deliberately restricted to the pre-title region, so ordinary prose such as
 * "background: dark blue" cannot be swallowed as configuration.
 */
internal object DirectiveRule : BlockRule {
    private fun find(line: String, context: SectionContext) =
        MarkdownSlideParser.DIRECTIVE_COMMENT_REGEX.find(line)
            ?: if (!context.hasTitle) MarkdownSlideParser.DIRECTIVE_LINE_REGEX.find(line) else null

    override fun matches(line: String, context: SectionContext) = find(line, context) != null

    override fun consume(line: String, raw: String, context: SectionContext) {
        val match = find(line, context) ?: return
        val poll = MarkdownSlideParser.applyDirective(
            key = match.groupValues[1].lowercase(),
            value = match.groupValues[2].trim(),
            directives = context.directives,
            // A poll adopts the slide title once there is one; the line form is only
            // reachable before the title, so it always takes the generic label.
            pollQuestion = context.title.ifEmpty { "Audience Poll" }
        )
        if (poll != null) context.elements.add(poll)
    }

    override val flushesPendingBlocks get() = false
}

internal object NoteCommentRule : BlockRule {
    override fun matches(line: String, context: SectionContext) =
        MarkdownSlideParser.NOTE_COMMENT_REGEX.containsMatchIn(line)

    override fun consume(line: String, raw: String, context: SectionContext) {
        MarkdownSlideParser.NOTE_COMMENT_REGEX.find(line)?.let { context.notes.add(it.groupValues[1]) }
    }

    override val flushesPendingBlocks get() = false
}

internal object NoteQuoteRule : BlockRule {
    override fun matches(line: String, context: SectionContext) =
        MarkdownSlideParser.NOTE_QUOTE_REGEX.containsMatchIn(line)

    override fun consume(line: String, raw: String, context: SectionContext) {
        MarkdownSlideParser.NOTE_QUOTE_REGEX.find(line)?.let { context.notes.add(it.groupValues[1]) }
    }

    override val flushesPendingBlocks get() = false
}

// ---------------------------------------------------------------------------
// Fenced blocks
// ---------------------------------------------------------------------------

/** A ``` line: closes an open fence, or opens a new one. */
internal object CodeFenceRule : BlockRule {
    override fun matches(line: String, context: SectionContext) = line.startsWith("```")

    override fun consume(line: String, raw: String, context: SectionContext) {
        if (context.inCodeBlock) {
            context.flushCode()
            return
        }
        // Opening a fence closes everything still accumulating, math included.
        context.flushPending()
        context.flushMath()

        val fence = MarkdownSlideParser.CODE_FENCE_START.find(line)
        context.openCodeBlock(
            language = fence?.groupValues?.getOrNull(1) ?: "",
            highlights = MarkdownSlideParser.parseLineHighlights(fence?.groupValues?.getOrNull(2))
        )
    }

    override val flushesPendingBlocks get() = false
}

/** Any line inside a fence is code, verbatim, indentation intact. */
internal object InCodeBlockRule : BlockRule {
    override fun matches(line: String, context: SectionContext) = context.inCodeBlock

    override fun consume(line: String, raw: String, context: SectionContext) {
        context.addCodeLine(raw)
    }

    override val flushesPendingBlocks get() = false
}

/** Inside a `$$` block: accumulate until the closing delimiter. */
internal object InMathBlockRule : BlockRule {
    override fun matches(line: String, context: SectionContext) = context.inMathBlock

    override fun consume(line: String, raw: String, context: SectionContext) {
        val closes = line == "$$" || (line.endsWith("$$") && !line.startsWith("$$"))
        if (!closes) {
            context.addMathLine(raw)
            return
        }
        line.removeSuffix("$$").trim().takeIf { it.isNotEmpty() }?.let { context.addMathLine(it) }
        context.flushMath()
    }

    override val flushesPendingBlocks get() = false
}

/** A `$$` opener, either inline (`$$ x $$`) or the start of a block. */
internal object MathFenceRule : BlockRule {
    override fun matches(line: String, context: SectionContext) = line.startsWith("$$")

    override fun consume(line: String, raw: String, context: SectionContext) {
        if (line.endsWith("$$") && line.length > 2) {
            line.removeSurrounding("$$").trim().takeIf { it.isNotEmpty() }?.let {
                context.elements.add(SlideElement.MathFormula(formula = it, isBlock = true))
            }
            return
        }
        context.openMathBlock()
        line.removePrefix("$$").trim().takeIf { it.isNotEmpty() }?.let { context.addMathLine(it) }
    }
}

// ---------------------------------------------------------------------------
// Body blocks
// ---------------------------------------------------------------------------

/**
 * A table row. Must be tried **before** [HeadingRule]: a header row is not a heading, and a
 * separator row would otherwise be mistaken for one.
 */
internal object TableRule : BlockRule {
    override fun matches(line: String, context: SectionContext) =
        (line.startsWith("|") && line.endsWith("|")) || (line.contains("|") && line.contains("-|-"))

    override fun consume(line: String, raw: String, context: SectionContext) {
        context.addTableLine(line)
    }

    /** A table row must not close the table it belongs to. */
    override val flushesPendingBlocks get() = false
}

internal object HeadingRule : BlockRule {
    override fun matches(line: String, context: SectionContext) =
        MarkdownSlideParser.HEADING_1_2_REGEX.containsMatchIn(line)

    override fun consume(line: String, raw: String, context: SectionContext) {
        MarkdownSlideParser.HEADING_1_2_REGEX.find(line)?.let {
            context.addHeading(it.groupValues[2].trim())
        }
    }
}

internal object SubheadingRule : BlockRule {
    private val H3 = Regex("""^###\s+(.+)$""")

    override fun matches(line: String, context: SectionContext) = H3.containsMatchIn(line)

    override fun consume(line: String, raw: String, context: SectionContext) {
        H3.find(line)?.let { context.addSubheading(it.groupValues[1].trim()) }
    }
}

internal object ListRule : BlockRule {
    private val BULLET = Regex("""^[-*+]\s+(.+)$""")
    private val NUMBERED = Regex("""^\d+\.\s+(.+)$""")

    private fun find(line: String) = BULLET.find(line) ?: NUMBERED.find(line)

    override fun matches(line: String, context: SectionContext) = find(line) != null

    override fun consume(line: String, raw: String, context: SectionContext) {
        // A list item closes a quote or table, but never the list it continues.
        context.flushQuote()
        context.flushTable()
        find(line)?.let { context.addListItem(it.groupValues[1].trim()) }
    }

    override val flushesPendingBlocks get() = false
}

internal object QuoteRule : BlockRule {
    override fun matches(line: String, context: SectionContext) = line.startsWith(">")

    override fun consume(line: String, raw: String, context: SectionContext) {
        context.flushTable()
        context.addQuoteLine(line.removePrefix(">").trim())
    }

    /** A quote line must not close the quote it belongs to. */
    override val flushesPendingBlocks get() = false
}

internal object ImageRule : BlockRule {
    override fun matches(line: String, context: SectionContext) =
        MarkdownSlideParser.IMAGE_REGEX.containsMatchIn(line)

    override fun consume(line: String, raw: String, context: SectionContext) {
        MarkdownSlideParser.IMAGE_REGEX.find(line)?.let {
            context.elements.add(SlideElement.Image(url = it.groupValues[2], altText = it.groupValues[1]))
        }
    }
}

/**
 * A standalone KPI line, e.g. `99.99% Uptime` or `+140% Growth`.
 *
 * COR-4: the value must carry a unit — a percent sign, a currency prefix, a magnitude suffix,
 * an explicit sign, or an `x` multiplier. Without that, any paragraph opening with a number
 * was captured, so an ordinary line like "2024 Roadmap Overview" rendered as a full-slide KPI.
 *
 * Only the *first* element may be a metric, which is what keeps a figure mentioned mid-slide
 * from hijacking the layout.
 */
internal object MetricRule : BlockRule {
    override fun matches(line: String, context: SectionContext) =
        context.elements.isEmpty() &&
            // A block that has not been flushed yet is still content; see hasPendingBlocks.
            !context.hasPendingBlocks &&
            MarkdownSlideParser.METRIC_REGEX.containsMatchIn(line)

    override fun consume(line: String, raw: String, context: SectionContext) {
        MarkdownSlideParser.METRIC_REGEX.find(line)?.let {
            context.elements.add(SlideElement.Metric(it.groupValues[1], it.groupValues[2]))
        }
    }
}

/**
 * Any remaining HTML comment is metadata, not content.
 *
 * Unrecognised comments used to fall through to [ParagraphRule] and render as literal
 * `<!-- … -->` text on the slide. That was already wrong for notes and parking-lot
 * directives, and became acute once captured questions were written back into the deck —
 * every one would have shown up on a slide.
 */
internal object HtmlCommentRule : BlockRule {
    override fun matches(line: String, context: SectionContext) =
        line.startsWith("<!--") && line.endsWith("-->")

    override fun consume(line: String, raw: String, context: SectionContext) = Unit

    override val flushesPendingBlocks get() = false
}

/** The fallback. Must be last: it accepts anything non-blank. */
internal object ParagraphRule : BlockRule {
    override fun matches(line: String, context: SectionContext) = line.isNotBlank()

    override fun consume(line: String, raw: String, context: SectionContext) = context.addParagraph(line)
}
