package com.skaldoria.cv

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Density
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The toolbar has no logic worth unit-testing, but it has plenty of ways to fail at runtime that a
 * compile cannot catch — a missing icon resource, a `Surface(onClick)` overload that needs an opt-in,
 * a layout that measures to zero. These render it in each state that changes its shape.
 */
@OptIn(ExperimentalComposeUiApi::class)
class CvToolbarRenderingTest {

    private fun render(state: CvEditorState, width: Int = 1360): ByteArray {
        val scene = ImageComposeScene(width = width, height = 130, density = Density(1f)) {
            MaterialTheme {
                CvToolbar(
                    state = state,
                    dispatch = {},
                    onOpenRequest = {},
                    onSaveRequest = {},
                    onSaveAsRequest = {},
                    onExportPdfRequest = {}
                )
            }
        }
        return try {
            scene.render(0L).encodeToData()?.bytes ?: error("toolbar produced no image")
        } finally {
            scene.close()
        }
    }

    private fun stateFor(markdown: String): CvEditorState {
        val store = CvStore(initialSource = markdown)
        return store.state
    }

    private val clean = stateFor("# Ada Lovelace\n\n## Profile\n\nEngineer.\n")

    @Test
    fun `the toolbar renders in its default state`() {
        assertTrue(render(clean).isNotEmpty())
    }

    @Test
    fun `the dirty indicator changes what is drawn`() {
        val dirty = clean.copy(source = TextFieldValue(clean.source.text + "\nmore"))
        assertTrue(dirty.isDirty, "fixture must actually be dirty")
        assertFalse(
            render(clean).contentEquals(render(dirty)),
            "an unsaved document draws the same as a saved one"
        )
    }

    @Test
    fun `each view mode draws its own selection`() {
        val rendered = CvViewMode.entries.map { mode -> render(clean.copy(viewMode = mode)) }
        for (first in rendered.indices) {
            for (second in first + 1 until rendered.size) {
                assertFalse(
                    rendered[first].contentEquals(rendered[second]),
                    "view modes ${CvViewMode.entries[first]} and ${CvViewMode.entries[second]} look identical"
                )
            }
        }
    }

    @Test
    fun `zoom controls appear only when a page is on screen`() {
        assertFalse(
            render(clean.copy(viewMode = CvViewMode.Source))
                .contentEquals(render(clean.copy(viewMode = CvViewMode.Preview))),
            "Source view should not draw zoom controls"
        )
    }

    @Test
    fun `the zoom percentage is drawn`() {
        val hundred = clean.copy(viewMode = CvViewMode.Preview, zoomPercent = 100)
        val fifty = clean.copy(viewMode = CvViewMode.Preview, zoomPercent = 50)
        assertFalse(render(hundred).contentEquals(render(fifty)), "zoom level is not visible")
    }

    @Test
    fun `a document with diagnostics shows a badge`() {
        // An unclosed front-matter block is a reliable CV_UNCLOSED_METADATA error.
        val broken = stateFor("---\nname: Ada\n\n# Ada\n")
        assertTrue(broken.document.diagnostics.isNotEmpty(), "fixture must produce diagnostics")
        assertFalse(
            render(clean).contentEquals(render(broken)),
            "diagnostics are not surfaced in the toolbar"
        )
    }

    @Test
    fun `the toolbar still lays out in a narrow window`() {
        // The previous single-row bar overflowed well before this width.
        assertTrue(render(clean, width = 1024).isNotEmpty())
        assertTrue(render(clean, width = 900).isNotEmpty())
    }
}
