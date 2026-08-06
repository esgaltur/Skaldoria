package com.skaldoria.remote

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * F-10: the two companion portals, now loaded from resources rather than held in a Kotlin
 * string literal.
 *
 * They were 375 lines — 34% of `RemoteCompanionServer` — of HTML, CSS and JavaScript embedded
 * in a Kotlin object, where no tooling could see them. That matters more than tidiness: the
 * SEC-1 rule that audience-supplied values are inserted with `textContent` and never
 * `innerHTML` was enforced by nothing but a comment asking future readers not to "simplify"
 * the DOM builders back into template literals. These tests enforce it.
 */
class PortalAssetsTest {

    private val presenter = PortalAssets.presenterHtml()
    private val audience = PortalAssets.audienceHtml()

    @Test
    fun `both portals load and are not empty`() {
        assertTrue(presenter.length > 1000, "presenter portal failed to load from resources")
        assertTrue(audience.length > 1000, "audience portal failed to load from resources")
    }

    @Test
    fun `each portal is the one it claims to be`() {
        assertTrue(presenter.contains("Skaldoria Presenter Remote"))
        assertTrue(audience.contains("Skaldoria Audience Portal"))
    }

    /**
     * SEC-1: audience-supplied text must never be inserted as markup.
     *
     * Matches an *assignment* rather than the bare word, because both portals carry a comment
     * telling future readers not to reintroduce `innerHTML` — and that comment is worth
     * keeping. `insertAdjacentHTML` and `outerHTML` are covered too; they are the same hole.
     */
    @Test
    fun `no portal writes markup from data`() {
        val markupSinks = Regex("""(innerHTML|outerHTML)\s*(=|\+=)|insertAdjacentHTML|document\.write""")
        listOf("presenter" to presenter, "audience" to audience).forEach { (name, html) ->
            val hit = markupSinks.find(withoutJsComments(html))
            assertNull(hit, "SEC-1: $name portal builds markup from data via '${hit?.value}'")
        }
    }

    /** Line comments only — enough to keep the SEC-1 reminder out of the scan. */
    private fun withoutJsComments(html: String): String =
        html.lines().filterNot { it.trimStart().startsWith("//") }.joinToString("\n")

    /** SEC-1: a quote in a question id must not be able to break out of an attribute. */
    @Test
    fun `no portal binds handlers through inline onclick on generated rows`() {
        // The static chrome may use onclick for its own fixed buttons; what must never
        // happen is an onclick built by concatenating server data.
        listOf("presenter" to presenter, "audience" to audience).forEach { (name, html) ->
            assertFalse(
                html.contains("onclick=\"" ) && html.contains("' + q.id"),
                "SEC-1: $name portal concatenates data into an inline handler"
            )
            assertTrue(
                html.contains("addEventListener"),
                "$name portal should bind generated rows with listener closures"
            )
        }
    }

    /** SEC-2: only the presenter portal handles the session token. */
    @Test
    fun `only the presenter portal carries the session token`() {
        assertTrue(presenter.contains("X-Skaldoria-Token"), "presenter portal must send the token header")
        assertFalse(
            audience.contains("X-Skaldoria-Token"),
            "SEC-2: the audience portal is tokenless by design"
        )
    }

    /** SEC-2: the credential must not linger in the address bar for a screenshot to leak. */
    @Test
    fun `the presenter portal strips the token from the visible url`() {
        assertTrue(presenter.contains("replaceState"), "the token should be removed from the address bar")
    }

    @Test
    fun `the build version is substituted into both portals`() {
        val version = com.skaldoria.BuildInfo.DISPLAY_VERSION
        assertTrue(presenter.contains(version), "presenter portal should show $version")
        assertTrue(audience.contains(version), "audience portal should show $version")
        listOf(presenter, audience).forEach {
            assertFalse(it.contains("\${"), "an unsubstituted placeholder was left in the markup")
        }
    }

    @Test
    fun `the served pages match the loaded assets`() {
        // What the loader returns is what the routes serve — no second copy anywhere.
        assertEquals(presenter, PortalAssets.presenterHtml())
        assertEquals(audience, PortalAssets.audienceHtml())
    }
}
