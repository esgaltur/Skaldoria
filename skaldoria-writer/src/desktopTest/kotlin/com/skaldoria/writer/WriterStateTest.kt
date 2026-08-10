package com.skaldoria.writer

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.skaldoria.writer.parser.DocumentParser
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WriterStateTest {

    @Test
    fun editingAndSavingTracksDirtyState() {
        val state = WriterState("hello")
        val store = InMemoryWriterStore()
        val controller = WriterFileController(store)

        state.updateText(TextFieldValue("hello world"))
        assertTrue(state.isDirty)

        assertTrue(controller.save(state, File("notes")))
        assertEquals("hello world", store.files["notes.md"])
        assertEquals("notes.md", state.currentFile?.name)
        assertFalse(state.isDirty)
    }

    @Test
    fun openingDocumentUpdatesTextAstAndCurrentFile() {
        val store = InMemoryWriterStore(mutableMapOf("opened.md" to "# Opened\n\nBody"))
        val state = WriterState("")

        assertTrue(WriterFileController(store).open(state, File("opened.md")))
        assertEquals("# Opened\n\nBody", state.text)
        assertEquals("Opened", state.headings.single().text)
        assertFalse(state.isDirty)
    }

    @Test
    fun failedFileOperationIsReportedWithoutDestroyingDocument() {
        val state = WriterState("keep me")
        val failingStore = object : WriterDocumentStore {
            override fun read(file: File): String = error("disk unavailable")
            override fun write(file: File, content: String) = error("disk unavailable")
        }

        assertFalse(WriterFileController(failingStore).open(state, File("missing.md")))
        assertEquals("keep me", state.text)
        assertTrue(state.errorMessage.orEmpty().contains("disk unavailable"))
    }

    @Test
    fun outlineNavigationSelectsHeadingSourceAndReturnsToEditMode() {
        val markdown = "# First\n\nParagraph\n\n## Second heading\n\nEnd"
        val state = WriterState(markdown)
        state.selectViewMode(ViewMode.Preview)

        assertTrue(state.navigateToHeading(1))
        assertEquals(ViewMode.Edit, state.viewMode)
        assertEquals(
            "Second heading",
            state.textValue.annotatedString.subSequence(
                state.textValue.selection.start,
                state.textValue.selection.end
            ).text
        )
    }

    @Test
    fun previewDisablesFocusMode() {
        val state = WriterState("")
        state.updateFocusMode(true)
        assertTrue(state.isFocusMode)

        state.selectViewMode(ViewMode.Preview)

        assertFalse(state.isFocusMode)
        state.updateFocusMode(true)
        assertFalse(state.isFocusMode)
    }

    @Test
    fun formattingOperatesOnCurrentSelection() {
        val state = WriterState("important")
        state.updateText(TextFieldValue("important", TextRange(0, 9)))

        state.applyFormat(WriterFormat.Bold)

        assertEquals("**important**", state.text)
    }

    @Test
    fun staleBackgroundParseResultIsIgnored() {
        val state = WriterState("# Current")
        state.updateText(TextFieldValue("# New"))

        state.acceptParsedDocument("# Current", DocumentParser().parse("# Stale"))

        assertEquals("Current", state.headings.single().text)
    }

    private class InMemoryWriterStore(
        val files: MutableMap<String, String> = mutableMapOf()
    ) : WriterDocumentStore {
        override fun read(file: File): String = files.getValue(file.name)
        override fun write(file: File, content: String) {
            files[file.name] = content
        }
    }
}
