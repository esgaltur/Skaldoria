package com.skaldoria.browser

import com.microsoft.playwright.Page
import com.microsoft.playwright.Response
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import com.microsoft.playwright.options.AriaRole
import com.skaldoria.BuildInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The speaker's remote (`/remote`), driven the way a paired phone drives it.
 *
 * `RemoteCompanionServerTest` proves the endpoints; these prove the page — that the buttons call
 * them with the token, that the state they return is rendered, and that what the audience typed
 * never becomes markup on the speaker's screen.
 */
class PresenterRemotePortalTest : CompanionPortalTestBase() {

    private val threeSlideDeck = """
        # Opening
        <!-- note: Smile and pause -->
        Welcome
        ---
        # Architecture
        <!-- note: Mention the ADR -->
        Deep dive
        ---
        # Questions
        Thank you
    """.trimIndent()

    private fun button(page: Page, name: String) =
        page.getByRole(AriaRole.BUTTON, Page.GetByRoleOptions().setName(name))

    @Test
    fun `pairing shows the current slide and its speaker notes`() {
        serve(presentationState(threeSlideDeck))

        page.navigate(presenterPairingUrl())

        assertThat(page.locator("#slide-index")).hasText("Slide 1 of 3")
        assertThat(page.locator("#slide-title")).hasText("Opening")
        assertThat(page.locator("#notes")).hasText("Smile and pause")
        assertThat(page.locator("#pairing-banner")).isHidden()
        assertTrue(pageErrors.isEmpty(), "the remote must load cleanly: $pageErrors")
    }

    @Test
    fun `SEC-2 the session token is stripped from the address bar`() {
        serve(presentationState(threeSlideDeck))

        page.navigate(presenterPairingUrl())

        assertThat(page.locator("#notes")).hasText("Smile and pause")
        assertEquals("$baseUrl/remote", page.url(), "a screenshot of the remote must not leak the credential")
    }

    @Test
    fun `NEXT and PREV drive the deck and the remote follows it`() {
        val state = serve(presentationState(threeSlideDeck))
        page.navigate(presenterPairingUrl())
        assertThat(page.locator("#slide-index")).hasText("Slide 1 of 3")

        button(page, "NEXT").click()
        assertThat(page.locator("#slide-index")).hasText("Slide 2 of 3")
        assertThat(page.locator("#pairing-banner")).isHidden()
        assertThat(page.locator("#slide-title")).hasText("Architecture")
        assertThat(page.locator("#notes")).hasText("Mention the ADR")
        assertEquals(1, state.currentSlideIndex)

        button(page, "PREV").click()
        assertThat(page.locator("#slide-index")).hasText("Slide 1 of 3")
        assertEquals(0, state.currentSlideIndex)
    }

    @Test
    fun `a slide changed on the laptop is reflected on the remote`() {
        val state = serve(presentationState(threeSlideDeck))
        page.navigate(presenterPairingUrl())
        assertThat(page.locator("#slide-index")).hasText("Slide 1 of 3")

        state.goToSlide(2)

        assertThat(page.locator("#slide-title")).hasText("Questions")
        assertThat(page.locator("#notes")).hasText("No notes for this slide.")
    }

    @Test
    fun `blackout and whiteout toggle and the buttons show which is active`() {
        val state = serve(presentationState(threeSlideDeck))
        page.navigate(presenterPairingUrl())

        page.locator("#btn-blackout").click()
        assertThat(page.locator("#btn-blackout")).hasClass("btn-action active")
        assertTrue(state.isBlackoutActive)

        page.locator("#btn-blackout").click()
        assertThat(page.locator("#btn-blackout")).hasClass("btn-action")
        assertFalse(state.isBlackoutActive)

        page.locator("#btn-whiteout").click()
        assertThat(page.locator("#btn-whiteout")).hasClass("btn-action active")
        assertTrue(state.isWhiteoutActive)
    }

    @Test
    fun `the timer button starts the talk clock`() {
        val state = serve(presentationState(threeSlideDeck))
        page.navigate(presenterPairingUrl())
        assertThat(page.locator("#timer")).hasText("00:00")

        button(page, "Timer").click()

        assertThat(page.locator("#timer")).not().hasText(
            "00:00",
            com.microsoft.playwright.assertions.LocatorAssertions.HasTextOptions().setTimeout(5_000.0)
        )
        assertTrue(state.isTimerRunning)
    }

    @Test
    fun `SEC-2 without the token the remote is inert and shows no notes`() {
        val state = serve(presentationState(threeSlideDeck))

        page.navigate("$baseUrl/remote")
        assertThat(page.locator("#slide-title")).hasText("Opening")
        assertThat(page.locator("#notes")).hasText("No notes for this slide.")

        val response: Response = page.waitForResponse({ it.url().contains("/api/action") }) {
            button(page, "NEXT").click()
        }

        assertEquals(401, response.status())
        assertEquals(0, state.currentSlideIndex, "an unpaired phone must not drive the deck")
        assertThat(page.locator("#pairing-banner")).isVisible()
    }

    @Test
    fun `audience questions appear in the Q&A tab and can be dismissed`() {
        val state = serve(presentationState(threeSlideDeck))
        state.audience.submit("Ada", "How does it scale?")
        page.navigate(presenterPairingUrl())

        assertThat(page.locator("#qa-count")).hasText("1")
        page.locator("#tab-qa-btn").click()
        assertThat(page.locator("#tab-qa")).isVisible()
        assertThat(page.locator("#tab-remote")).isHidden()
        assertThat(page.locator(".qa-text")).hasText("How does it scale?")
        assertThat(page.locator(".qa-author")).hasText("Ada")

        button(page, "Dismiss").click()

        assertThat(page.locator("#qa-count")).hasText("0")
        assertThat(page.locator("#qa-container")).hasText("No questions submitted yet.")
        assertTrue(state.audience.snapshot().isEmpty())
    }

    @Test
    fun `SEC-1 a hostile question renders as inert text on the speaker's screen`() {
        val payload = """<img src=x onerror="alert('xss')">"""
        val state = serve(presentationState(threeSlideDeck))
        state.audience.submit("""<b onmouseover="alert(1)">Mallory</b>""", payload)
        page.navigate(presenterPairingUrl())

        page.locator("#tab-qa-btn").click()

        assertThat(page.locator(".qa-text")).hasText(payload)
        assertEquals(0, page.locator("#qa-container img, #qa-container b").count(), "no audience markup may reach the DOM")
        page.locator(".qa-author").hover()
        assertTrue(dialogs.isEmpty(), "an injected handler ran: $dialogs")
    }

    @Test
    fun `the build version is substituted into the header`() {
        serve(presentationState(threeSlideDeck))

        page.navigate(presenterPairingUrl())

        assertThat(page.locator(".logo")).containsText(BuildInfo.DISPLAY_VERSION)
        assertFalse(page.content().contains("{{VERSION}}"))
    }
}
