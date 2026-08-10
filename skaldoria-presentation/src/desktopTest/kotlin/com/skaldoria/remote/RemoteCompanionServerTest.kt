package com.skaldoria.remote

import com.skaldoria.PresentationStateTestBase
import com.skaldoria.state.PresentationState
import java.net.HttpURLConnection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemoteCompanionServerTest : PresentationStateTestBase() {

    private fun open(url: String, method: String = "GET", token: String? = null): HttpURLConnection =
        (java.net.URI.create(url).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 3000
            readTimeout = 3000
            if (token != null) setRequestProperty("X-Skaldoria-Token", token)
        }

    /** Extracts the session token the pairing URL hands to the speaker's device. */
    private fun sessionToken(): String =
        RemoteCompanionServer.presenterUrl().substringAfter("?t=")

    private fun HttpURLConnection.bodyText(): String {
        val stream = if (responseCode in 200..299) inputStream else errorStream
        return stream?.bufferedReader()?.use { it.readText() }.orEmpty()
    }

    private fun <T> withServer(state: PresentationState, block: (String) -> T): T {
        RemoteCompanionServer.start(state, preferredPort = 18888)
        return try {
            block("http://127.0.0.1:${RemoteCompanionServer.currentPort}")
        } finally {
            RemoteCompanionServer.stop()
        }
    }

    @Test
    fun testServerLifecycleAndApiEndpoints() {
        val state = presentationState()
        state.updateMarkdown(
            """
            # Slide 1
            <!-- note: Welcome Note -->
            Welcome to the presentation
            ---
            # Slide 2
            <!-- note: Deep Dive Note -->
            Technical architecture deep dive
            """.trimIndent()
        )

        val urlStr = RemoteCompanionServer.start(state, preferredPort = 18888)
        assertTrue(RemoteCompanionServer.isRunning(), "Server should be running after start()")
        assertTrue(urlStr.startsWith("http://"), "URL should be formatted with http scheme: $urlStr")

        val baseUrl = "http://127.0.0.1:${RemoteCompanionServer.currentPort}"

        try {
            // 1. GET /api/state — reads stay on GET.
            val stateConn = open("$baseUrl/api/state")
            assertEquals(200, stateConn.responseCode)
            val stateBody = stateConn.bodyText()
            assertTrue(stateBody.contains("\"currentSlideIndex\": 0"))
            assertTrue(stateBody.contains("\"totalSlides\": 2"))
            assertTrue(stateBody.contains("Slide 1"))

            // 2. POST /api/action — SEC-3 requires POST, SEC-2 requires the session token.
            val actionConn = open("$baseUrl/api/action?action=next", "POST", sessionToken())
            assertEquals(200, actionConn.responseCode)
            assertEquals(1, state.currentSlideIndex, "authenticated POST 'next' should advance the deck")

            // 3. GET /
            val webConn = open("$baseUrl/")
            assertEquals(200, webConn.responseCode)
            assertTrue(webConn.bodyText().contains("Skaldoria Presenter Remote"))

            // 4. GET /audience
            val audienceConn = open("$baseUrl/audience")
            assertEquals(200, audienceConn.responseCode)
            assertTrue(audienceConn.bodyText().contains("Skaldoria Audience Portal"))
        } finally {
            RemoteCompanionServer.stop()
            assertFalse(RemoteCompanionServer.isRunning(), "Server should be stopped")
        }
    }

    /**
     * SEC-3 — a cross-origin page must not be able to drive the deck with a bare
     * `<img src="...">`, which can only issue a GET.
     */
    @Test
    fun `SEC-3 GET on a write endpoint is rejected and does not mutate state`() {
        val state = presentationState()
        state.updateMarkdown("# One\n\n---\n\n# Two\n\n---\n\n# Three")

        withServer(state) { baseUrl ->
            val conn = open("$baseUrl/api/action?action=next")
            assertEquals(405, conn.responseCode, "GET on /api/action must be rejected")
            assertEquals(0, state.currentSlideIndex, "rejected GET must not advance the deck")

            for (path in listOf("/api/poll/vote", "/api/qa/submit", "/api/qa/upvote", "/api/qa/dismiss", "/api/parking-lot/add")) {
                assertEquals(405, open("$baseUrl$path").responseCode, "GET on $path must be rejected")
            }

            // Reads are unaffected.
            assertEquals(200, open("$baseUrl/api/state").responseCode)
        }
    }

    /**
     * SEC-3 — the wildcard CORS header let any site the presenter visited read from
     * and drive the server. Nothing may emit `Access-Control-Allow-*`.
     */
    @Test
    fun `SEC-3 no CORS headers are emitted`() {
        val state = presentationState()

        withServer(state) { baseUrl ->
            for (path in listOf("/", "/audience", "/api/state")) {
                val conn = open("$baseUrl$path")
                conn.responseCode
                assertNull(
                    conn.getHeaderField("Access-Control-Allow-Origin"),
                    "$path must not send Access-Control-Allow-Origin"
                )
                assertNull(
                    conn.getHeaderField("Access-Control-Allow-Methods"),
                    "$path must not send Access-Control-Allow-Methods"
                )
            }
        }
    }

    /**
     * SEC-2 — presenter-scope routes must reject anyone without the session token, so a
     * bystander on the conference wifi cannot drive the deck.
     */
    @Test
    fun `SEC-2 presenter endpoints reject requests without the session token`() {
        val state = presentationState()
        state.updateMarkdown("# One\n\n---\n\n# Two\n\n---\n\n# Three")

        withServer(state) { baseUrl ->
            // No token at all.
            assertEquals(401, open("$baseUrl/api/action?action=next", "POST").responseCode)
            assertEquals(0, state.currentSlideIndex, "unauthenticated POST must not advance the deck")

            // Wrong token.
            assertEquals(401, open("$baseUrl/api/action?action=next", "POST", "not-the-token").responseCode)
            assertEquals(0, state.currentSlideIndex, "bad token must not advance the deck")

            assertEquals(401, open("$baseUrl/api/qa/dismiss?id=x", "POST").responseCode)

            // Correct token works.
            assertEquals(200, open("$baseUrl/api/action?action=next", "POST", sessionToken()).responseCode)
            assertEquals(1, state.currentSlideIndex)
        }
    }

    /**
     * SEC-2 — the audience shares the network and hits the same /api/state endpoint.
     * Speaker notes must not be readable without the presenter token.
     */
    @Test
    fun `SEC-2 speaker notes are withheld from unauthenticated clients`() {
        val state = presentationState()
        state.updateMarkdown(
            """
            # Only Slide
            <!-- note: CONFIDENTIAL rehearsal reminder -->
            Body text
            """.trimIndent()
        )

        withServer(state) { baseUrl ->
            val anonymous = open("$baseUrl/api/state").bodyText()
            assertFalse(
                anonymous.contains("CONFIDENTIAL"),
                "speaker notes leaked to an unauthenticated client"
            )
            assertTrue(anonymous.contains("\"notes\": []"), "notes should be present but empty")

            val presenter = open("$baseUrl/api/state", "GET", sessionToken()).bodyText()
            assertTrue(presenter.contains("CONFIDENTIAL"), "presenter should still receive notes")
        }
    }

    /** SEC-2 — a fresh token per session; stopping the server invalidates it. */
    @Test
    fun `SEC-2 session token is regenerated on each start`() {
        val state = presentationState()

        RemoteCompanionServer.start(state, preferredPort = 18888)
        val first = sessionToken()
        RemoteCompanionServer.stop()

        RemoteCompanionServer.start(state, preferredPort = 18888)
        val second = sessionToken()
        RemoteCompanionServer.stop()

        assertTrue(first.length >= 20, "token should carry at least 128 bits of entropy: $first")
        assertNotEquals(first, second, "a new session must not reuse the previous token")
    }

    /** SEC-2 — the audience pairing link must never carry the presenter credential. */
    @Test
    fun `SEC-2 audience url carries no token`() {
        val state = presentationState()

        withServer(state) { _ ->
            val token = sessionToken()
            val audience = RemoteCompanionServer.audienceUrl()
            assertFalse(audience.contains(token), "audience URL must not embed the session token")
            assertFalse(audience.contains("t="), "audience URL must not carry a token parameter")
            assertTrue(RemoteCompanionServer.presenterUrl().contains(token), "presenter URL should carry it")
        }
    }

    /**
     * EXP-4 — `Content-Length` counts bytes, but the body was read as that many *chars*
     * through a decoding reader, so any multi-byte body never satisfied the read loop and
     * stalled until the 10s socket timeout. ASCII-only tests could never catch it.
     */
    @Test
    fun `EXP-4 multi-byte request body is read without stalling`() {
        val state = presentationState()
        val question = "Что насчёт производительности? 🚀 — Grüße"

        withServer(state) { baseUrl ->
            val payload = "author=Аноним&text=" + java.net.URLEncoder.encode(question, "UTF-8")
            val bytes = payload.toByteArray(Charsets.UTF_8)

            val started = System.currentTimeMillis()
            val conn = open("$baseUrl/api/qa/submit", "POST").apply {
                doOutput = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                setRequestProperty("Content-Length", bytes.size.toString())
            }
            conn.outputStream.use { it.write(bytes) }
            assertEquals(200, conn.responseCode)
            val elapsed = System.currentTimeMillis() - started

            assertTrue(elapsed < 3000, "multi-byte body should not stall on the socket timeout (took ${elapsed}ms)")
            assertEquals(1, state.audienceQuestions.size, "the question should have been recorded")
            assertEquals(question, state.audienceQuestions.first().text, "text must survive UTF-8 round trip")
        }
    }

    /** SEC-7 — the parser only frames by Content-Length; chunked must be refused, not misread. */
    @Test
    fun `SEC-7 chunked transfer-encoding is rejected`() {
        val state = presentationState()

        withServer(state) { baseUrl ->
            java.net.Socket("127.0.0.1", RemoteCompanionServer.currentPort).use { socket ->
                socket.getOutputStream().write(
                    (
                        "POST /api/qa/submit?text=hi HTTP/1.1\r\n" +
                            "Host: 127.0.0.1\r\n" +
                            "Transfer-Encoding: chunked\r\n" +
                            "\r\n" +
                            "5\r\nhello\r\n0\r\n\r\n"
                        ).toByteArray(Charsets.UTF_8)
                )
                socket.getOutputStream().flush()

                val response = socket.getInputStream().bufferedReader().readText()
                assertTrue(response.startsWith("HTTP/1.1 411"), "expected 411, got: ${response.lineSequence().first()}")
            }
            assertEquals(0, state.audienceQuestions.size, "no question should have been recorded")
        }
    }

    /**
     * SEC-1 — audience-submitted text reaches the presenter remote and every other
     * audience device. It must never be interpolated into markup.
     */
    @Test
    fun `SEC-1 audience input is JSON-escaped and never rendered as markup`() {
        val state = presentationState()
        val payload = """<img src=x onerror="alert(1)">"""
        val author = """"><script>alert(2)</script>"""

        withServer(state) { baseUrl ->
            val submit = open(
                "$baseUrl/api/qa/submit?author=${java.net.URLEncoder.encode(author, "UTF-8")}" +
                    "&text=${java.net.URLEncoder.encode(payload, "UTF-8")}",
                "POST"
            )
            assertEquals(200, submit.responseCode)

            val body = open("$baseUrl/api/state").bodyText()

            // The quotes inside the payload must be backslash-escaped, so the payload
            // cannot terminate its JSON string and inject structure.
            assertFalse(
                body.contains("""onerror="alert(1)""""),
                "raw unescaped quotes leaked into the JSON payload"
            )
            assertTrue(
                body.contains("""onerror=\"alert(1)\""""),
                "payload quotes should be JSON-escaped"
            )

            // Neither portal may assign user-controlled data through innerHTML.
            val innerHtmlAssignment = Regex("""innerHTML\s*=""")
            for (path in listOf("/", "/audience")) {
                val html = open("$baseUrl$path").bodyText()
                assertFalse(
                    innerHtmlAssignment.containsMatchIn(html),
                    "$path must build DOM with textContent, not innerHTML"
                )
            }
        }
    }
}
