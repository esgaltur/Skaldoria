package com.skaldoria.markdown.parser

import com.skaldoria.markdown.models.SlideElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ListRulesTest {
    @Test
    fun `recognizes shared bullet and ordered markers`() {
        val bullet = ListRules.listItem("  - Kotlin")
        val ordered = ListRules.listItem("12. Compose")

        assertEquals("Kotlin", bullet?.text)
        assertFalse(bullet?.isOrdered ?: true)
        assertEquals("Compose", ordered?.text)
        assertTrue(ordered?.isOrdered ?: false)
    }

    /**
     * The rule above always knew a list was numbered; the parser dropped it and built every list
     * unordered, so `1. 2. 3.` rendered as bullets in slides and canvas cards alike.
     */
    @Test
    fun `a numbered list stays numbered and a change of marker starts a new list`() {
        val lists = MarkdownSlideParser.parse("# T\n\n1. First\n2. Second\n- Loose\n- Ends")
            .single().elements.filterIsInstance<SlideElement.BulletList>()

        assertEquals(2, lists.size, "a bullet after a number is a second list: $lists")
        assertTrue(lists[0].isOrdered)
        assertEquals(listOf("First", "Second"), lists[0].items)
        assertFalse(lists[1].isOrdered)
        assertEquals(listOf("Loose", "Ends"), lists[1].items)
    }

    @Test
    fun `rejects marker-like prose`() {
        assertNull(ListRules.listItem("-not a list"))
        assertNull(ListRules.listItem("1) not supported"))
        assertNull(ListRules.listItem("ordinary prose"))
    }
}
