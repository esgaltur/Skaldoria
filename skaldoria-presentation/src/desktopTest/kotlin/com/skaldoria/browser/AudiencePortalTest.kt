package com.skaldoria.browser

import com.microsoft.playwright.Page
import com.microsoft.playwright.Route
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import com.microsoft.playwright.options.AriaRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The audience portal (`/audience`), driven the way a phone in the room drives it.
 */
class AudiencePortalTest : CompanionPortalTestBase() {

    private val pollDeck = """
        # Favourite language?
        <!-- poll: Kotlin | Java | Go -->
        <!-- note: Reveal results after a minute -->
        ---
        # Results
        Thanks for voting
    """.trimIndent()

    private fun Page.submitButton() =
        getByRole(AriaRole.BUTTON, Page.GetByRoleOptions().setName("Submit Question"))

    /** Waits for one full polling round trip, so "nothing happened" is an observation, not a guess. */
    private fun Page.awaitNextStatePoll() {
        waitForResponse({ it.url().endsWith("/api/state") }) {}
    }

    // ---- questions ----

    @Test
    fun `a submitted question appears in the feed and the box clears`() {
        val state = serve(presentationState(pollDeck))
        page.navigate(audienceUrl())

        page.locator("#qa-author-input").fill("Grace")
        page.locator("#qa-text-input").fill("Is the deck open source?")
        page.submitButton().click()

        assertThat(page.locator(".qa-card .qa-text-body")).hasText("Is the deck open source?")
        assertThat(page.locator(".qa-card .qa-author-label")).hasText("Grace")
        assertThat(page.locator("#qa-text-input")).hasValue("")
        assertThat(page.locator("#qa-error-msg")).isHidden()
        val question = state.audience.snapshot().single()
        assertEquals("Grace", question.author)
        assertTrue(pageErrors.isEmpty(), "the portal must run cleanly: $pageErrors")
    }

    @Test
    fun `a question without a name is credited to Anonymous`() {
        val state = serve(presentationState(pollDeck))
        page.navigate(audienceUrl())

        page.locator("#qa-text-input").fill("What about accessibility?")
        page.submitButton().click()

        assertThat(page.locator(".qa-card .qa-author-label")).hasText("Anonymous")
        assertEquals("Anonymous", state.audience.snapshot().single().author)
    }

    @Test
    fun `a blank question is not sent`() {
        val state = serve(presentationState(pollDeck))
        val submits = mutableListOf<String>()
        page.onRequest { if (it.url().contains("/api/qa/submit")) submits += it.url() }
        page.navigate(audienceUrl())

        page.locator("#qa-text-input").fill("   \n  ")
        page.submitButton().click()
        page.awaitNextStatePoll()

        assertTrue(submits.isEmpty(), "whitespace must not be submitted: $submits")
        assertTrue(state.audience.snapshot().isEmpty())
    }

    @Test
    fun `a rejected question is kept in the box so it can be resent`() {
        // SEC-5 rate limiting answers 429. The portal clears the textarea on *any* response,
        // so a throttled question is silently thrown away after the speaker's audience typed it.
        serve(presentationState(pollDeck))
        page.route("**/api/qa/submit**") { route: Route ->
            route.fulfill(Route.FulfillOptions().setStatus(429).setBody("""{"status":"error","message":"Too many requests"}"""))
        }
        page.navigate(audienceUrl())

        page.locator("#qa-text-input").fill("Will this be lost?")
        page.waitForResponse({ it.url().contains("/api/qa/submit") }) { page.submitButton().click() }
        page.awaitNextStatePoll()

        assertThat(page.locator("#qa-text-input")).hasValue("Will this be lost?")
        assertThat(page.locator("#qa-error-msg")).isVisible()
    }

    @Test
    fun `upvoting raises the count on the phone and in the deck`() {
        val state = serve(presentationState(pollDeck))
        state.audience.submit("Ada", "How does it scale?")
        page.navigate(audienceUrl())
        assertThat(page.locator(".upvote-btn")).hasText("👍 0")

        page.locator(".upvote-btn").click()

        assertThat(page.locator(".upvote-btn")).hasText("👍 1")
        assertEquals(1, state.audience.snapshot().single().upvotes)
    }

    @Test
    fun `one device cannot stuff upvotes on a question`() {
        // SEC-5 made poll ballots one-per-device precisely because a counter can be stuffed;
        // upvotes are still a bare counter, so a single phone can push any question to the top.
        val state = serve(presentationState(pollDeck))
        state.audience.submit("Ada", "How does it scale?")
        page.navigate(audienceUrl())
        assertThat(page.locator(".upvote-btn")).hasText("👍 0")

        repeat(3) {
            page.waitForResponse({ it.url().contains("/api/qa/upvote") }) { page.locator(".upvote-btn").click() }
        }
        page.awaitNextStatePoll()

        assertEquals(1, state.audience.snapshot().single().upvotes, "one device, one upvote")
    }

    @Test
    fun `SEC-1 a hostile question is shown as text to the rest of the room`() {
        val payload = """<script>alert('xss')</script><img src=x onerror="alert(2)">"""
        serve(presentationState(pollDeck))
        page.navigate(audienceUrl())

        page.locator("#qa-author-input").fill("""<i>Mallory</i>""")
        page.locator("#qa-text-input").fill(payload)
        page.submitButton().click()

        assertThat(page.locator(".qa-text-body")).hasText(payload)
        assertThat(page.locator(".qa-author-label")).hasText("<i>Mallory</i>")
        assertEquals(0, page.locator("#qa-feed script, #qa-feed img, #qa-feed i").count())
        assertTrue(dialogs.isEmpty(), "an injected script ran: $dialogs")
    }

    // ---- polls ----

    @Test
    fun `a poll on the current slide is shown and can be voted on`() {
        val state = serve(presentationState(pollDeck))
        page.navigate(audienceUrl())

        assertThat(page.locator("#poll-section")).isVisible()
        assertThat(page.locator("#poll-question")).hasText("Favourite language?")
        assertThat(page.locator(".poll-opt-btn")).hasCount(3)

        page.locator(".poll-opt-btn", Page.LocatorOptions().setHasText("Kotlin")).click()

        assertThat(page.locator("#poll-voted-msg")).isVisible()
        val kotlin = page.locator(".poll-opt-btn", Page.LocatorOptions().setHasText("Kotlin"))
        assertThat(kotlin).hasClass("poll-opt-btn voted")
        assertThat(kotlin).containsText("1 votes")
        assertEquals(mapOf(0 to 1), state.audience.votesForSlide(0))
    }

    @Test
    fun `changing a vote moves the ballot instead of adding one`() {
        val state = serve(presentationState(pollDeck))
        page.navigate(audienceUrl())
        assertThat(page.locator(".poll-opt-btn")).hasCount(3)

        page.locator(".poll-opt-btn", Page.LocatorOptions().setHasText("Kotlin")).click()
        assertThat(page.locator(".poll-opt-btn", Page.LocatorOptions().setHasText("Kotlin"))).containsText("1 votes")
        page.locator(".poll-opt-btn", Page.LocatorOptions().setHasText("Java")).click()

        assertThat(page.locator(".poll-opt-btn", Page.LocatorOptions().setHasText("Java"))).containsText("1 votes")
        assertThat(page.locator(".poll-opt-btn", Page.LocatorOptions().setHasText("Kotlin"))).containsText("0 votes")
        assertEquals(mapOf(1 to 1), state.audience.votesForSlide(0))
    }

    @Test
    fun `a rejected vote is not reported as recorded`() {
        // The portal shows "Your vote was recorded!" without looking at the response, so a
        // throttled (429) or refused vote is confirmed to the voter anyway.
        val state = serve(presentationState(pollDeck))
        page.route("**/api/poll/vote**") { route: Route ->
            route.fulfill(Route.FulfillOptions().setStatus(429).setBody("""{"status":"error","message":"Too many requests"}"""))
        }
        page.navigate(audienceUrl())
        assertThat(page.locator(".poll-opt-btn")).hasCount(3)

        page.waitForResponse({ it.url().contains("/api/poll/vote") }) {
            page.locator(".poll-opt-btn", Page.LocatorOptions().setHasText("Go")).click()
        }
        page.awaitNextStatePoll()

        assertThat(page.locator("#poll-voted-msg")).isHidden()
        assertTrue(state.audience.votesForSlide(0).isEmpty())
        assertThat(page.locator("#poll-error-msg")).isVisible()
    }

    @Test
    fun `a vote for an option the poll does not have is refused`() {
        val state = serve(presentationState(pollDeck))
        page.navigate(audienceUrl())

        val response = page.request().post("$baseUrl/api/poll/vote?slideIndex=0&optionIndex=99")

        assertEquals(400, response.status(), "option 99 does not exist on a three-option poll")
        assertTrue(state.audience.votesForSlide(0).isEmpty(), "a phantom ballot was recorded")
    }

    @Test
    fun `a vote for a slide without a poll is refused`() {
        val state = serve(presentationState(pollDeck))
        page.navigate(audienceUrl())

        val response = page.request().post("$baseUrl/api/poll/vote?slideIndex=1&optionIndex=0")

        assertEquals(400, response.status(), "slide 2 carries no poll")
        assertTrue(state.audience.votesForSlide(1).isEmpty(), "a ballot was recorded for a slide with no poll")
    }

    @Test
    fun `the poll disappears when the deck moves past it`() {
        val state = serve(presentationState(pollDeck))
        page.navigate(audienceUrl())
        assertThat(page.locator("#poll-section")).isVisible()

        state.goToSlide(1)

        assertThat(page.locator("#poll-section")).isHidden()
    }

    // ---- separation from the presenter ----

    @Test
    fun `SEC-2 the audience never receives speaker notes nor sends a token`() {
        serve(presentationState(pollDeck))
        val tokenHeaders = mutableListOf<String>()
        page.onRequest { req -> req.headers()["x-skaldoria-token"]?.let { tokenHeaders += it } }

        val stateResponse = page.waitForResponse({ it.url().endsWith("/api/state") }) { page.navigate(audienceUrl()) }

        assertFalse(stateResponse.text().contains("Reveal results"), "notes leaked to an audience device")
        assertFalse(page.content().contains("Reveal results"))
        assertTrue(tokenHeaders.isEmpty(), "the audience portal must not know about the presenter token")
    }

    @Test
    fun `a question asked from the room reaches the speaker's remote and dismissal clears it`() {
        serve(presentationState(pollDeck))
        val remote = openSecondDevice()
        remote.navigate(presenterPairingUrl())
        page.navigate(audienceUrl())

        page.locator("#qa-text-input").fill("Can we get the slides?")
        page.submitButton().click()

        assertThat(remote.locator("#qa-count")).hasText("1")
        remote.locator("#tab-qa-btn").click()
        assertThat(remote.locator(".qa-text")).hasText("Can we get the slides?")

        remote.getByRole(AriaRole.BUTTON, Page.GetByRoleOptions().setName("Dismiss")).click()

        assertThat(page.locator("#qa-feed")).hasText("No questions submitted yet. Be the first!")
    }
}
