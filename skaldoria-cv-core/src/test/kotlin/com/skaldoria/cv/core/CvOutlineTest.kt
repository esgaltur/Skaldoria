package com.skaldoria.cv.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** CV-FR-024: the outline names every section and entry, and knows where each one starts. */
class CvOutlineTest {

    private val markdown = """
        |# Ada Lovelace
        |
        |## Profile
        |
        |Analytical engine programmer.
        |
        |## Experience
        |
        |### Staff Engineer
        |
        |- Led the migration.
        |
        |### Senior Engineer
        |
        |- Shipped the parser.
        |
        |## Notes From The Margin
        |
        |Custom sections are preserved.
    """.trimMargin()

    private val outline = CvOutline.of(CvMarkdownAdapter().parse(markdown))

    @Test
    fun `sections and entries appear in source order with their lines`() {
        assertEquals(
            listOf(
                Triple("Profile", CvOutlineLevel.Section, 3),
                Triple("Experience", CvOutlineLevel.Section, 7),
                Triple("Staff Engineer", CvOutlineLevel.Entry, 9),
                Triple("Senior Engineer", CvOutlineLevel.Entry, 13),
                Triple("Notes From The Margin", CvOutlineLevel.Section, 17)
            ),
            outline.map { Triple(it.title, it.level, it.source.startLine) }
        )
    }

    @Test
    fun `sections keep the semantic kind the adapter recognised`() {
        assertEquals(CvSectionKind.Profile, outline.first { it.title == "Profile" }.kind)
        assertEquals(CvSectionKind.Experience, outline.first { it.title == "Experience" }.kind)
        assertEquals(
            CvSectionKind.Custom,
            outline.first { it.title == "Notes From The Margin" }.kind,
            "an unrecognised heading is still navigable, just custom"
        )
    }

    @Test
    fun `entries take their meaning from the section above them`() {
        assertNull(outline.first { it.title == "Staff Engineer" }.kind)
    }

    // ---------------------------------------------------------------------
    // Which row the caret is in
    // ---------------------------------------------------------------------

    @Test
    fun `the active row is the last one starting at or before the caret`() {
        assertEquals("Profile", CvOutline.activeAt(outline, 5)?.title)
        assertEquals("Experience", CvOutline.activeAt(outline, 7)?.title)
        assertEquals("Staff Engineer", CvOutline.activeAt(outline, 11)?.title)
        assertEquals("Senior Engineer", CvOutline.activeAt(outline, 15)?.title)
        assertEquals("Notes From The Margin", CvOutline.activeAt(outline, 19)?.title)
    }

    @Test
    fun `the header belongs to no row`() {
        // Claiming section one here would show the user a position they are not at.
        assertNull(CvOutline.activeAt(outline, 1))
        assertNull(CvOutline.activeAt(outline, 2))
    }

    @Test
    fun `a document without sections has an empty outline`() {
        assertEquals(emptyList(), CvOutline.of(CvMarkdownAdapter().parse("# Ada Lovelace\n")))
    }
}
