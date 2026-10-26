package com.skaldoria.markdown.parser

/**
 * A matched emphasis span: `*x*` / `_x_` ([strong] false) or `**x**` / `__x__` ([strong] true).
 *
 * [contentStart] until [contentEnd] is the text between the delimiters; [end] is the index just past
 * the closing delimiter.
 */
data class EmphasisSpan(
    val strong: Boolean,
    val contentStart: Int,
    val contentEnd: Int
) {
    private val delimiterLength: Int get() = if (strong) 2 else 1
    val end: Int get() = contentEnd + delimiterLength
}

/**
 * **Category: shared grammar.** The single authority on whether `*`, `**`, `_` or `__` delimit
 * emphasis. The deck renderer, the CV renderers ([InlineRuns]) and the writer's parser all call it.
 *
 * Each of them used to pair a marker with the *next identical character*, whatever surrounded
 * either one. So `snake_case_name` rendered with "case" in italics, a handle like
 * `jane_doe_dev` in a CV came out partly italic in the exported PDF, and `2 * 3 * 4` lost its
 * operators. These are the CommonMark flanking rules (spec §6.2), minus its punctuation cases:
 *
 *  - An opener must be followed by a non-whitespace character; a closer preceded by one.
 *  - `_` additionally may not open or close *inside* a word: an opener must not follow a letter or
 *    digit, and a closer must not be followed by one. `*` may, so `a*b*c` still emphasises `b`.
 *  - A single delimiter never closes on one half of a doubled delimiter, so the italic in
 *    `*see **this** too*` spans the whole phrase.
 *
 * An unmatched delimiter is ordinary text, never an error.
 */
object EmphasisRules {

    /** The emphasis span opening at [index] in [text], or null when no valid span opens there. */
    fun match(text: String, index: Int): EmphasisSpan? {
        val marker = text.getOrNull(index) ?: return null
        if (marker != '*' && marker != '_') return null

        val length = if (text.getOrNull(index + 1) == marker) 2 else 1
        if (!canOpen(text, index, length)) return null

        val delimiter = marker.toString().repeat(length)
        var from = index + length + 1 // content is never empty
        while (true) {
            val close = text.indexOf(delimiter, from)
            if (close < 0) return null
            if (canClose(text, close, length) && (length == 2 || !isDoubled(text, close))) {
                return EmphasisSpan(strong = length == 2, contentStart = index + length, contentEnd = close)
            }
            from = close + 1
        }
    }

    private fun canOpen(text: String, index: Int, length: Int): Boolean {
        val next = text.getOrNull(index + length) ?: return false
        if (next.isWhitespace()) return false
        return text[index] != '_' || text.getOrNull(index - 1)?.isLetterOrDigit() != true
    }

    private fun canClose(text: String, index: Int, length: Int): Boolean {
        val previous = text.getOrNull(index - 1) ?: return false
        if (previous.isWhitespace()) return false
        return text[index] != '_' || text.getOrNull(index + length)?.isLetterOrDigit() != true
    }

    private fun isDoubled(text: String, index: Int): Boolean =
        text.getOrNull(index - 1) == text[index] || text.getOrNull(index + 1) == text[index]
}
