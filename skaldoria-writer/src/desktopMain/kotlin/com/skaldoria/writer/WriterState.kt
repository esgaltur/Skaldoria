package com.skaldoria.writer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.skaldoria.markdown.parser.HeadingRules
import com.skaldoria.shared.ui.formatting.MarkdownFormatter
import com.skaldoria.shared.ui.theme.Themes
import com.skaldoria.writer.parser.Document
import com.skaldoria.writer.parser.DocumentParser
import com.skaldoria.writer.parser.Heading
import java.io.File

enum class ViewMode { Edit, Split, Preview }
enum class EditingMode { Visual, Source }
enum class WriterFormat { Bold, Italic, Strikethrough, Code, Heading1, Heading2, Quote, List, Checklist }

/** Owns the writer's document and UI state independently from Compose rendering. */
class WriterState(
    initialText: String = STARTER_MARKDOWN,
    private val parser: DocumentParser = DocumentParser()
) {
    var textValue by mutableStateOf(TextFieldValue(initialText))
        private set

    var document by mutableStateOf(parser.parse(initialText))
        private set

    var currentFile by mutableStateOf<File?>(null)
        private set

    var viewMode by mutableStateOf(ViewMode.Edit)
        private set

    var editingMode by mutableStateOf(EditingMode.Visual)
        private set

    var isFocusMode by mutableStateOf(false)
        private set

    var isSidebarOpen by mutableStateOf(true)
        private set

    var currentThemeIndex by mutableStateOf(0)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    private var savedText by mutableStateOf(initialText)

    val text: String get() = textValue.text
    val theme get() = Themes.all[currentThemeIndex]
    val headings: List<Heading> get() = document.blocks.filterIsInstance<Heading>()
    val isDirty: Boolean get() = text != savedText
    val wordCount: Int get() = WORD_PATTERN.findAll(text).count()
    val readTimeMinutes: Int get() = maxOf(1, wordCount / WORDS_PER_MINUTE)

    fun updateText(value: TextFieldValue) {
        textValue = value
    }

    fun acceptParsedDocument(sourceText: String, parsedDocument: Document) {
        if (sourceText == text) document = parsedDocument
    }

    fun load(file: File, content: String) {
        currentFile = file
        savedText = content
        textValue = TextFieldValue(content)
        document = parser.parse(content)
        errorMessage = null
    }

    fun markSaved(file: File) {
        currentFile = file
        savedText = text
        errorMessage = null
    }

    fun reportError(message: String) {
        errorMessage = message
    }

    fun clearError() {
        errorMessage = null
    }

    fun selectViewMode(mode: ViewMode) {
        viewMode = mode
        if (mode == ViewMode.Preview) isFocusMode = false
    }

    fun selectEditingMode(mode: EditingMode) {
        editingMode = mode
    }

    fun toggleSidebar() {
        isSidebarOpen = !isSidebarOpen
    }

    fun updateFocusMode(enabled: Boolean) {
        if (viewMode != ViewMode.Preview) isFocusMode = enabled
    }

    fun toggleFocusMode() {
        updateFocusMode(!isFocusMode)
    }

    /** Pick a theme directly. [cycleTheme] remains for the keyboard path. */
    fun selectTheme(index: Int) {
        if (index in Themes.all.indices) currentThemeIndex = index
    }

    fun cycleTheme() {
        currentThemeIndex = (currentThemeIndex + 1) % Themes.all.size
    }

    fun applyFormat(format: WriterFormat) {
        textValue = when (format) {
            WriterFormat.Bold -> MarkdownFormatter.toggleBold(textValue)
            WriterFormat.Italic -> MarkdownFormatter.toggleItalic(textValue)
            WriterFormat.Strikethrough -> MarkdownFormatter.toggleStrikethrough(textValue)
            WriterFormat.Code -> MarkdownFormatter.toggleCode(textValue)
            WriterFormat.Heading1 -> MarkdownFormatter.toggleHeader1(textValue)
            WriterFormat.Heading2 -> MarkdownFormatter.toggleHeader2(textValue)
            WriterFormat.Quote -> MarkdownFormatter.toggleQuote(textValue)
            WriterFormat.List -> MarkdownFormatter.toggleList(textValue)
            WriterFormat.Checklist -> MarkdownFormatter.toggleChecklist(textValue)
        }
    }

    fun navigateToHeading(headingIndex: Int): Boolean {
        if (headingIndex !in headings.indices) return false
        var sourceOffset = 0
        var parsedHeadingIndex = 0

        text.split('\n').forEach { line ->
            val heading = HeadingRules.heading(line)
            if (heading != null) {
                if (parsedHeadingIndex == headingIndex) {
                    val contentStartInLine = line.indexOf(heading.text).coerceAtLeast(0)
                    val selectionStart = sourceOffset + contentStartInLine
                    textValue = textValue.copy(
                        selection = TextRange(selectionStart, selectionStart + heading.text.length)
                    )
                    viewMode = ViewMode.Edit
                    return true
                }
                parsedHeadingIndex++
            }
            sourceOffset += line.length + 1
        }
        return false
    }

    companion object {
        const val STARTER_MARKDOWN = """# Welcome to Skaldoria Writer

Write in **Visual** or **Source** mode, then use Split view for a live preview.

## Start here

Select text and use the formatting toolbar, or press Ctrl+B / Ctrl+I."""

        private val WORD_PATTERN = Regex("""\S+""")
        private const val WORDS_PER_MINUTE = 200
    }
}
