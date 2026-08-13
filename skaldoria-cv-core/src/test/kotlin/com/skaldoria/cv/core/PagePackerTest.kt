package com.skaldoria.cv.core

import kotlin.test.Test
import kotlin.test.assertEquals

class PagePackerTest {
    @Test
    fun `overflow starts another fixed page without changing order`() {
        val pages = PagePacker.pack(
            items = listOf(
                MeasuredPageItem("a", 40.0),
                MeasuredPageItem("b", 40.0),
                MeasuredPageItem("c", 40.0)
            ),
            pageExtent = 100.0
        )

        assertEquals(listOf(listOf("a", "b"), listOf("c")), pages)
    }

    @Test
    fun `heading moves with following content`() {
        val pages = PagePacker.pack(
            items = listOf(
                MeasuredPageItem("body", 75.0),
                MeasuredPageItem("heading", 15.0, keepWithNext = true),
                MeasuredPageItem("next", 20.0)
            ),
            pageExtent = 100.0
        )

        assertEquals(listOf(listOf("body"), listOf("heading", "next")), pages)
    }

    @Test
    fun `short semantic group moves intact instead of orphaning its final item`() {
        val pages = PagePacker.pack(
            items = listOf(
                MeasuredPageItem("previous", 75.0),
                MeasuredPageItem("Languages", 10.0, keepTogetherGroup = "languages"),
                MeasuredPageItem("English", 10.0, keepTogetherGroup = "languages"),
                MeasuredPageItem("Czech", 10.0, keepTogetherGroup = "languages")
            ),
            pageExtent = 100.0
        )

        assertEquals(
            listOf(listOf("previous"), listOf("Languages", "English", "Czech")),
            pages
        )
    }

    @Test
    fun `group larger than a page may split normally`() {
        val pages = PagePacker.pack(
            items = listOf(
                MeasuredPageItem("before", 40.0),
                MeasuredPageItem("one", 60.0, keepTogetherGroup = "large"),
                MeasuredPageItem("two", 60.0, keepTogetherGroup = "large")
            ),
            pageExtent = 100.0
        )

        assertEquals(listOf(listOf("before", "one"), listOf("two")), pages)
    }
}
