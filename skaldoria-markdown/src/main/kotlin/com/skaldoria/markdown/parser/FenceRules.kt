package com.skaldoria.markdown.parser

/**
 * What an opening fence line declares.
 *
 * [marker] and [length] are carried because a fence may only be closed by **at least as many**
 * of the **same** character — a rule none of the previous three implementations had, which is
 * why a nested ` ``` ` inside a `~~~` block used to terminate the wrong thing.
 */
data class FenceInfo(
    val marker: Char,
    val length: Int,
    val language: String,
    val highlights: Set<Int>
)

/**
 * **Category: shared grammar.** Every consumer must agree with this — the parser, the block
 * rules and the editor's highlighter all call it, and `FenceLexerAgreementTest` fails if any of
 * them starts answering differently. Contrast `MarkdownSlideParser.SLIDE_HEADING`, which is
 * *policy*: the highlighter is expected to disagree with that one.
 *
 * The single authority on what opens and closes a fenced block.
 *
 * Before this existed, four places each decided independently:
 *
 * | Site | Rule it used |
 * | :--- | :--- |
 * | `MarkdownSlideParser` slide-split scan | `trimmed.startsWith("```")`, toggled |
 * | `MarkdownSlideParser.CODE_FENCE_START` | anchored regex, backticks only |
 * | `BlockRules.CodeFenceRule` | `line.startsWith("```")` |
 * | `MarkdownVisualTransformation` | `trimmed.startsWith("```")`, toggled |
 *
 * They disagreed, and `FenceLexerDivergenceTest` was written to prove it. Two defects came
 * directly out of that disagreement: tilde fences were invisible everywhere, and a fence whose
 * info string the anchored regex rejected (` ```js {highlight=2} `) lost its language, silently
 * falling back to `kotlin` in `SectionContext.flushCode`.
 *
 * See `docs/MARKDOWN_UNIFICATION_PLAN.md`, Phase B.
 */
object FenceRules {

    /** CommonMark's minimum run length for a fence. */
    private const val MIN_FENCE_LENGTH = 3

    /** Skaldoria's extension: `[1,3-5]` after the language selects lines to highlight. */
    private val HIGHLIGHT_RANGE = Regex("""\[([0-9,\-|]+)]""")

    /**
     * The fence this line opens, or null if it opens none.
     *
     * Accepts backtick and tilde fences of any length ≥ 3 and **any** info string, which is what
     * CommonMark specifies and what the previous anchored regex did not do.
     */
    fun openingFence(line: String): FenceInfo? {
        val text = line.trimStart()
        val marker = text.firstOrNull() ?: return null
        if (marker != '`' && marker != '~') return null

        val length = text.countLeading(marker)
        if (length < MIN_FENCE_LENGTH) return null

        val info = text.substring(length).trim()

        // CommonMark: a backtick fence's info string may not itself contain a backtick, so
        // `` `a` `` on its own line stays inline code rather than opening a block.
        if (marker == '`' && info.contains('`')) return null

        // A bare `[1,3-5]` is a highlight spec, not a language name.
        val firstToken = info.takeWhile { !it.isWhitespace() }
        val language = if (firstToken.startsWith("[")) "" else firstToken

        return FenceInfo(
            marker = marker,
            length = length,
            language = language,
            highlights = MarkdownSlideParser.parseLineHighlights(
                HIGHLIGHT_RANGE.find(info)?.groupValues?.getOrNull(1)
            )
        )
    }

    /**
     * Whether [line] closes [open].
     *
     * A closing fence is the same marker, at least as long, and carries **no** info string —
     * so ` ```js ` appearing inside an open block is code, not a terminator.
     */
    fun closes(line: String, open: FenceInfo): Boolean {
        val text = line.trim()
        val length = text.countLeading(open.marker)
        if (length < open.length) return false
        return text.substring(length).isBlank()
    }

    private fun String.countLeading(char: Char): Int {
        var count = 0
        while (count < length && this[count] == char) count++
        return count
    }
}
