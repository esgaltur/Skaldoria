package com.skaldoria.browser

import com.microsoft.playwright.Route
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import com.skaldoria.core.audience.Reaction
import com.skaldoria.state.PresentationState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Following the talk on a phone, taking the slides home, and telling the speaker how it is going.
 */
class AudienceFollowAlongTest : CompanionPortalTestBase() {

    private val talk = """
        # Intro
        Why we are here
        ---
        # Middle
        The interesting part
        <!-- note: do not leak this -->
        ---
        # Finale
        The punchline
    """.trimIndent()

    private fun PresentationState.presenterGoesTo(index: Int) = applyFromBackgroundThread { goToSlide(index) }

    private val slideTitle get() = page.frameLocator("#slide-iframe").locator("h1")

    // ---- follow along ----

    @Test
    fun `the phone shows the slide on screen and follows the speaker`() {
        val state = serve(presentationState(talk))
        page.navigate(audienceUrl())

        assertThat(slideTitle).hasText("Intro")
        assertThat(page.locator("#slide-label")).hasText("1 / 3 · live")

        state.presenterGoesTo(1)
        assertThat(slideTitle).hasText("Middle")
        assertThat(page.locator("#slide-label")).hasText("2 / 3 · live")
        assertTrue(pageErrors.isEmpty(), "the portal must run cleanly: $pageErrors")
    }

    @Test
    fun `a listener can look back at earlier slides and rejoin live`() {
        val state = serve(presentationState(talk))
        page.navigate(audienceUrl())
        assertThat(slideTitle).hasText("Intro")
        state.presenterGoesTo(2)
        assertThat(slideTitle).hasText("Finale")

        page.locator("#slide-prev").click()
        page.locator("#slide-prev").click()
        assertThat(slideTitle).hasText("Intro")
        assertThat(page.locator("#slide-label")).hasText("1 / 3")
        assertThat(page.locator("#slide-prev")).isDisabled()
        assertThat(page.locator("#slide-open")).hasAttribute("href", "/slide?index=0")

        page.locator("#slide-live").click()
        assertThat(slideTitle).hasText("Finale")
        assertThat(page.locator("#slide-next")).isDisabled()
    }

    @Test
    fun `a slide the speaker has not reached cannot be read from a phone`() {
        serve(presentationState(talk))
        page.navigate(audienceUrl())
        assertThat(slideTitle).hasText("Intro")

        assertThat(page.locator("#slide-next")).isDisabled()
        assertEquals(403, page.request().get("$baseUrl/slide?index=2").status(), "the punchline leaked")
    }

    @Test
    fun `followed slides never carry speaker notes`() {
        val state = serve(presentationState(talk))
        page.navigate(audienceUrl())
        state.presenterGoesTo(1)
        assertThat(slideTitle).hasText("Middle")

        val html = page.request().get("$baseUrl/slide?index=1").text()
        assertTrue("do not leak this" !in html, "speaker notes reached the audience")
        // The slide body, not just its title: the PNG renderer draws titles only and must
        // never become what phones see.
        assertTrue("The interesting part" in html, "the phone got a title-only slide")
    }

    // ---- handout ----

    @Test
    fun `the slides can be taken home once the speaker shares them`() {
        val state = serve(presentationState(talk))
        page.navigate(audienceUrl())
        assertThat(slideTitle).hasText("Intro")
        assertThat(page.locator("#handout-link")).isHidden()

        state.applyFromBackgroundThread { state.audience.share.toggleHandout() }

        assertThat(page.locator("#handout-link")).isVisible()
        val handout = page.request().get("$baseUrl/handout").text()
        listOf("Intro", "Middle", "Finale").forEach { assertTrue(it in handout, "$it missing from the handout") }
        assertTrue("do not leak this" !in handout)
    }

    // ---- reactions ----

    @Test
    fun `a reaction reaches the speaker's pulse`() {
        val state = serve(presentationState(talk))
        page.navigate(audienceUrl())

        page.locator(".react-btn[data-kind='lost']").click()

        assertThat(page.locator("#react-sent-msg")).isVisible()
        assertThat(page.locator(".react-btn[data-kind='lost']")).hasClass("react-btn chosen")
        assertEquals(1, state.audience.reactions.pulse().getValue(Reaction.Lost))
    }

    @Test
    fun `a refused reaction is not reported as sent`() {
        serve(presentationState(talk))
        page.route("**/api/react**") { it.fulfill(Route.FulfillOptions().setStatus(429).setBody("{}")) }
        page.navigate(audienceUrl())

        page.locator(".react-btn[data-kind='fast']").click()

        assertThat(page.locator("#react-error-msg")).isVisible()
        assertThat(page.locator("#react-sent-msg")).isHidden()
        assertThat(page.locator(".react-btn.chosen")).hasCount(0)
    }

    // ---- the speaker's remote ----

    @Test
    fun `the remote shares the slides and shows the room's pulse`() {
        val state = serve(presentationState(talk))
        repeat(3) { state.audience.reactions.react("phone-$it", Reaction.TooFast, slideIndex = 0) }
        page.navigate(presenterPairingUrl())

        assertThat(page.locator("#pulse-fast")).hasText("🐢 3")
        assertThat(page.locator("#pulse-fast")).hasClass("hot")

        page.locator("#btn-handout").click()
        assertThat(page.locator("#btn-handout")).hasText("📤 Slides shared")
        assertTrue(state.audience.share.handoutReleased)
    }
}
