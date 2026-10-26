package com.skaldoria.writer

import androidx.compose.ui.text.input.TextFieldValue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Opening a file must never silently replace unsaved work. */
class WriterOpenGuardTest {

    private val store = object : WriterDocumentStore {
        override fun read(file: File) = "# Other document"
        override fun write(file: File, content: String) = Unit
    }

    private fun dirtyState() = WriterState("draft").apply { updateText(TextFieldValue("draft, edited")) }

    @Test
    fun `declining the prompt keeps the unsaved edits and never shows the file chooser`() {
        val state = dirtyState()
        var chooserShown = false

        val opened = WriterFileController(store) { false }.requestOpen(state) {
            chooserShown = true
            File("other.md")
        }

        assertFalse(opened)
        assertFalse(chooserShown, "the chooser must not appear once the user chose to keep editing")
        assertEquals("draft, edited", state.text)
        assertTrue(state.isDirty)
    }

    @Test
    fun `confirming the prompt opens the chosen file`() {
        val state = dirtyState()

        val opened = WriterFileController(store) { true }.requestOpen(state) { File("other.md") }

        assertTrue(opened)
        assertEquals("# Other document", state.text)
        assertEquals("other.md", state.currentFile?.name)
    }

    @Test
    fun `a clean document opens without asking`() {
        val state = WriterState("saved")
        var asked = false

        WriterFileController(store) { asked = true; true }.requestOpen(state) { File("other.md") }

        assertFalse(asked, "nothing would be lost, so nothing should be asked")
        assertEquals("# Other document", state.text)
    }

    @Test
    fun `cancelling the chooser leaves the document as it was`() {
        val state = dirtyState()

        assertFalse(WriterFileController(store) { true }.requestOpen(state) { null })
        assertEquals("draft, edited", state.text)
    }
}
