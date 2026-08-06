package com.skaldoria.core.editor

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Find & replace over the source editor's text.
 *
 * F-11: extracted from `PresentationState`, where eight properties and seven functions with
 * nothing to do with presenting a deck sat among eleven other responsibilities.
 *
 * The text is reached through [text]/[onTextChanged] rather than by holding a reference to
 * the document, so this class has no idea whether it is editing a single markdown file or one
 * slide of a multi-file project — which is exactly the distinction the caller owns.
 *
 * @param text the buffer being searched, read fresh on each access.
 * @param onTextChanged applies a replacement back to whatever owns the buffer.
 */
class FindReplaceController(
    private val text: () -> String,
    private val onTextChanged: (String) -> Unit
) {

    var isOpen by mutableStateOf(false)
        private set

    var isReplaceOpen by mutableStateOf(false)
        private set

    var query by mutableStateOf("")
    var replacement by mutableStateOf("")
    var isCaseSensitive by mutableStateOf(false)
    var isWholeWord by mutableStateOf(false)
    var isRegex by mutableStateOf(false)
    var currentMatchIndex by mutableStateOf(0)

    /**
     * PRF-3: cached.
     *
     * This was an uncached computed property running `Regex.findAll` over the whole document
     * — read three times inside `replaceCurrent` alone, and on every recomposition of the
     * find bar, i.e. a full-document scan per frame while typing. `derivedStateOf` recomputes
     * only when the text, the query, or a flag actually changes.
     */
    private val matchesState = derivedStateOf {
        val needle = query
        if (needle.isEmpty()) return@derivedStateOf emptyList()
        val haystack = text()
        if (haystack.isEmpty()) return@derivedStateOf emptyList()

        val pattern = compile(needle) ?: return@derivedStateOf emptyList()
        pattern.findAll(haystack).map { it.range }.toList()
    }

    val matches: List<IntRange>
        get() = matchesState.value

    /**
     * The active pattern, or null when the query cannot compile.
     *
     * A regex the user is halfway through typing is *expected* to be invalid, so this is a
     * normal outcome rather than an error to report.
     */
    private fun compile(needle: String): Regex? = try {
        val options = if (isCaseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
        if (isRegex) {
            Regex(needle, options)
        } else {
            val literal = Regex.escape(needle)
            Regex(if (isWholeWord) "\\b$literal\\b" else literal, options)
        }
    } catch (_: Exception) {
        null
    }

    fun open(withReplace: Boolean = false) {
        isOpen = true
        if (withReplace) isReplaceOpen = true
    }

    fun close() {
        isOpen = false
        isReplaceOpen = false
    }

    /** Shows or hides the replace row from the find bar's own chevron. */
    fun toggleReplaceRow() {
        isReplaceOpen = !isReplaceOpen
    }

    /**
     * Ctrl+F / Ctrl+H.
     *
     * Pressing Ctrl+H while plain Find is open *widens* the bar to include replace rather
     * than closing it; only repeating the same mode closes.
     */
    fun toggle(withReplace: Boolean = false) {
        if (isOpen && (!withReplace || isReplaceOpen)) close() else open(withReplace)
    }

    fun findNext() {
        val found = matches
        if (found.isNotEmpty()) currentMatchIndex = (currentMatchIndex + 1) % found.size
    }

    fun findPrevious() {
        val found = matches
        if (found.isNotEmpty()) currentMatchIndex = (currentMatchIndex - 1 + found.size) % found.size
    }

    fun replaceCurrent() {
        val found = matches
        if (found.isEmpty()) return

        val safeIndex = currentMatchIndex.coerceIn(0, found.size - 1)
        val range = found[safeIndex]
        val haystack = text()
        if (range.first < 0 || range.last >= haystack.length || range.first > range.last) return

        onTextChanged(haystack.substring(0, range.first) + replacement + haystack.substring(range.last + 1))

        // The replacement changes the match set; keep the cursor inside the new one.
        currentMatchIndex = if (matches.isEmpty()) 0 else safeIndex.coerceIn(0, matches.size - 1)
    }

    fun replaceAll() {
        if (matches.isEmpty()) return
        val haystack = text()
        val pattern = compile(query) ?: return
        onTextChanged(pattern.replace(haystack, replacement))
        currentMatchIndex = 0
    }
}
