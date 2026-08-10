package com.skaldoria.core.deck

import com.skaldoria.core.models.DeckProject
import com.skaldoria.core.models.SlideFileEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * COR-3: the slide-to-file mapping is derived, never positional.
 *
 * The assumption that slide *i* lives in file *i* held only while every file contained exactly
 * one slide. A single `---` inside any file shifts everything after it, and the editor then
 * silently writes to the wrong file — which is what these cases pin.
 */
class ProjectSlideMapTest {

    private fun project(vararg contents: String) = DeckProject(
        name = "Test",
        rootDir = "/tmp/deck",
        manifestPath = null,
        slideFiles = contents.mapIndexed { i, c ->
            SlideFileEntry("slides/0$i.md", "/tmp/deck/slides/0$i.md", c)
        }.toMutableList()
    )

    @Test
    fun `one slide per file maps straight through`() {
        val p = project("# A", "# B", "# C")
        assertEquals(0, ProjectSlideMap.ownerFileIndex(p, 0))
        assertEquals(1, ProjectSlideMap.ownerFileIndex(p, 1))
        assertEquals(2, ProjectSlideMap.ownerFileIndex(p, 2))
        assertTrue(ProjectSlideMap.isOneSlidePerFile(p))
    }

    /** The case the positional assumption got wrong. */
    @Test
    fun `a file holding two slides shifts every later mapping`() {
        val p = project("# A\n\n---\n\n# A2", "# B")

        assertEquals(0, ProjectSlideMap.ownerFileIndex(p, 0), "slide 0 is in file 0")
        assertEquals(0, ProjectSlideMap.ownerFileIndex(p, 1), "slide 1 is ALSO in file 0")
        assertEquals(1, ProjectSlideMap.ownerFileIndex(p, 2), "slide 2 is in file 1, not file 2")

        assertFalse(ProjectSlideMap.isOneSlidePerFile(p))
    }

    @Test
    fun `local index addresses a slide within its own file from zero`() {
        val p = project("# A\n\n---\n\n# A2", "# B")
        assertEquals(0, ProjectSlideMap.localSlideIndex(p, 0))
        assertEquals(1, ProjectSlideMap.localSlideIndex(p, 1), "second slide of file 0")
        assertEquals(0, ProjectSlideMap.localSlideIndex(p, 2), "first slide of file 1")
    }

    @Test
    fun `an out-of-range slide maps to nothing`() {
        val p = project("# A")
        assertNull(ProjectSlideMap.ownerFileIndex(p, 99))
        assertNull(ProjectSlideMap.localSlideIndex(p, 99))
    }

    @Test
    fun `a null project maps to nothing rather than throwing`() {
        assertNull(ProjectSlideMap.ownerFileIndex(null, 0))
        assertNull(ProjectSlideMap.localSlideIndex(null, 0))
        assertFalse(ProjectSlideMap.isOneSlidePerFile(null))
        assertEquals(0, ProjectSlideMap.slideCountInFile(null, 0))
    }

    @Test
    fun `slide count per file counts contributions, not files`() {
        val p = project("# A\n\n---\n\n# A2\n\n---\n\n# A3", "# B")
        assertEquals(3, ProjectSlideMap.slideCountInFile(p, 0))
        assertEquals(1, ProjectSlideMap.slideCountInFile(p, 1))
    }

    /** `deleteSlide` uses this to decide between editing a file and dropping it. */
    @Test
    fun `the last slide in its file is detectable`() {
        val p = project("# A\n\n---\n\n# A2", "# B")
        assertTrue(ProjectSlideMap.slideCountInFile(p, 1) <= 1, "file 1 holds its last slide")
        assertFalse(ProjectSlideMap.slideCountInFile(p, 0) <= 1, "file 0 still has another")
    }
}
