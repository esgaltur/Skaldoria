package com.skaldoria.cv.core

import kotlin.test.Test
import kotlin.test.assertEquals

class CvFrontMatterEditorTest {
    private val editor = CvFrontMatterEditor()

    @Test
    fun `replaces an existing key in place`() {
        val source = """---
            |template: software-engineer-ats
            |theme: ats-classic
            |font: roboto
            |---
            |# Ada Lovelace
        """.trimMargin()

        val result = editor.upsert(source, "theme", "modern-blue")

        val expected = """---
            |template: software-engineer-ats
            |theme: modern-blue
            |font: roboto
            |---
            |# Ada Lovelace
        """.trimMargin()
        assertEquals(expected, result)
    }

    @Test
    fun `inserts a missing key before the closing fence`() {
        val source = """---
            |template: software-engineer-ats
            |---
            |# Ada Lovelace
        """.trimMargin()

        val result = editor.upsert(source, "theme", "modern-blue")

        val expected = """---
            |template: software-engineer-ats
            |theme: modern-blue
            |---
            |# Ada Lovelace
        """.trimMargin()
        assertEquals(expected, result)
    }

    @Test
    fun `creates a front matter block when none exists`() {
        val source = """# Ada Lovelace
            |
            |## Profile
        """.trimMargin()

        val result = editor.upsert(source, "theme", "modern-blue")

        val expected = """---
            |theme: modern-blue
            |---
            |# Ada Lovelace
            |
            |## Profile
        """.trimMargin()
        assertEquals(expected, result)
    }

    @Test
    fun `matches keys case-insensitively`() {
        val source = """---
            |Theme: ats-classic
            |---
            |# Ada Lovelace
        """.trimMargin()

        val result = editor.upsert(source, "theme", "modern-blue")

        val expected = """---
            |theme: modern-blue
            |---
            |# Ada Lovelace
        """.trimMargin()
        assertEquals(expected, result)
    }

    @Test
    fun `preserves carriage-return newlines`() {
        val source = "---\r\ntemplate: software-engineer-ats\r\n---\r\n# Ada Lovelace"

        val result = editor.upsert(source, "theme", "modern-blue")

        assertEquals(
            "---\r\ntemplate: software-engineer-ats\r\ntheme: modern-blue\r\n---\r\n# Ada Lovelace",
            result
        )
    }

    @Test
    fun `re-parses to the upserted value`() {
        val source = """---
            |theme: ats-classic
            |---
            |# Ada Lovelace
        """.trimMargin()

        val result = editor.upsert(source, "theme", "modern-blue")

        assertEquals("modern-blue", CvMarkdownAdapter().parse(result).metadata["theme"])
    }
}
