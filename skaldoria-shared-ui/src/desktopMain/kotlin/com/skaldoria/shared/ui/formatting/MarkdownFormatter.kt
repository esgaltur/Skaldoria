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

    private fun toggleLinePrefix(state: TextFieldValue, prefix: String): TextFieldValue {
        val text = state.text
        val selection = state.selection
        
        var lineStart = text.lastIndexOf('\n', maxOf(0, selection.min - 1)) + 1
        if (lineStart < 0) lineStart = 0
        
        val isPrefixed = text.substring(lineStart).startsWith(prefix)
        
        return if (isPrefixed) {
            val newText = text.substring(0, lineStart) + text.substring(lineStart + prefix.length)
            val diff = -prefix.length
            val newStart = maxOf(lineStart, selection.start + diff)
            val newEnd = maxOf(lineStart, selection.end + diff)
            TextFieldValue(newText, TextRange(newStart, newEnd))
        } else {
            val newText = text.substring(0, lineStart) + prefix + text.substring(lineStart)
            val diff = prefix.length
            val newStart = selection.start + diff
            val newEnd = selection.end + diff
            TextFieldValue(newText, TextRange(newStart, newEnd))
        }
    }

    private fun toggleSurrounding(state: TextFieldValue, marker: String): TextFieldValue {
        val text = state.text
        val selection = state.selection

        val start = selection.min
        val end = selection.max

        // Case 1: Markers are immediately outside the current selection.
        val isSurroundedOutside = start >= marker.length && end <= text.length - marker.length &&
            text.substring(start - marker.length, start) == marker &&
            text.substring(end, end + marker.length) == marker

        if (isSurroundedOutside) {
            // Remove the markers from outside the selection
            val newText = text.substring(0, start - marker.length) + 
                          text.substring(start, end) + 
                          text.substring(end + marker.length)
            return TextFieldValue(newText, TextRange(start - marker.length, end - marker.length))
        }

        // Case 2: Markers are immediately inside the current selection.
        val isSurroundedInside = (end - start) >= marker.length * 2 &&
            text.substring(start, start + marker.length) == marker &&
            text.substring(end - marker.length, end) == marker

        if (isSurroundedInside) {
             // Remove the markers from inside the selection
             val newText = text.substring(0, start) + 
                           text.substring(start + marker.length, end - marker.length) + 
                           text.substring(end)
             return TextFieldValue(newText, TextRange(start, end - (marker.length * 2)))
        }

        // Case 3: No markers found. Wrap the selection.
        val newText = text.substring(0, start) + marker + text.substring(start, end) + marker + text.substring(end)
        
        // If there was no selection, place the cursor directly inside the newly added markers.
        // Otherwise, keep the original text selected.
        val newSelection = if (start == end) {
            TextRange(start + marker.length)
        } else {
            TextRange(start + marker.length, end + marker.length)
        }
        
        return TextFieldValue(newText, newSelection)
    }
}
