package com.skaldoria.markdown.parser

/**
 * A stretch of text carrying resolved inline formatting, with the markdown delimiters removed.
 *
 * Contrast [MarkdownToken], which keeps the delimiters and reports *where* they are — that is what
 * a source highlighter needs. A renderer needs the opposite: the text as it should appear, plus
 * what it should look like.
 */
data class InlineRun(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val strikethrough: Boolean = false,
    /** Link target for `[text](target)`, or null. */
    val link: String? = null
)

/**
 * **Category: shared grammar.** Splits one line of inline markdown into renderable runs.
 *
 * Written for the CV renderers, which need the *same* runs in two places — the on-screen preview
 * and the PDF export — because pagination is only deterministic if both lay out identical text.
 * `CvPdfRenderer` and the preview both call this; neither parses emphasis itself.
 *
 * Emphasis nests (`**bold with *italic* inside**`). Inline code is literal, so markers inside
 * backticks stay as written. An unterminated marker is ordinary text rather than an error, which
 * is what keeps a half-typed `**` from restyling the remainder of a CV while it is being edited.
 *
 * Deliberately *not* handling `$…$` math: `inlineMarkdown` in `:skaldoria-shared-ui` does, because
 * decks need it and a CV does not. That is a dialect difference, not duplication to remove —
 * though the two should eventually meet here. See `MARKDOWN_UNIFICATION_PLAN.md`, Phase H.
 */
object InlineRuns {

    fun parse(markdown: String): List<InlineRun> {
        val runs = ArrayList<InlineRun>()
        append(markdown, InlineRun(text = "", bold = false), runs)
        return merge(runs)
    }

    /** [inherited] carries the styles of the enclosing markers; only its [InlineRun.text] is unused. */
    private fun append(text: String, inherited: InlineRun, out: MutableList<InlineRun>) {
        val literal = StringBuilder()

        fun flush() {
            if (literal.isNotEmpty()) {
                out += inherited.copy(text = literal.toString())
                literal.setLength(0)
            }
        }

        var i = 0
        while (i < text.length) {
            val c = text[i]

            // Inline code: literal content, no nested markers.
            if (c == '`') {
                val end = text.indexOf('`', i + 1)
                if (end > i) {
                    flush()
                    out += inherited.copy(text = text.substring(i + 1, end), code = true)
                    i = end + 1
                    continue
                }
            }

            // Link: [label](target). The label may itself carry emphasis.
            if (c == '[') {
                val labelEnd = text.indexOf(']', i + 1)
                if (labelEnd > i && text.getOrNull(labelEnd + 1) == '(') {
                    val targetEnd = text.indexOf(')', labelEnd + 2)
                    if (targetEnd > labelEnd + 1) {
                        flush()
                        val target = text.substring(labelEnd + 2, targetEnd).trim()
                        append(
                            text.substring(i + 1, labelEnd),
                            inherited.copy(link = target),
                            out
                        )
                        i = targetEnd + 1
                        continue
                    }
                }
            }

            // Bold: ** or __
            if ((c == '*' || c == '_') && text.getOrNull(i + 1) == c) {
                val marker = "$c$c"
                val end = text.indexOf(marker, i + 2)
                if (end > i + 2) {
                    flush()
                    append(text.substring(i + 2, end), inherited.copy(bold = true), out)
                    i = end + 2
                    continue
                }
            }

            // Italic: a single * or _ that is not part of a bold run.
            if ((c == '*' || c == '_') && text.getOrNull(i + 1) != c) {
                val end = text.indexOf(c, i + 1)
                if (end > i + 1) {
                    flush()
                    append(text.substring(i + 1, end), inherited.copy(italic = true), out)
                    i = end + 1
                    continue
                }
            }

            // Strikethrough: ~~
            if (c == '~' && text.getOrNull(i + 1) == '~') {
                val end = text.indexOf("~~", i + 2)
                if (end > i + 2) {
                    flush()
                    append(text.substring(i + 2, end), inherited.copy(strikethrough = true), out)
                    i = end + 2
                    continue
                }
            }

            literal.append(c)
            i++
        }

        flush()
    }

    /**
     * Coalesces neighbours that ended up with identical formatting.
     *
     * Recursion emits one run per literal stretch, so `a*b*c` produced three. Fewer, longer runs
     * matter downstream: each run becomes a separate text-showing operation in the PDF content
     * stream, and a separate measurement call in the preview.
     */
    private fun merge(runs: List<InlineRun>): List<InlineRun> {
        val merged = ArrayList<InlineRun>(runs.size)
        for (run in runs) {
            if (run.text.isEmpty()) continue
            val last = merged.lastOrNull()
            if (last != null && last.copy(text = "") == run.copy(text = "")) {
                merged[merged.lastIndex] = last.copy(text = last.text + run.text)
            } else {
                merged += run
            }
        }
        return merged
    }
}
