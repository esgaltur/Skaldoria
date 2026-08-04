package com.skaldoria.remote

import com.skaldoria.state.PresentationState
import java.net.HttpURLConnection
import java.net.URI
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RemoteCompanionServerTest {

    private lateinit var state: PresentationState
    private val testPort = 18889

    @BeforeTest
    fun setUp() {
        state = PresentationState("""
            # Slide One
            <!-- note: Note for slide 1 -->
            ---
            # Slide Two
            <!-- note: Note for slide 2 -->
        """.trimIndent())
    }

    @AfterTest
    fun tearDown() {
        RemoteCompanionServer.stop()
    }

    @Test
    fun `test companion server starts and responds with HTML web client`() {
        RemoteCompanionServer.start(state, testPort)
        assertTrue(RemoteCompanionServer.isRunning())

        val url = URI("http://127.0.0.1:$testPort/").toURL()
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connect()

        assertEquals(200, conn.responseCode)
        val body = conn.inputStream.bufferedReader().readText()
        assertTrue(body.contains("Skaldoria Remote Companion"))
    }

    @Test
    fun `test state API endpoint returns JSON`() {
        RemoteCompanionServer.start(state, testPort)

        val url = URI("http://127.0.0.1:$testPort/api/state").toURL()
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connect()

        assertEquals(200, conn.responseCode)
        val body = conn.inputStream.bufferedReader().readText()
        assertTrue(body.contains("\"currentSlideIndex\": 0"))
        assertTrue(body.contains("\"totalSlides\": 2"))
        assertTrue(body.contains("Note for slide 1"))
    }

    @Test
    fun `test action API executes slide navigation`() {
        RemoteCompanionServer.start(state, testPort)
        assertEquals(0, state.currentSlideIndex)

        val url = URI("http://127.0.0.1:$testPort/api/action?action=next").toURL()
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connect()

        assertEquals(200, conn.responseCode)
        assertEquals(1, state.currentSlideIndex)
    }
}
