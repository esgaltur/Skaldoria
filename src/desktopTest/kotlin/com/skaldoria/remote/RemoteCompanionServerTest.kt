package com.skaldoria.remote

import com.skaldoria.state.PresentationState
import java.net.HttpURLConnection
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RemoteCompanionServerTest {

    @Test
    fun testServerLifecycleAndApiEndpoints() {
        val state = PresentationState()
        state.updateMarkdown("""
            # Slide 1
            <!-- note: Welcome Note -->
            Welcome to the presentation
            ---
            # Slide 2
            <!-- note: Deep Dive Note -->
            Technical architecture deep dive
        """.trimIndent())

        val urlStr = RemoteCompanionServer.start(state, preferredPort = 18888)
        assertTrue(RemoteCompanionServer.isRunning(), "Server should be running after start()")
        assertTrue(urlStr.startsWith("http://"), "URL should be formatted with http scheme: $urlStr")

        val port = RemoteCompanionServer.currentPort
        val baseUrl = "http://127.0.0.1:$port"

        try {
            // 1. Test GET /api/state
            val stateConn = java.net.URI.create("$baseUrl/api/state").toURL().openConnection() as HttpURLConnection
            stateConn.requestMethod = "GET"
            stateConn.connectTimeout = 3000
            stateConn.readTimeout = 3000
            assertEquals(200, stateConn.responseCode)
            val stateBody = stateConn.inputStream.bufferedReader().use { it.readText() }
            assertTrue(stateBody.contains("\"currentSlideIndex\": 0"))
            assertTrue(stateBody.contains("\"totalSlides\": 2"))
            assertTrue(stateBody.contains("Slide 1"))

            // 2. Test GET /api/action?action=next
            val actionConn = java.net.URI.create("$baseUrl/api/action?action=next").toURL().openConnection() as HttpURLConnection
            actionConn.requestMethod = "GET"
            assertEquals(200, actionConn.responseCode)
            assertEquals(1, state.currentSlideIndex, "State should advance to slide 1 after 'next' action")

            // 3. Test GET /
            val webConn = java.net.URI.create("$baseUrl/").toURL().openConnection() as HttpURLConnection
            webConn.requestMethod = "GET"
            assertEquals(200, webConn.responseCode)
            val webHtml = webConn.inputStream.bufferedReader().use { it.readText() }
            assertTrue(webHtml.contains("Skaldoria Presenter Remote"))

            // 4. Test GET /audience
            val audienceConn = java.net.URI.create("$baseUrl/audience").toURL().openConnection() as HttpURLConnection
            audienceConn.requestMethod = "GET"
            assertEquals(200, audienceConn.responseCode)
            val audienceHtml = audienceConn.inputStream.bufferedReader().use { it.readText() }
            assertTrue(audienceHtml.contains("Skaldoria Audience Portal"))

        } finally {
            RemoteCompanionServer.stop()
            assertFalse(RemoteCompanionServer.isRunning(), "Server should be stopped")
        }
    }
}
