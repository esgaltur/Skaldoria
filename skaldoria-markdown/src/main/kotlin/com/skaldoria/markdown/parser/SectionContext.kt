package com.skaldoria.markdown.parser

import com.skaldoria.markdown.models.SlideElement
import com.skaldoria.markdown.models.SlideLayoutType
import com.skaldoria.markdown.models.SlideTransition

/** The directives one slide section declares. */
internal class SectionDirectives {
    var layout: SlideLayoutType? = null
    var background: String? = null
    var transition: SlideTransition? = null
}

/**
 * The state of parsing one slide section, and the accumulators that span several lines.
 *
 * F-17: this was twelve mutable locals and five nested closures inside a 364-line function.
 * As an object the accumulators can be *owned* — which is what lets [flushPending] exist, so
 * a rule no longer has to remember which two, three or four `flushX()` calls to make before
 * doing its own work. Omitting one silently dropped an element, and the ritual was repeated
 * about twenty times.
 *
 * Block elements are line-spanning: a bullet list continues until something that is not a
 * bullet arrives. So each accumulator collects until the *next* thing closes it, which is why
 * flushing is a separate step from consuming.
 */
internal class SectionContext {

    var title: String = ""
    var subtitle: String? = null

    val elements = mutableListOf<SlideElement>()
    val notes = mutableListOf<String>()
    val directives = SectionDirectives()

    // ---- accumulators for line-spanning blocks ----

    private var listItems = mutableListOf<String>()
    private var quoteLines = mutableListOf<String>()
    private var tableLines = mutableListOf<String>()
    private var mathLines = mutableListOf<String>()
    private var codeLines = mutableListOf<String>()

    var inCodeBlock: Boolean = false
        private set

    /**
     * The fence currently held open, so [CodeFenceRule] can require a *matching* terminator
     * rather than accepting any line that starts with a marker. Null whenever [inCodeBlock] is
     * false.
     */
    var openFence: FenceInfo? = null
        private set
    var inMathBlock: Boolean = false
        private set

    private var codeLanguage: String = "kotlin"
    private var highlightedLines: Set<Int> = emptySet()

    /** True when the section has content, used by the "title or paragraph?" decision. */
    val hasTitle: Boolean get() = title.isNotEmpty()

    /**
     * True while a list, quote or table is still accumulating and has not yet become an
     * element.
     *
     * [MetricRule] needs this. In the original `if`-chain every earlier branch flushed as a
     * side effect, so by the time the metric check ran its `elements.isEmpty()` test already
     * accounted for a list that had just ended. Here flushing happens *after* a rule is
     * chosen, so "is this the first element?" has to ask about pending blocks too — without
     * it, a KPI-shaped line following a bullet list hijacks the slide layout.
     */
    val hasPendingBlocks: Boolean
        get() = listItems.isNotEmpty() || quoteLines.isNotEmpty() || tableLines.isNotEmpty()

    // ---- adding to a block ----

    fun addListItem(item: String) = listItems.add(item)

    fun addQuoteLine(line: String) = quoteLines.add(line)

    fun addTableLine(line: String) = tableLines.add(line)

    /**
     * AUT-17: the line after the one being dispatched, or null at the end of the section.
     *
     * A table header is only a header because a delimiter row follows it, and nothing else in
     * the grammar needs to see forward. Rather than give every rule a lookahead parameter, the
     * parse loop parks it here for the one rule that asks.
     */
    var nextLine: String? = null

    /** AUT-17: whether a table is currently accumulating, so continuation rows can be claimed. */
    val hasOpenTable: Boolean get() = tableLines.isNotEmpty()

    fun addMathLine(line: String) = mathLines.add(line)

    fun addCodeLine(line: String) = codeLines.add(line)

    fun openCodeBlock(language: String, highlights: Set<Int>, fence: FenceInfo? = null) {
        inCodeBlock = true
        openFence = fence
        codeLanguage = language
        highlightedLines = highlights
        codeLines = mutableListOf()
    }

    fun openMathBlock() {
        inMathBlock = true
        mathLines = mutableListOf()
    }

    /**
     * Records a heading or lead line. The first one becomes the slide title; later ones
     * become lead text.
     *
     * COR-5: a second `#`/`##` in one section — possible when the split heuristic declines to
     * split — used to fall through to the paragraph branch and render with its literal `##`
     * markers still attached.
     */
    fun addHeading(text: String) {
        if (title.isEmpty()) title = text else elements.add(SlideElement.Text(text, isLead = true))
    }

    /** A `###` line: title if there is none, else subtitle, else lead text. */
    fun addSubheading(text: String) {
        when {
            title.isEmpty() -> title = text
            subtitle == null -> subtitle = text
            else -> elements.add(SlideElement.Text(text, isLead = true))
        }
    }

    fun addParagraph(text: String) {
        if (title.isEmpty()) title = text else elements.add(SlideElement.Text(text))
    }

    // ---- closing blocks ----

    /**
     * Closes every open block except code and math, which are explicitly terminated by their
     * own fence and are therefore never closed by the arrival of another line.
     *
     * Called once by the dispatcher before a rule consumes, rather than by each rule.
     */
    fun flushPending() {
        flushList()
        flushQuote()
        flushTable()
    }

    /** Closes everything, including the fenced blocks. Called at the end of the section. */
    fun flushAll() {
        flushList()
        flushQuote()
        flushTable()
        flushCode()
        flushMath()
    }

    fun flushList() {
        if (listItems.isEmpty()) return
        elements.add(SlideElement.BulletList(listItems.toList()))
        listItems = mutableListOf()
    }

    fun flushQuote() {
        if (quoteLines.isEmpty()) return
        val full = quoteLines.joinToString("\n")
        // An attribution line (`-- Name` / `- Name`) splits the quote from its author.
        val parts = full.split(Regex("""\n\s*--?\s*"""))
        elements.add(
            if (parts.size >= 2) SlideElement.Quote(parts[0].trim(), parts[1].trim())
            else SlideElement.Quote(full.trim())
        )
        quoteLines = mutableListOf()
    }

    fun flushTable() {
        if (tableLines.isEmpty()) return

        // Fewer than two lines is not a table — it is prose that happened to contain a pipe.
        if (tableLines.size < 2) {
            tableLines.forEach { elements.add(SlideElement.Text(it)) }
            tableLines = mutableListOf()
            return
        }

        val headers = splitCells(tableLines[0])
        val rows = tableLines.drop(1)
            // The `|---|---|` separator carries no data.
            // AUT-17: one authority for what a delimiter row is; see TableRules.
            .filterNot { TableRules.isSeparatorRow(it) }
            .map { splitCells(it) }

        if (headers.isNotEmpty()) elements.add(SlideElement.Table(headers = headers, rows = rows))
        tableLines = mutableListOf()
    }

    fun flushMath() {
        if (mathLines.isEmpty()) {
            inMathBlock = false
            return
        }
        val formula = mathLines.joinToString("\n").trim()
        if (formula.isNotEmpty()) elements.add(SlideElement.MathFormula(formula = formula, isBlock = true))
        mathLines = mutableListOf()
        inMathBlock = false
    }

    fun flushCode() {
        if (codeLines.isEmpty() && !inCodeBlock) return

        val code = codeLines.joinToString("\n")
        val language = codeLanguage.lowercase().trim()

        elements.add(
            when (language) {
                // A fenced diagram or formula is that element, not a code listing.
                in DIAGRAM_LANGUAGES -> SlideElement.MermaidDiagram(
                    code = code,
                    diagramType = language.ifBlank { "flowchart" }
                )
                in MATH_LANGUAGES -> SlideElement.MathFormula(formula = code, isBlock = true)
                else -> SlideElement.CodeBlock(
                    code = code,
                    language = codeLanguage.ifBlank { "kotlin" },
                    highlightedLines = highlightedLines
                )
            }
        )
        codeLines = mutableListOf()
        inCodeBlock = false
        openFence = null
    }

    private fun splitCells(raw: String): List<String> =
        raw.trim().removeSurrounding("|", "|").split("|").map { it.trim() }

    private companion object {
        val DIAGRAM_LANGUAGES = setOf("mermaid", "diagram", "flowchart", "sequence", "graph")
        val MATH_LANGUAGES = setOf("math", "latex", "katex", "tex", "equation")
    }
}
