package com.skaldoria.cv.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CvMarkdownAdapterTest {
    private val adapter = CvMarkdownAdapter()

    @Test
    fun `maps identity recognized sections custom sections entries and contacts`() {
        val source = """---
            |template: ats-safe
            |paper: A4
            |---
            |# Grace Hopper
            |
            |[Email](mailto:grace@example.com) · [Web](https://example.com/grace)
            |
            |## Experience
            |
            |### Rear Admiral
            |
            |- Led standards work
            |
            |## Community Leadership
            |
            |1. Mentored engineers
        """.trimMargin()

        val result = adapter.parse(source)

        assertEquals(source, result.sourceMarkdown)
        assertEquals("Grace Hopper", result.candidateName)
        assertEquals(mapOf("template" to "ats-safe", "paper" to "A4"), result.metadata)
        assertEquals(listOf(CvSectionKind.Experience, CvSectionKind.Custom), result.sections.map { it.kind })
        assertEquals("Rear Admiral", result.sections.first().entries.single().title)
        assertEquals(CvBlockKind.ListItem, result.sections.first().entries.single().content.single().kind)
        assertTrue(result.sections.last().introduction.single().isOrdered)
        assertEquals(listOf(ContactKind.Email, ContactKind.Web), result.contacts.map { it.kind })
        assertTrue(result.diagnostics.isEmpty())
    }

    @Test
    fun `reports actionable structural errors while preserving recoverable content`() {
        val source = """### Orphan entry
            |Useful detail
            |# First Person
            |# Second Person
            |#### Ambiguous detail
        """.trimMargin()

        val result = adapter.parse(source)

        assertEquals("First Person", result.candidateName)
        assertEquals("Unsectioned", result.sections.single().title)
        assertEquals("Useful detail", result.sections.single().entries.single().content.single().markdown)
        assertEquals(
            setOf("CV_ENTRY_WITHOUT_SECTION", "CV_MULTIPLE_IDENTITIES", "CV_AMBIGUOUS_HEADING"),
            result.diagnostics.map { it.code }.toSet()
        )
        assertTrue(result.diagnostics.all { it.action.isNotBlank() })
        assertTrue(result.hasErrors)
    }

    @Test
    fun `missing identity and malformed metadata point at their source lines`() {
        val result = adapter.parse("""---
            |template
            |---
            |## Skills
            |- Kotlin
        """.trimMargin())

        assertNull(result.candidateName)
        assertEquals(1, result.diagnostics.single { it.code == "CV_MISSING_IDENTITY" }.source.startLine)
        assertEquals(2, result.diagnostics.single { it.code == "CV_MALFORMED_METADATA" }.source.startLine)
    }

    @Test
    fun `headings inside fences do not alter CV structure`() {
        val result = adapter.parse("""# Linus Torvalds
            |```markdown
            |# Not another person
            |```
            |## Projects
        """.trimMargin())

        assertEquals("Linus Torvalds", result.candidateName)
        assertFalse(result.diagnostics.any { it.code == "CV_MULTIPLE_IDENTITIES" })
        assertEquals("Projects", result.sections.single().title)
        assertEquals("CV_UNSUPPORTED_FENCE", result.diagnostics.single().code)
    }

    @Test
    fun `structured contact header is extracted without duplicating preview content`() {
        val result = adapter.parse("""# Alex Morgan
            |[Email](mailto:alex@example.com) · [GitHub](https://github.example/alex)
            |Location: Prague, Czechia
            |
            |## Skills
            |- Kotlin
        """.trimMargin())

        assertTrue(result.headerContent.isEmpty())
        assertEquals(
            listOf(ContactKind.Email, ContactKind.Web, ContactKind.Location),
            result.contacts.map { it.kind }
        )
        assertEquals("Prague, Czechia", result.contacts.last().value)
    }

    @Test
    fun `professional headline is mapped from metadata`() {
        val result = adapter.parse("---\nheadline: Senior Software Engineer · Kotlin\n---\n# Alex Morgan")

        assertEquals("Senior Software Engineer · Kotlin", result.professionalHeadline)
    }
}
