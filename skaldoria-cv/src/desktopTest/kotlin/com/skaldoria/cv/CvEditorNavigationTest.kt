package com.skaldoria.cv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Outline navigation (CV-FR-024), find and replace (CV-FR-025) and the fit controls (CV-FR-047),
 * as they behave through the store.
 */
class CvEditorNavigationTest {

    private val markdown = """
        |# Ada Lovelace
        |
        |## Profile
        |
        |Kotlin engineer.
        |
        |## Experience
        |
        |### Staff Engineer
        |
        |- Led the Kotlin migration.
    """.trimMargin()

    private fun store() = CvStore(initialSource = markdown)

    // ---------------------------------------------------------------------
    // Line and offset arithmetic
    // ---------------------------------------------------------------------

    @Test
    fun `an offset and a line describe the same position from either side`() {
        for (line in 1..11) {
            assertEquals(
                line,
                lineOfOffset(markdown, offsetOfLine(markdown, line)),
                "line $line did not survive the round trip"
            )
        }
    }

    @Test
    fun `line one starts at the beginning and a line past the end clamps`() {
        assertEquals(0, offsetOfLine(markdown, 1))
        assertEquals(0, offsetOfLine(markdown, 0))
        assertEquals(markdown.length, offsetOfLine(markdown, 9_000))
    }

    @Test
    fun `an offset resolves to the line whose text contains it`() {
        val profileHeading = offsetOfLine(markdown, 3)
        assertEquals("## Profile", markdown.substring(profileHeading, profileHeading + 10))
    }

    // ---------------------------------------------------------------------
    // Outline — CV-FR-024
    // ---------------------------------------------------------------------

    @Test
    fun `the outline lists what the document contains`() {
        assertEquals(
            listOf("Profile", "Experience", "Staff Engineer"),
            store().state.outline.map { it.title }
        )
    }

    @Test
    fun `selecting a row moves the caret onto that heading`() {
        val store = store()
        val experience = store.state.outline.first { it.title == "Experience" }

        store.dispatch(CvEvent.OutlineItemSelected(experience))

        assertEquals(offsetOfLine(markdown, 7), store.state.source.selection.start)
        assertEquals(7, store.state.caretLine)
        assertTrue(store.state.source.selection.collapsed, "navigating should not select the heading")
    }

    @Test
    fun `selecting a row asks the preview to show the page holding it`() {
        val store = store()
        val entry = store.state.outline.first { it.title == "Staff Engineer" }

        store.dispatch(CvEvent.OutlineItemSelected(entry))

        val request = assertNotNull(store.state.navigation)
        assertEquals(9, request.line)
    }

    @Test
    fun `picking the same row twice asks again rather than going quiet`() {
        val store = store()
        val profile = store.state.outline.first { it.title == "Profile" }

        store.dispatch(CvEvent.OutlineItemSelected(profile))
        val first = assertNotNull(store.state.navigation)
        store.dispatch(CvEvent.OutlineItemSelected(profile))
        val second = assertNotNull(store.state.navigation)

        assertEquals(first.line, second.line)
        assertTrue(second.serial > first.serial, "a repeated selection must be a new request")
    }

    @Test
    fun `the outline follows the document as it is edited`() {
        val store = store()
        store.dispatch(
            CvEvent.SourceChanged(
                store.state.source.copy(text = markdown + "\n\n## Education\n\n- BSc\n")
            )
        )

        assertTrue("Education" in store.state.outline.map { it.title })
    }

    @Test
    fun `the panel can be put away`() {
        val store = store()
        assertTrue(store.state.isOutlineVisible)

        store.dispatch(CvEvent.ToggleOutline)

        assertFalse(store.state.isOutlineVisible)
    }

    // ---------------------------------------------------------------------
    // Find and replace — CV-FR-025
    // ---------------------------------------------------------------------

    @Test
    fun `the shared controller searches this editor's source`() {
        val store = store()
        store.findReplace.query = "Kotlin"

        assertEquals(2, store.findReplace.matches.size)
    }

    @Test
    fun `revealing a match selects it so the field scrolls it into view`() {
        val store = store()
        store.findReplace.query = "Kotlin"

        store.dispatch(CvEvent.FindMatchRevealed)

        val selection = store.state.source.selection
        assertEquals("Kotlin", store.state.source.text.substring(selection.start, selection.end))
        assertEquals(markdown.indexOf("Kotlin"), selection.start)
    }

    @Test
    fun `stepping to the next match moves the selection to it`() {
        val store = store()
        store.findReplace.query = "Kotlin"
        store.findReplace.findNext()

        store.dispatch(CvEvent.FindMatchRevealed)

        assertEquals(markdown.lastIndexOf("Kotlin"), store.state.source.selection.start)
    }

    @Test
    fun `revealing nothing when there is nothing to reveal leaves the caret alone`() {
        val store = store()
        store.findReplace.query = "nowhere in this document"
        val before = store.state.source.selection

        store.dispatch(CvEvent.FindMatchRevealed)

        assertEquals(before, store.state.source.selection)
    }

    @Test
    fun `replacing goes through the store, so the preview and undo history follow`() {
        val store = store()
        store.findReplace.query = "Kotlin"
        store.findReplace.replacement = "Rust"

        store.findReplace.replaceAll()

        assertFalse("Kotlin" in store.state.source.text)
        assertEquals(2, store.state.source.text.split("Rust").size - 1)
        assertTrue(store.state.canUndo, "a replacement is an edit and must be undoable")
        assertTrue(store.state.isDirty)

        store.dispatch(CvEvent.Undo)
        assertEquals(markdown, store.state.source.text)
    }

    @Test
    fun `a replacement that shortens the document keeps the caret inside it`() {
        val store = store()
        store.dispatch(
            CvEvent.SourceChanged(
                store.state.source.copy(selection = androidx.compose.ui.text.TextRange(markdown.length))
            )
        )
        store.findReplace.query = "Led the Kotlin migration."
        store.findReplace.replacement = "-"

        store.findReplace.replaceAll()

        assertTrue(
            store.state.source.selection.start <= store.state.source.text.length,
            "caret left the buffer after the text shrank"
        )
    }

    // ---------------------------------------------------------------------
    // Zoom and fit — CV-FR-047
    // ---------------------------------------------------------------------

    @Test
    fun `choosing a fit mode records it`() {
        val store = store()

        store.dispatch(CvEvent.ZoomFitWidth)
        assertEquals(CvZoomFit.Width, store.state.zoomFit)

        store.dispatch(CvEvent.ZoomFitPage)
        assertEquals(CvZoomFit.Page, store.state.zoomFit)
    }

    @Test
    fun `an explicit zoom stops the scale moving with the window`() {
        val store = store()
        store.dispatch(CvEvent.ZoomFitPage)

        store.dispatch(CvEvent.ZoomIn)

        assertEquals(CvZoomFit.None, store.state.zoomFit)
    }

    @Test
    fun `actual size is both a percentage and the end of fit mode`() {
        val store = store()
        store.dispatch(CvEvent.ZoomFitWidth)

        store.dispatch(CvEvent.ZoomReset)

        assertEquals(CvZoomPolicy.DefaultPercent, store.state.zoomPercent)
        assertEquals(CvZoomFit.None, store.state.zoomFit)
    }

    @Test
    fun `zooming in continues from the scale a fit mode resolved to`() {
        val store = store()
        store.dispatch(CvEvent.ZoomFitWidth)
        // What the preview measured for the current viewport.
        store.dispatch(CvEvent.ZoomResolved(70))

        store.dispatch(CvEvent.ZoomIn)

        assertEquals(70 + CvZoomPolicy.StepPercent, store.state.zoomPercent)
    }

    @Test
    fun `navigation state does not survive opening another document`() {
        val store = store()
        store.dispatch(CvEvent.OutlineItemSelected(store.state.outline.first()))
        assertNotNull(store.state.navigation)

        store.dispatch(CvEvent.DocumentOpened(java.io.File("other.md"), "# Other\n\n## Skills\n\n- Kotlin\n"))

        assertNull(store.state.navigation, "a stale scroll request would jump the new document")
    }
}
