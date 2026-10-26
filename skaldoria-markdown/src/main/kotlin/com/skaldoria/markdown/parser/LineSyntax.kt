package com.skaldoria.markdown.parser

/**
 * **Category: shared grammar.** Everything in this file answers *"what is this line?"* — a
 * question CommonMark decides, not Skaldoria. Every consumer must agree with it, and
 * `LineRuleAgreementTest` fails when one stops.
 *
 * What each consumer *does* with the answer is policy, and stays with the consumer:
 * `MarkdownSlideParser.startsSlide` decides that levels 1–2 begin a slide, while the editor's
 * highlighter colours all six. Those are different questions with different right answers, so they
 * are deliberately not unified — see `docs/MARKDOWN_UNIFICATION_PLAN.md`, Phase F.
 *
 * Plain objects and plain functions on purpose. A shared `LineRule<T>` interface was considered
 * and rejected: callers need different subsets at different points and the results genuinely
 * differ, so the interface would buy polymorphism nobody uses.
 */

/**
 * An ATX heading: `#` through `######`, its level, its text, and where that text begins.
 *
 * [textStart] is an offset into the line exactly as handed to [HeadingRules.heading], leading
 * whitespace included. It exists so that a caller needing to *locate* the heading in its source
 * does not have to search the line for [text] and risk matching an earlier coincidence — the rule
 * that recognised the heading is the thing that knows where its text starts.
 */
data class HeadingInfo(val level: Int, val text: String, val textStart: Int)

/** A CommonMark list item and the text following its marker. */
data class ListItemInfo(val text: String, val isOrdered: Boolean)

object HeadingRules {

    /** CommonMark requires whitespace after the hashes — `#hashtag` is prose, not a heading. */
    private val ATX = Regex("""^(#{1,6})\s+(.+)$""")

    fun heading(line: String): HeadingInfo? {
        val text = line.trimStart()
        // Cheap reject before the regex. Ordinary prose is the overwhelmingly common line, this
        // runs on every one of them on every keystroke, and `Regex.find` on a non-heading costs
        // far more than a single char comparison. Measured at ~40% of the highlighter when the
        // regex was reached unconditionally.
        if (text.firstOrNull() != '#') return null

        val match = ATX.find(text) ?: return null
        val body = match.groups[2] ?: return null
        // `text` is `line` with its indent removed, so the regex offsets are shifted by exactly
        // that much. `\s+` is greedy, so `body` already begins at the first non-space character.
        val indent = line.length - text.length
        return HeadingInfo(
            level = match.groupValues[1].length,
            text = body.value.trim(),
            textStart = indent + body.range.first
        )
    }
}

/** Shared authority for the list markers understood by Skaldoria Markdown consumers. */
object ListRules {
    private val BULLET = Regex("""^[-*+]\s+(.+)$""")
    private val NUMBERED = Regex("""^\d+\.\s+(.+)$""")

    fun listItem(line: String): ListItemInfo? {
        val text = line.trimStart()
        val first = text.firstOrNull() ?: return null
        if (first != '-' && first != '*' && first != '+' && !first.isDigit()) return null

        BULLET.matchEntire(text)?.let {
            return ListItemInfo(text = it.groupValues[1].trim(), isOrdered = false)
        }
        NUMBERED.matchEntire(text)?.let {
            return ListItemInfo(text = it.groupValues[1].trim(), isOrdered = true)
        }
        return null
    }
}

object ThematicBreakRules {

    /**
     * `***`, `---` or `___`, three or more, nothing else on the line.
     *
     * The editor previously recognised only an exact `---`, so `***` split a deck with no visual
     * indication that anything had happened. That is the divergence this object removes; whether a
     * break *ends a slide* remains the parser's policy.
     */
    private val BREAK = Regex("""^(\*{3,}|-{3,}|_{3,})\s*$""")

    fun isThematicBreak(line: String): Boolean {
        val text = line.trim()
        // Same cheap reject as HeadingRules: only three characters can begin a break.
        val first = text.firstOrNull() ?: return false
        if (first != '*' && first != '-' && first != '_') return false

        return BREAK.matches(text)
    }
}

object FrontMatterRules {

    private const val DELIMITER = "---"

    /**
     * Index of the line closing the document's YAML front matter, or null when the document has
     * none.
     *
     * Only the CV dialect uses front matter, but the rule still belongs here: `CvMarkdownAdapter`
     * and the CV editor's highlighter are two consumers asking the same question, and they used to
     * answer differently. The adapter trims each delimiter line; the highlighter matched
     * `^---\n.*?\n---` against the raw text, so a delimiter with a trailing space — or a file with
     * CRLF endings — parsed as metadata while showing as an unstyled thematic break.
     *
     * A `---` opening the document is front matter, never a thematic break. That precedence is the
     * caller's to apply; this only reports where the block ends.
     */
    fun closingLineIndex(lines: List<String>): Int? {
        if (lines.firstOrNull()?.trim() != DELIMITER) return null
        return (1 until lines.size).firstOrNull { lines[it].trim() == DELIMITER }
    }
}

object MathRules {

    private const val DELIMITER = "$$"

    /** A complete formula on one line: `$$ x = 1 $$`. */
    fun isSingleLine(line: String): Boolean {
        val text = line.trim()
        return text.startsWith(DELIMITER) && text.endsWith(DELIMITER) && text.length > DELIMITER.length
    }

    /** Opens a multi-line block — a `$$` that does not also close on the same line. */
    fun opensBlock(line: String): Boolean {
        val text = line.trim()
        return text.startsWith(DELIMITER) && !isSingleLine(text)
    }

    /**
     * Closes an open multi-line block.
     *
     * Callers must already know a block is open: a bare `$$` both opens and closes, so state
     * decides which it is. This mirrors [FenceRules.closes], which has the same requirement.
     */
    fun closesBlock(line: String): Boolean {
        val text = line.trim()
        return text == DELIMITER || (text.endsWith(DELIMITER) && !text.startsWith(DELIMITER))
    }
}
