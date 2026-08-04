package com.skaldoria.core.document

import com.skaldoria.core.parser.MarkdownSlideParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * COR-1 — structural slide edits. The defining property is that every edit agrees with
 * the parser about slide boundaries, for all three ways a deck can be split.
 */
class SlideDocumentTest {

    private val hrDeck = """
        ## Alpha
        content a

        ---

        ## Beta
        content b

        ---

        ## Gamma
        content c
    """.trimIndent()

    private val headingDeck = """
        ## Alpha
        content a
        ## Beta
        content b
        ## Gamma
        content c
    """.trimIndent()

    private val fourDashDeck = "## Alpha\ncontent a\n\n----\n\n## Beta\ncontent b\n\n----\n\n## Gamma\ncontent c"

    private fun titlesOf(markdown: String) = MarkdownSlideParser.parse(markdown).map { it.title }

    // -----------------------------------------------------------------
    // The core invariant, across every split style
    // -----------------------------------------------------------------

    @Test
    fun `all three split styles yield the same three slides`() {
        for ((label, deck) in listOf("hr" to hrDeck, "heading" to headingDeck, "fourDash" to fourDashDeck)) {
            assertEquals(listOf("Alpha", "Beta", "Gamma"), titlesOf(deck), "$label: parse")
            assertEquals(3, SlideDocument.of(deck).size, "$label: document size")
        }
    }

    @Test
    fun `delete removes exactly the target slide in every split style`() {
        for ((label, deck) in listOf("hr" to hrDeck, "heading" to headingDeck, "fourDash" to fourDashDeck)) {
            val result = SlideDocument.of(deck).delete(1)
            assertTrue(result != null, "$label: delete should succeed")
            assertEquals(listOf("Alpha", "Gamma"), titlesOf(result!!), "$label: delete(1)")
            assertTrue(result.contains("content a"), "$label: kept slide content survives")
            assertFalse(result.contains("content b"), "$label: deleted slide content is gone")
            assertTrue(result.contains("content c"), "$label: later slide content survives")
        }
    }

    @Test
    fun `move reorders in every split style`() {
        for ((label, deck) in listOf("hr" to hrDeck, "heading" to headingDeck, "fourDash" to fourDashDeck)) {
            val result = SlideDocument.of(deck).move(0, 2)
            assertEquals(listOf("Beta", "Gamma", "Alpha"), titlesOf(result!!), "$label: move(0,2)")
        }
    }

    @Test
    fun `duplicate inserts a copy directly after the source`() {
        val result = SlideDocument.of(hrDeck).duplicate(1)
        assertEquals(listOf("Alpha", "Beta", "Beta", "Gamma"), titlesOf(result!!))
    }

    @Test
    fun `insert places the template after the given index`() {
        val result = SlideDocument.of(hrDeck).insert(0, "## Inserted\nnew body")
        assertEquals(listOf("Alpha", "Inserted", "Beta", "Gamma"), titlesOf(result))
    }

    @Test
    fun `insert past the end appends`() {
        val result = SlideDocument.of(hrDeck).insert(99, "## Last\nnew body")
        assertEquals(listOf("Alpha", "Beta", "Gamma", "Last"), titlesOf(result))
    }

    @Test
    fun `replace swaps a single slide and leaves neighbours untouched`() {
        val result = SlideDocument.of(hrDeck).replace(1, "## Replaced\nfresh body")
        assertEquals(listOf("Alpha", "Replaced", "Gamma"), titlesOf(result!!))
        assertTrue(result.contains("content a"))
        assertTrue(result.contains("content c"))
        assertFalse(result.contains("content b"))
    }

    // -----------------------------------------------------------------
    // Guard rails
    // -----------------------------------------------------------------

    @Test
    fun `invalid operations return null rather than corrupting the deck`() {
        val doc = SlideDocument.of(hrDeck)

        assertNull(doc.delete(-1), "negative index")
        assertNull(doc.delete(99), "index past end")
        assertNull(doc.move(0, 99), "move target out of range")
        assertNull(doc.move(1, 1), "move to itself is a no-op")
        assertNull(doc.duplicate(99), "duplicate out of range")
        assertNull(doc.replace(99, "x"), "replace out of range")
    }

    @Test
    fun `the last slide cannot be deleted`() {
        val single = SlideDocument.of("## Only\nbody")
        assertEquals(1, single.size)
        assertNull(single.delete(0), "a deck must always keep at least one slide")
    }

    @Test
    fun `sourceOf returns the original text of a slide`() {
        val doc = SlideDocument.of(hrDeck)
        val second = doc.sourceOf(1)

        assertTrue(second!!.contains("## Beta"))
        assertTrue(second.contains("content b"))
        assertFalse(second.contains("Alpha"), "must not bleed into the previous slide")
        assertFalse(second.contains("Gamma"), "must not bleed into the next slide")
    }

    /** A fenced `---` inside a code block is content, not a slide boundary. */
    @Test
    fun `code fences containing rules do not split slides`() {
        val deck = """
            ## Config

            ```yaml
            ---
            key: value
            ---
            ```

            ---

            ## After
            body
        """.trimIndent()

        val doc = SlideDocument.of(deck)
        assertEquals(2, doc.size, "the rules inside the fence must not split")

        val result = doc.delete(1)
        assertEquals(listOf("Config"), titlesOf(result!!))
        assertTrue(result.contains("key: value"), "fenced content survives intact")
    }
}
