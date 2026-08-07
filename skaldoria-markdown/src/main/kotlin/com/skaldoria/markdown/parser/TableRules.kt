package com.skaldoria.markdown.parser

/**
 * AUT-17: the one authority on what a GFM table row looks like.
 *
 * **Why this exists as shared grammar rather than a private check.** Table recognition was
 * written twice and differently: `TableRule.matches` asked for outer pipes or a literal
 * `"-|-"` substring, while `SectionContext.flushTable` decided which row was the separator with
 * `replace(Regex("[|:\\-\\s]"), "").isEmpty()`. The editor's highlighter held a third opinion
 * (`startsWith("|") && endsWith("|")`). Three definitions of one construct is the shape that
 * produced the fence defects Phase B of `MARKDOWN_UNIFICATION_PLAN.md` fixed with `FenceRules`;
 * this is the same remedy applied to tables before it costs anything.
 *
 * **The feature this unlocks.** Outer pipes are optional in GFM — every other tool accepts
 *
 * ```
 * Deck | Purpose
 * ---|---
 * intro | the opening
 * ```
 *
 * and Skaldoria rendered all three lines as prose, because only the separator satisfied the old
 * `matches` and a lone table line is flushed back out as text.
 */
// Public for the same reason as FenceRules and ThematicBreakRules: the editor's
// highlighter lives in the app module and must share this grammar rather than
// reimplement it.
object TableRules {

    /** Characters a separator row is allowed to contain, besides whitespace. */
    private const val SEPARATOR_CHARS = "|:-"

    /**
     * A delimiter row: `---|---`, `| :--- | ---: |`, and so on.
     *
     * **The pipe is required**, which is what keeps a bare `---` a thematic break — i.e. a slide
     * boundary — rather than the separator of a table with no columns. A dash is required too,
     * so a row of empty cells (`| |`) is data, not a delimiter.
     */
    fun isSeparatorRow(line: String): Boolean {
        val trimmed = line.trim()
        if (!trimmed.contains('|') || !trimmed.contains('-')) return false
        return trimmed.all { it in SEPARATOR_CHARS || it.isWhitespace() }
    }

    /**
     * A row carrying cells: pipe-delimited, with or without the outer pipes.
     *
     * Deliberately permissive on its own — prose containing a pipe satisfies it. Being a table
     * is decided by [isSeparatorRow] appearing next to such a row, never by this alone.
     */
    fun isRow(line: String): Boolean = line.contains('|')

    /** A row with the conventional outer pipes, which is a table row with no further context. */
    fun isFencedRow(line: String): Boolean {
        val trimmed = line.trim()
        return trimmed.length >= 2 && trimmed.startsWith("|") && trimmed.endsWith("|")
    }
}
