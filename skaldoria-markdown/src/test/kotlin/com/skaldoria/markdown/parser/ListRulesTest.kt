package com.skaldoria.markdown.parser

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

    @Test
    fun `rejects marker-like prose`() {
        assertNull(ListRules.listItem("-not a list"))
        assertNull(ListRules.listItem("1) not supported"))
        assertNull(ListRules.listItem("ordinary prose"))
    }
}
