package com.skaldoria.core.deck

import com.skaldoria.core.models.DeckProject
import com.skaldoria.core.models.SlideFileEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * F-13: the deck's text, its parse and its project files — independent of navigation, the
 * talk clock, the companion server and everything else that shared a class with them.
 *
 * The two invariants this area exists to hold, exercised directly for the first time:
 *
 * COR-1 — structural edits go through `SlideDocument`, which reads the parser's own slide
 * boundaries, so they agree with what the deck actually renders.
 *
 * COR-2 — in project mode the combined markdown is derived. An edit is applied to the owning
 * *file* and the combined text recompiled, never edited in place.
 */
class DeckDocumentTest {

    private var changes = mutableListOf<String>()
    private fun document(markdown: String) = DeckDocument(markdown) { changes.add(it) }

    private fun projectOf(vararg contents: String) = DeckProject(
        name = "T", rootDir = "/tmp/d", manifestPath = null,
        slideFiles = contents.mapIndexed { i, c ->
            SlideFileEntry("s$i.md", "/tmp/d/s$i.md", c)
        }.toMutableList()
    )

    // ---- flat documents ----

    @Test
    fun `parses on construction`() {
        val doc = document("# One\n\n---\n\n# Two")
        assertEquals(2, doc.slides.size)
        assertFalse(doc.isProjectMode)
    }

    @Test
    fun `replaceAll reparses and notifies`() {
        val doc = document("# One")
        doc.replaceAll("# A\n\n---\n\n# B\n\n---\n\n# C")
        assertEquals(3, doc.slides.size)
        assertEquals(listOf("# A\n\n---\n\n# B\n\n---\n\n# C"), changes)
    }

    @Test
    fun `structural edits return where the cursor should land`() {
        val doc = document("# A\n\n---\n\n# B\n\n---\n\n# C")

        assertEquals(2, doc.duplicate(1), "the copy sits after the original")
        assertEquals(4, doc.slides.size)

        assertEquals(0, doc.delete(1))
        assertEquals(3, doc.slides.size)

        assertEquals(2, doc.insert(1, "## Inserted\n"))
        assertEquals(4, doc.slides.size)
    }

    @Test
    fun `an impossible edit returns null and changes nothing`() {
        val doc = document("# Only")
        val before = doc.markdown
        assertNull(doc.duplicate(99))
        assertNull(doc.delete(99))
        assertEquals(before, doc.markdown)
    }

    @Test
    fun `move reorders a flat deck`() {
        val doc = document("# A\n\n---\n\n# B\n\n---\n\n# C")
        assertEquals(0, doc.move(2, 0))
        assertTrue(doc.slides.first().title.contains("C"), "C should now be first: ${doc.slides.map { it.title }}")
    }

    // ---- project mode ----

    @Test
    fun `adopting a project compiles its files into the deck`() {
        val doc = document("# Scratch")
        doc.adopt(projectOf("# One", "# Two"))

        assertTrue(doc.isProjectMode)
        assertEquals(2, doc.slides.size)
        assertTrue(doc.markdown.contains("# One") && doc.markdown.contains("# Two"))
    }

    /** COR-2: the edit must reach the file, not just the derived combined text. */
    @Test
    fun `editing a slide writes through to its owning file`() {
        val doc = document("")
        doc.adopt(projectOf("# One", "# Two"))

        doc.updateEditorContent(slideIndex = 1, newContent = "# Two edited")

        assertEquals("# Two edited", doc.project!!.slideFiles[1].content, "COR-2: the file must hold the edit")
        assertTrue(doc.markdown.contains("# Two edited"), "and the combined text is recompiled from it")
        assertTrue(doc.markdown.contains("# One"), "the untouched file survives")
    }

    @Test
    fun `an edit survives the next recompile`() {
        val doc = document("")
        doc.adopt(projectOf("# One", "# Two"))
        doc.updateEditorContent(1, "# Two edited")

        // Any further change recompiles from the files; a stale file would lose the edit.
        doc.updateEditorContent(0, "# One edited")

        assertTrue(doc.markdown.contains("# Two edited"), "COR-2: the earlier edit must not be discarded")
        assertTrue(doc.markdown.contains("# One edited"))
    }

    @Test
    fun `deleting the last slide of a file drops the file`() {
        val doc = document("")
        doc.adopt(projectOf("# One", "# Two"))

        assertEquals(0, doc.delete(1))
        assertEquals(1, doc.project!!.slideFiles.size, "the emptied file leaves the deck")
        assertFalse(doc.markdown.contains("# Two"))
    }

    @Test
    fun `deleting the only remaining file is refused`() {
        val doc = document("")
        doc.adopt(projectOf("# Only"))
        assertNull(doc.delete(0), "a deck cannot be left with no files")
        assertEquals(1, doc.project!!.slideFiles.size)
    }

    @Test
    fun `deleting one of several slides inside a file edits that file`() {
        val doc = document("")
        doc.adopt(projectOf("# A\n\n---\n\n# B", "# C"))
        assertEquals(3, doc.slides.size)

        doc.delete(1)

        assertEquals(2, doc.project!!.slideFiles.size, "the file stays; only a slide went")
        assertFalse(doc.project!!.slideFiles[0].content.contains("# B"))
    }

    @Test
    fun `moving across files reorders the files when each holds one slide`() {
        val doc = document("")
        doc.adopt(projectOf("# A", "# B"))
        assertEquals(0, doc.move(1, 0))
        assertEquals("# B", doc.project!!.slideFiles[0].content)
    }

    @Test
    fun `moving across files is refused when a file holds several slides`() {
        val doc = document("")
        doc.adopt(projectOf("# A\n\n---\n\n# B", "# C"))
        assertNull(doc.move(0, 2), "reordering files is not well defined here")
    }

    // ---- PRF-5: the slide→file map is cached, so it must be invalidated ----
    //
    // `slideOwnerFileIndices()` reparses every file in the project, and `editorTextFor` reaches
    // it on every composition — 1.1 ms per call for a twenty-file project, several times a
    // frame. Caching it is worth ~1000x on that path and buys a stale-cache failure mode that
    // is exactly COR-3: the editor silently writing to the wrong file. These pin it down.

    @Test
    fun `adding a slide inside a file re-maps the files after it`() {
        val doc = document("")
        doc.adopt(projectOf("# A", "# B"))
        assertEquals("# B", doc.editorTextFor(1), "warms the map")

        // File 0 now holds two slides, so global slide 1 belongs to file 0, not file 1.
        doc.insert(0, "# A2")

        assertEquals(3, doc.slides.size)
        assertEquals(0, doc.fileFor(1)?.relativePath?.let { path -> doc.project!!.slideFiles.indexOfFirst { it.relativePath == path } })
        assertTrue(doc.editorTextFor(1).contains("# A2"), "the editor is showing a stale file")
    }

    @Test
    fun `deleting a slide re-maps the files after it`() {
        val doc = document("")
        doc.adopt(projectOf("# A\n\n---\n\n# B", "# C"))
        assertEquals("# C", doc.editorTextFor(2), "warms the map")

        doc.delete(0)

        assertEquals(2, doc.slides.size)
        assertEquals("# C", doc.editorTextFor(1), "slide 1 is now the second file, not the first")
    }

    @Test
    fun `adopting a different project discards the previous map`() {
        val doc = document("")
        doc.adopt(projectOf("# A\n\n---\n\n# B", "# C"))
        assertEquals("# C", doc.editorTextFor(2), "warms the map")

        doc.adopt(projectOf("# X", "# Y", "# Z"))

        assertEquals("# Z", doc.editorTextFor(2))
    }

    @Test
    fun `editing a file re-maps when the edit changes its slide count`() {
        val doc = document("")
        doc.adopt(projectOf("# A", "# B"))
        assertEquals("# B", doc.editorTextFor(1), "warms the map")

        // The first file grows a second slide by hand.
        doc.updateEditorContent(0, "# A\n\n---\n\n# A2")

        assertEquals(3, doc.slides.size)
        assertEquals("# B", doc.editorTextFor(2), "the third slide is still the second file")
        assertTrue(doc.editorTextFor(1).contains("# A2"))
    }

    // ---- editor text selection ----

    @Test
    fun `per-slide editing shows the owning file, full-deck editing shows everything`() {
        val doc = document("")
        doc.adopt(projectOf("# One", "# Two"))

        doc.perSlideEditing = true
        assertEquals("# Two", doc.editorTextFor(1))

        doc.perSlideEditing = false
        assertEquals(doc.markdown, doc.editorTextFor(1))
    }

    @Test
    fun `a flat deck always shows the whole document`() {
        val doc = document("# A\n\n---\n\n# B")
        doc.perSlideEditing = true
        assertEquals(doc.markdown, doc.editorTextFor(1))
    }

    @Test
    fun `loadFlat leaves project mode`() {
        val doc = document("")
        doc.adopt(projectOf("# One"))
        assertTrue(doc.isProjectMode)

        doc.loadFlat("# Plain")
        assertFalse(doc.isProjectMode)
        assertNull(doc.project)
        assertEquals("# Plain", doc.markdown)
    }
}
