package com.skaldoria.cv

import androidx.compose.ui.text.input.TextFieldValue
import com.skaldoria.cv.core.CvTemplateId
import com.skaldoria.cv.core.CvFontId
import com.skaldoria.cv.core.CvThemeId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CvStoreTest {
    @Test
    fun `source event updates semantic preview and dirty state`() {
        val store = CvStore("# Original")

        store.dispatch(CvEvent.SourceChanged(TextFieldValue("# Updated\n\n## Skills\n\n- Kotlin")))

        assertEquals("Updated", store.state.document.candidateName)
        assertEquals("Skills", store.state.document.sections.single().title)
        assertTrue(store.state.isDirty)
    }

    @Test
    fun `view switching retains source and parsed document`() {
        val store = CvStore("# Candidate\n\n## Experience")
        val document = store.state.document

        store.dispatch(CvEvent.ViewModeSelected(CvViewMode.Preview))
        store.dispatch(CvEvent.ViewModeSelected(CvViewMode.Source))

        assertEquals("# Candidate\n\n## Experience", store.state.source.text)
        assertEquals(document, store.state.document)
        assertFalse(store.state.isDirty)
    }

    @Test
    fun `theme switching never rewrites or dirties Markdown`() {
        val store = CvStore("# Candidate\n\n## Experience")

        store.dispatch(CvEvent.ThemeSelected(CvThemeId.Forest))

        assertEquals(CvThemeId.Forest, store.state.themeId)
        assertEquals("# Candidate\n\n## Experience", store.state.source.text)
        assertFalse(store.state.isDirty)
    }

    @Test
    fun `bundled software engineer example selects metadata theme`() {
        val store = CvStore()

        assertEquals("Alex Morgan", store.state.document.candidateName)
        assertEquals("Senior Software Engineer · Kotlin · Distributed Systems", store.state.document.professionalHeadline)
        assertEquals(CvTemplateId.SoftwareEngineerAts, store.state.templateId)
        assertEquals(CvThemeId.ModernBlue, store.state.themeId)
        assertEquals(CvFontId.Roboto, store.state.fontId)
        assertTrue(store.state.document.sections.any { it.title == "Experience" && it.entries.size == 3 })
        assertTrue(store.state.document.diagnostics.isEmpty())
    }

    @Test
    fun `font switching changes presentation only`() {
        val store = CvStore("# Candidate")

        store.dispatch(CvEvent.FontSelected(CvFontId.Georgia))

        assertEquals(CvFontId.Georgia, store.state.fontId)
        assertEquals("# Candidate", store.state.source.text)
        assertFalse(store.state.isDirty)
    }

    @Test
    fun `zoom is bounded resettable and never dirties Markdown`() {
        val store = CvStore("# Candidate")

        repeat(20) { store.dispatch(CvEvent.ZoomIn) }
        assertEquals(CvZoomPolicy.MaximumPercent, store.state.zoomPercent)
        repeat(30) { store.dispatch(CvEvent.ZoomOut) }
        assertEquals(CvZoomPolicy.MinimumPercent, store.state.zoomPercent)
        store.dispatch(CvEvent.ZoomReset)

        assertEquals(CvZoomPolicy.DefaultPercent, store.state.zoomPercent)
        assertEquals("# Candidate", store.state.source.text)
        assertFalse(store.state.isDirty)
    }

    @Test
    fun `metadata theme edits stay live until user explicitly overrides them`() {
        val store = CvStore("---\ntheme: modern-blue\n---\n# Candidate")

        store.dispatch(CvEvent.SourceChanged(TextFieldValue("---\ntheme: graphite\n---\n# Candidate")))
        assertEquals(CvThemeId.Graphite, store.state.themeId)

        store.dispatch(CvEvent.ThemeSelected(CvThemeId.Forest))
        store.dispatch(CvEvent.SourceChanged(TextFieldValue("---\ntheme: warm-minimal\n---\n# Candidate")))
        assertEquals(CvThemeId.Forest, store.state.themeId)
    }

    @Test
    fun `legacy template palette metadata remains compatible`() {
        val store = CvStore("---\ntemplate: warm-minimal\n---\n# Candidate")

        assertEquals(CvTemplateId.SoftwareEngineerAts, store.state.templateId)
        assertEquals(CvThemeId.WarmMinimal, store.state.themeId)
    }

    @Test
    fun `metadata font edits stay live until user explicitly overrides them`() {
        val store = CvStore("---\nfont: roboto\n---\n# Candidate")

        store.dispatch(CvEvent.SourceChanged(TextFieldValue("---\nfont: arial\n---\n# Candidate")))
        assertEquals(CvFontId.Arial, store.state.fontId)

        store.dispatch(CvEvent.FontSelected(CvFontId.Georgia))
        store.dispatch(CvEvent.SourceChanged(TextFieldValue("---\nfont: inter\n---\n# Candidate")))
        assertEquals(CvFontId.Georgia, store.state.fontId)
    }

    @Test
    fun `undo and redo keep metadata-derived presentation state consistent with source`() {
        val modern = "---\ntheme: modern-blue\nfont: roboto\ntemplate: software-engineer-ats\n---\n# Candidate"
        val graphite = "---\ntheme: graphite\nfont: arial\ntemplate: classic-ats\n---\n# Candidate"
        val store = CvStore(modern)

        store.dispatch(CvEvent.SourceChanged(TextFieldValue(graphite)))
        assertEquals(CvThemeId.Graphite, store.state.themeId)
        assertEquals(CvFontId.Arial, store.state.fontId)

        store.dispatch(CvEvent.Undo)
        assertEquals(modern, store.state.source.text)
        assertEquals(CvThemeId.ModernBlue, store.state.themeId)
        assertEquals(CvFontId.Roboto, store.state.fontId)

        store.dispatch(CvEvent.Redo)
        assertEquals(graphite, store.state.source.text)
        assertEquals(CvThemeId.Graphite, store.state.themeId)
        assertEquals(CvFontId.Arial, store.state.fontId)
    }

    @Test
    fun `source history remains bounded during a long editing session`() {
        val store = CvStore("# Candidate")

        repeat(CvStore.HISTORY_LIMIT + 25) { index ->
            store.dispatch(CvEvent.SourceChanged(TextFieldValue("# Candidate $index")))
        }

        assertEquals(CvStore.HISTORY_LIMIT, store.state.undoStack.size)
    }
}
