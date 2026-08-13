package com.skaldoria.cv.core

/**
 * Writes `key: value` pairs into a document's YAML front matter.
 *
 * The top bar lets the user pick a template, theme, or font. Those choices must be reflected back
 * into the `---` front-matter block so the source, the preview, and a later save all agree; before
 * this existed the selections lived only in UI state and the header kept its stale values.
 *
 * Behaviour:
 *  * a well-formed `--- ... ---` block has the key replaced in place, or the pair inserted just
 *    before the closing delimiter when the key is absent;
 *  * a document with no front matter gains a new block at the very top;
 *  * the document's original newline style (`\n` or `\r\n`) is preserved.
 */
class CvFrontMatterEditor {

    fun upsert(source: String, key: String, value: String): String {
        val newline = if (source.contains("\r\n")) "\r\n" else "\n"
        val lines = source.split("\n").map { it.removeSuffix("\r") }.toMutableList()
        val entry = "$key: $value"

        if (lines.firstOrNull()?.trim() == FENCE) {
            // Unclosed front matter still has a region we can edit: treat the rest of the file as it.
            val closingIndex = (1 until lines.size).firstOrNull { lines[it].trim() == FENCE }
            val regionEnd = closingIndex ?: lines.size
            val existingIndex = (1 until regionEnd).firstOrNull { keyOf(lines[it]) == key.lowercase() }
            if (existingIndex != null) {
                lines[existingIndex] = entry
            } else {
                lines.add(regionEnd, entry)
            }
            return lines.joinToString(newline)
        }

        return buildList {
            add(FENCE)
            add(entry)
            add(FENCE)
            addAll(lines)
        }.joinToString(newline)
    }

    /** The lower-cased key of a `key: value` metadata line, or null for blanks, comments, or non-pairs. */
    private fun keyOf(line: String): String? {
        val trimmed = line.trimStart()
        if (trimmed.isBlank() || trimmed.startsWith('#')) return null
        val separator = line.indexOf(':')
        if (separator <= 0) return null
        return line.substring(0, separator).trim().lowercase()
    }

    private companion object {
        const val FENCE = "---"
    }
}
