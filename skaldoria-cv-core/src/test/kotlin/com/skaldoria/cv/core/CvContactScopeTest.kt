package com.skaldoria.cv.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * CV-FR-005: contacts are the ways to reach the candidate, and only those.
 *
 * The adapter used to harvest a contact from every line in the document, so a repository link in an
 * Experience bullet or a publication URL appeared in the header strip next to the phone number.
 * Body links keep working — the layout engine turns them into link runs through `InlineRuns` — they
 * simply are not contacts.
 */
class CvContactScopeTest {

    private fun parse(markdown: String) = CvMarkdownAdapter().parse(markdown)

    private val withBodyLinks = """
        |# Ada Lovelace
        |
        |[ada@example.com](mailto:ada@example.com) · [+420 777 000 111](tel:+420777000111)
        |Location: Prague, Czechia
        |
        |## Experience
        |
        |### Engineer
        |
        |- Shipped the analytical engine, see https://github.com/example/engine for details.
        |- Reachable for that project at engine-team@example.com.
        |
        |## Projects
        |
        |- [Difference Engine](https://github.com/example/difference) — a second link.
    """.trimMargin()

    @Test
    fun `only the header contributes contacts`() {
        val contacts = parse(withBodyLinks).contacts

        assertEquals(
            listOf(ContactKind.Email, ContactKind.Telephone, ContactKind.Location),
            contacts.map { it.kind },
            "body links leaked into the contact strip: ${contacts.map { it.value }}"
        )
    }

    @Test
    fun `body links stay in the body`() {
        val document = parse(withBodyLinks)
        val bullets = document.sections.flatMap { it.entries.flatMap { entry -> entry.content } + it.introduction }

        assertTrue(
            bullets.any { "github.com/example/engine" in it.markdown },
            "the link was removed from the text as well as from the contacts"
        )
        assertTrue(
            bullets.any { "difference" in it.markdown },
            "the markdown link in a project bullet was lost"
        )
    }

    @Test
    fun `the header still collects every way to reach the candidate`() {
        val contacts = parse(
            """
            |# Ada Lovelace
            |
            |[ada@example.com](mailto:ada@example.com) · [LinkedIn](https://example.com/ada)
            |[GitHub](https://github.com/ada)
            |Location: Prague, Czechia
            |
            |## Profile
            |
            |Text.
            """.trimMargin()
        ).contacts

        assertEquals(4, contacts.size, "got: ${contacts.map { "${it.kind}=${it.value}" }}")
        assertTrue(contacts.any { it.kind == ContactKind.Email })
        assertTrue(contacts.count { it.kind == ContactKind.Web } == 2)
        assertTrue(contacts.any { it.kind == ContactKind.Location })
    }

    @Test
    fun `a contact line before the name is not mistaken for the header`() {
        // No candidate heading yet, so there is no header to belong to.
        val contacts = parse("[ada@example.com](mailto:ada@example.com)\n\n# Ada Lovelace\n").contacts

        assertTrue(contacts.isEmpty(), "got: ${contacts.map { it.value }}")
    }
}
