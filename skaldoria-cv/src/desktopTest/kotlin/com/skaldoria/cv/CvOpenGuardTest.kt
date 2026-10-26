package com.skaldoria.cv

import androidx.compose.ui.text.input.TextFieldValue
import java.io.File
import kotlin.io.path.createTempFile
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Opening a CV must never silently replace unsaved work. Before the guard it did, and the
 * crash-recovery snapshot was cleared with it, because the newly opened document is clean.
 */
class CvOpenGuardTest {

    private val other: File = createTempFile("skaldoria-cv-open", ".md").apply {
        writeText("# Someone Else\n")
    }.toFile()

    @AfterTest
    fun cleanUp() {
        other.delete()
    }

    private fun dirtyStore() = CvStore("# Draft\n").apply {
        dispatch(CvEvent.SourceChanged(TextFieldValue("# Draft, edited\n")))
    }

    @Test
    fun `declining the prompt keeps the unsaved CV and never shows the file chooser`() {
        val store = dirtyStore()
        var chooserShown = false

        CvFileController { false }.requestOpen(store) {
            chooserShown = true
            other
        }

        assertFalse(chooserShown)
        assertEquals("# Draft, edited\n", store.state.source.text)
        assertTrue(store.state.isDirty)
    }

    @Test
    fun `confirming the prompt opens the chosen CV`() {
        val store = dirtyStore()

        CvFileController { true }.requestOpen(store) { other }

        assertEquals("# Someone Else\n", store.state.source.text)
        assertEquals(other, store.state.currentFile)
    }

    @Test
    fun `a clean CV opens without asking`() {
        val store = CvStore("# Draft\n")
        var asked = false

        CvFileController { asked = true; true }.requestOpen(store) { other }

        assertFalse(asked)
        assertEquals("# Someone Else\n", store.state.source.text)
    }
}
