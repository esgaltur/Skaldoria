package com.skaldoria.remote

import com.skaldoria.core.audience.AudienceSession
import com.skaldoria.markdown.models.SlideElement
import java.net.HttpURLConnection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * F-08: the companion server runs against the [DeckControl] port alone.
 *
 * `start()` used to take the whole `PresentationState` — 132 members, including file dialogs,
 * project loading, theme unlocking and structural slide editing — to use about twenty. A
 * network worker thread could reach every one of them.
 *
 * That this file compiles *is* the assertion: nothing here constructs a `PresentationState`,
 * so the dependency is gone rather than merely narrowed by convention. The tests then check
 * that the port carries enough to actually serve the portals.
 */
class DeckControlPortTest {

    /** A deck that exists only in this test. */
    private class FakeDeck(
        override var currentSlideIndex: Int = 0,
        override val totalSlides: Int = 3,
        override val currentSlideTitle: String = "Fake Slide",
        override val currentSlideNotes: List<String> = listOf("secret speaker note"),
        override val currentSlidePoll: SlideElement.Poll? = SlideElement.Poll("Pick one", listOf("A", "B"))
    ) : DeckControl {
        override val elapsedSeconds = 42L
        override val isTimerRunning = true
        override var isBlackoutActive = false
        override var isWhiteoutActive = false
        override val audience = AudienceSession()

        var parked: Pair<String, String?>? = null
        var timerResets = 0

        override fun nextSlide() { currentSlideIndex++ }
        override fun previousSlide() { currentSlideIndex-- }
        override fun goToSlide(index: Int) { currentSlideIndex = index }
        override fun toggleBlackout() { isBlackoutActive = !isBlackoutActive }
        override fun toggleWhiteout() { isWhiteoutActive = !isWhiteoutActive }
        override fun toggleTimer() {}
        override fun resetTimer() { timerResets++ }
        override fun parkQuestion(question: String, author: String?) { parked = question to author }
        override fun <T> applyFromBackgroundThread(mutation: () -> T): T = mutation()
        override fun slideHtml(index: Int) = "<p>slide $index</p>"
        override fun handoutHtml() = "<p>every slide</p>"
    }

    private fun open(url: String, method: String = "GET", token: String? = null): HttpURLConnection =
        (java.net.URI.create(url).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 3000
            readTimeout = 3000
            if (token != null) setRequestProperty("X-Skaldoria-Token", token)
        }

    private fun HttpURLConnection.bodyText(): String {
        val stream = if (responseCode in 200..299) inputStream else errorStream
        return stream?.bufferedReader()?.use { it.readText() }.orEmpty()
    }

    private fun <T> withDeck(deck: DeckControl, block: (String) -> T): T {
        RemoteCompanionServer.start(deck, preferredPort = 18700)
        return try {
            block("http://127.0.0.1:${RemoteCompanionServer.currentPort}")
        } finally {
            RemoteCompanionServer.stop()
        }
    }

    @Test
    fun `the state feed is served entirely from the port`() {
        val deck = FakeDeck()
        withDeck(deck) { base ->
            val body = open("$base/api/state").bodyText()
            assertTrue(body.contains("\"title\": \"Fake Slide\""), body)
            assertTrue(body.contains("\"totalSlides\": 3"), body)
            assertTrue(body.contains("\"elapsedSeconds\": 42"), body)
            assertTrue(body.contains("\"hasPoll\": true"), body)
            assertTrue(body.contains("Pick one"), body)
        }
    }

    /** SEC-2: notes are presenter-only, and the rule holds through the port. */
    @Test
    fun `notes are withheld without a token and delivered with one`() {
        withDeck(FakeDeck()) { base ->
            assertFalse(
                open("$base/api/state").bodyText().contains("secret speaker note"),
                "SEC-2: notes leaked to an unauthenticated reader"
            )
            val token = RemoteCompanionServer.presenterUrl().substringAfter("?t=")
            assertTrue(open("$base/api/state", token = token).bodyText().contains("secret speaker note"))
        }
    }

    @Test
    fun `an authenticated action drives the port`() {
        val deck = FakeDeck()
        withDeck(deck) { base ->
            val token = RemoteCompanionServer.presenterUrl().substringAfter("?t=")
            assertEquals(200, open("$base/api/action?action=next", "POST", token).responseCode)
            assertEquals(1, deck.currentSlideIndex)

            open("$base/api/action?action=jump&index=2", "POST", token).responseCode
            assertEquals(2, deck.currentSlideIndex)

            open("$base/api/action?action=blackout", "POST", token).responseCode
            assertTrue(deck.isBlackoutActive)

            open("$base/api/action?action=resetTimer", "POST", token).responseCode
            assertEquals(1, deck.timerResets)
        }
    }

    @Test
    fun `audience contributions land in the session`() {
        val deck = FakeDeck()
        withDeck(deck) { base ->
            open("$base/api/qa/submit?author=Ada&text=How%20fast", "POST").responseCode
            assertEquals("How fast", deck.audience.questions.single().text)
            assertEquals("Ada", deck.audience.questions.single().author)

            open("$base/api/poll/vote?slideIndex=0&optionIndex=1", "POST").responseCode
            assertEquals(mapOf(1 to 1), deck.audience.votesForSlide(0))
        }
    }

    @Test
    fun `parking a question goes through the port, not the state class`() {
        val deck = FakeDeck()
        withDeck(deck) { base ->
            open("$base/api/parking-lot/add?question=Throughput%20per%20shard&author=Bo", "POST").responseCode
            assertEquals("Throughput per shard" to "Bo", deck.parked)
        }
    }

    @Test
    fun `a phone can read slides the room has seen but never the ones ahead`() {
        val deck = FakeDeck()
        withDeck(deck) { base ->
            assertEquals(403, open("$base/slide?index=0").responseCode, "nothing is shown before a phone has synced")

            open("$base/api/state").bodyText()
            deck.currentSlideIndex = 2
            open("$base/api/state").bodyText()

            assertEquals("<p>slide 1</p>", open("$base/slide?index=1").bodyText(), "slides already passed stay readable")
            assertEquals(200, open("$base/slide?index=2").responseCode)

            deck.currentSlideIndex = 0
            open("$base/api/state").bodyText()
            assertEquals(200, open("$base/slide?index=2").responseCode, "stepping back does not take slides away")

            assertEquals(404, open("$base/slide?index=9").responseCode)
            assertEquals(400, open("$base/slide?index=abc").responseCode)
        }
    }

    @Test
    fun `slides ahead of the speaker stay hidden even after a jump back`() {
        val deck = FakeDeck(totalSlides = 5)
        withDeck(deck) { base ->
            deck.currentSlideIndex = 1
            open("$base/api/state").bodyText()
            assertEquals(403, open("$base/slide?index=3").responseCode)
        }
    }

    @Test
    fun `the handout is withheld until the speaker shares it`() {
        val deck = FakeDeck()
        withDeck(deck) { base ->
            assertEquals(403, open("$base/handout").responseCode)
            assertTrue("\"handoutReleased\": false" in open("$base/api/state").bodyText())

            assertEquals(401, open("$base/api/action?action=toggleHandout", "POST").responseCode, "the room cannot release it")
            open("$base/api/action?action=toggleHandout", "POST", presenterToken()).responseCode

            assertEquals("<p>every slide</p>", open("$base/handout").bodyText())
            assertTrue("\"handoutReleased\": true" in open("$base/api/state").bodyText())
        }
    }

    @Test
    fun `reactions reach the pulse, one per device`() {
        val deck = FakeDeck()
        withDeck(deck) { base ->
            repeat(3) { open("$base/api/react?kind=lost", "POST").responseCode }
            assertEquals(400, open("$base/api/react?kind=bored", "POST").responseCode)

            val state = open("$base/api/state").bodyText()
            assertTrue("\"lost\": 1" in state, "three taps from one phone are one person: $state")
            assertEquals(3, deck.audience.reactions.history().size, "the report still sees every tap")
        }
    }

    @Test
    fun `a new session forgets what the last room saw`() {
        val deck = FakeDeck()
        withDeck(deck) { base ->
            open("$base/api/state").bodyText()
            open("$base/api/action?action=toggleHandout", "POST", presenterToken()).responseCode
        }
        withDeck(deck) { base ->
            assertEquals(403, open("$base/slide?index=0").responseCode)
            assertEquals(403, open("$base/handout").responseCode)
        }
    }

    @Test
    fun `the slide a phone is told about is always one it may read`() {
        // The speaker moves on mid-request: every read of the index after the first sees the
        // next slide. The response must not announce a slide it did not also reveal.
        val deck = object : DeckControl by FakeDeck() {
            private var reads = 0
            override val currentSlideIndex: Int get() = if (reads++ == 0) 0 else 1
        }
        withDeck(deck) { base ->
            val announced = Regex("\"currentSlideIndex\": (\\d+)")
                .find(open("$base/api/state").bodyText())!!.groupValues[1].toInt()

            assertEquals(200, open("$base/slide?index=$announced").responseCode)
        }
    }

    private fun presenterToken(): String =
        RemoteCompanionServer.presenterUrl().substringAfter("t=")
}
