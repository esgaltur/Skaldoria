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
            ## Slide Two Poll
            <!-- poll: Yes | No | Maybe -->
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

        val url = URI("http://127.0.0.1:$testPort/remote").toURL()
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connect()

        assertEquals(200, conn.responseCode)
        val body = conn.inputStream.bufferedReader().readText()
        assertTrue(body.contains("Skaldoria Presenter Remote"))
    }

    @Test
    fun `test audience portal responds with HTML web client`() {
        RemoteCompanionServer.start(state, testPort)
        assertTrue(RemoteCompanionServer.isRunning())

        val url = URI("http://127.0.0.1:$testPort/audience").toURL()
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connect()

        assertEquals(200, conn.responseCode)
        val body = conn.inputStream.bufferedReader().readText()
        assertTrue(body.contains("Skaldoria Audience Portal"))
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
    }

    @Test
    fun `test audience voting API endpoint`() {
        RemoteCompanionServer.start(state, testPort)

        // Navigate to slide 1 which has a poll
        state.goToSlide(1)

        val url = URI("http://127.0.0.1:$testPort/api/poll/vote?slide=1&option=0").toURL()
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.outputStream.write(ByteArray(0))
        conn.connect()

        assertEquals(200, conn.responseCode)
        val votes = state.getVotesForSlide(1)
        assertEquals(1, votes[0])
    }

    @Test
    fun `test action API endpoint navigates slides`() {
        RemoteCompanionServer.start(state, testPort)
        assertEquals(0, state.currentSlideIndex)

        val url = URI("http://127.0.0.1:$testPort/api/action?cmd=next").toURL()
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.outputStream.write(ByteArray(0))
        conn.connect()

        assertEquals(200, conn.responseCode)
        assertEquals(1, state.currentSlideIndex)
    }
}
