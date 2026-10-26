package com.skaldoria.shared.ui.formatting

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Handles toggling markdown formatting within a TextFieldValue.
 */
object MarkdownFormatter {
    fun toggleBold(state: TextFieldValue): TextFieldValue = toggleSurrounding(state, "**")
    fun toggleItalic(state: TextFieldValue): TextFieldValue = toggleSurrounding(state, "*")
    fun toggleStrikethrough(state: TextFieldValue): TextFieldValue = toggleSurrounding(state, "~~")
    fun toggleCode(state: TextFieldValue): TextFieldValue = toggleSurrounding(state, "`")

    fun toggleHeader1(state: TextFieldValue) = toggleLinePrefix(state, "# ")
    fun toggleHeader2(state: TextFieldValue) = toggleLinePrefix(state, "## ")
    fun toggleHeader3(state: TextFieldValue) = toggleLinePrefix(state, "### ")
    fun toggleQuote(state: TextFieldValue) = toggleLinePrefix(state, "> ")
    fun toggleList(state: TextFieldValue) = toggleLinePrefix(state, "- ")
    fun toggleChecklist(state: TextFieldValue) = toggleLinePrefix(state, "- [ ] ")

    /**
     * Line prefixes that are alternatives to one another. Applying one replaces any other from the
     * same family instead of stacking on top of it — H1 on `## Title` gave `# ## Title`, and
     * Checklist on `- item` gave `- [ ] - item`. Longest first, so `- [ ] ` is recognised before
     * the `- ` it starts with.
     */
    private val PREFIX_FAMILIES = listOf(
        listOf("### ", "## ", "# "),
        listOf("- [ ] ", "- [x] ", "- [X] ", "- "),
        listOf("> ")
    )

    /**
     * Toggles [prefix] on every line the selection touches, not only the first.
     *
     * When every touched line already carries exactly [prefix] it is removed from all of them;
     * otherwise each line gets it, replacing a sibling from its family. A selection that ends at
     * the very start of a line does not pull that line in, as in every other editor.
     */
    private fun toggleLinePrefix(state: TextFieldValue, prefix: String): TextFieldValue {
        val text = state.text
        val family = PREFIX_FAMILIES.firstOrNull { prefix in it } ?: listOf(prefix)
        val selection = state.selection

        val lastOffset = if (!selection.collapsed && text.getOrNull(selection.max - 1) == '\n') {
            selection.max - 1
        } else {
            selection.max
        }
        val lineStarts = generateSequence(lineStartOf(text, selection.min)) { start ->
            text.indexOf('\n', start).takeIf { it in 0 until lastOffset }?.plus(1)
        }.toList()

        fun existingPrefix(lineStart: Int) = family.firstOrNull { text.startsWith(it, lineStart) }
        val removing = lineStarts.all { existingPrefix(it) == prefix }

        val builder = StringBuilder(text)
        var start = selection.start
        var end = selection.end
        for (lineStart in lineStarts.asReversed()) {
            val oldLength = existingPrefix(lineStart)?.length ?: 0
            val replacement = if (removing) "" else prefix
            builder.replace(lineStart, lineStart + oldLength, replacement)

            fun shift(offset: Int): Int = when {
                offset >= lineStart + oldLength -> offset + replacement.length - oldLength
                offset >= lineStart -> lineStart + replacement.length
                else -> offset
            }
            start = shift(start)
            end = shift(end)
        }
        return TextFieldValue(builder.toString(), TextRange(start, end))
    }

    private fun lineStartOf(text: String, offset: Int): Int = text.lastIndexOf('\n', offset - 1) + 1

    /**
     * Whether runs of [left] and [right] marker characters around a span apply [marker].
     *
     * `*` is special because `**` is made of it: italic is present when the run is odd (`*x*`,
     * `***x***`). Treating any `*` as italic made Italic on a bold word strip half of the bold
     * markers and turn `**word**` into `*word*`.
     */
    private fun isWrapped(left: Int, right: Int, marker: String): Boolean {
        val run = minOf(left, right)
        return if (marker == "*") run % 2 == 1 else run >= marker.length
    }

    private fun runBackward(text: String, from: Int, char: Char, limit: Int = 0): Int {
        var count = 0
        while (from - count - 1 >= limit && text[from - count - 1] == char) count++
        return count
    }

    private fun runForward(text: String, from: Int, char: Char, limit: Int): Int {
        var count = 0
        while (from + count < limit && text[from + count] == char) count++
        return count
    }

    private fun toggleSurrounding(state: TextFieldValue, marker: String): TextFieldValue {
        val text = state.text
        val selection = state.selection
        val char = marker.first()

        val start = selection.min
        val end = selection.max

        // Case 1: the markers sit immediately outside the selection.
        val outsideLeft = runBackward(text, start, char)
        val outsideRight = runForward(text, end, char, text.length)
        if (isWrapped(outsideLeft, outsideRight, marker)) {
            val newText = text.substring(0, start - marker.length) +
                text.substring(start, end) +
                text.substring(end + marker.length)
            return TextFieldValue(newText, TextRange(start - marker.length, end - marker.length))
        }

        // Case 2: the selection includes the markers.
        if (end - start >= marker.length * 2) {
            val insideLeft = runForward(text, start, char, end)
            val insideRight = runBackward(text, end, char, start)
            if (insideLeft < end - start && isWrapped(insideLeft, insideRight, marker)) {
                val newText = text.substring(0, start) +
                    text.substring(start + marker.length, end - marker.length) +
                    text.substring(end)
                return TextFieldValue(newText, TextRange(start, end - marker.length * 2))
            }
        }

        // Case 3: not applied yet. Wrap the selection; an empty one leaves the caret inside.
        val newText = text.substring(0, start) + marker + text.substring(start, end) + marker + text.substring(end)
        val newSelection = if (start == end) {
            TextRange(start + marker.length)
        } else {
            TextRange(start + marker.length, end + marker.length)
        }
        return TextFieldValue(newText, newSelection)
    }
}
