package com.skaldoria.remote

import com.skaldoria.PresentationStateTestBase
import com.skaldoria.core.json.Json
import java.net.HttpURLConnection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * F-04: audience-supplied text must always produce **valid** JSON.
 *
 * `escapeJson` handled `\`, `"`, `\n`, `\r` and `\t` but not the other C0 control
 * characters, which RFC 8259 requires to be escaped inside a string. Audience text arrives
 * URL-decoded, so `%01` yields a raw `0x01` that survives `trim()` and `take()` and lands
 * verbatim in the response.
 *
 * The consequence is not cosmetic. `/api/state` is polled sub-second by every connected
 * device; an unparseable body makes `res.json()` throw, the portals swallow it in
 * `catch(e){}`, and **both portals stop updating for everyone** until that question is
 * dismissed — from a single crafted submission.
 */
class JsonEscapingTest : PresentationStateTestBase() {

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

    /**
     * Control characters that may legally appear between JSON tokens. Anything else in the
     * C0 range means an unescaped character leaked out of a string.
     */
    private fun rawControlCharacters(body: String): List<Int> =
        body.filter { it.code < 0x20 && it != '\n' && it != '\r' && it != '\t' }
            .map { it.code }
            .distinct()

    // ---- the encoder itself ----

    @Test
    fun `escaping covers the whole C0 range`() {
        for (code in 0x00..0x1F) {
            val encoded = Json.string(code.toChar().toString())
            assertTrue(
                rawControlCharacters(encoded).isEmpty(),
                "U+%04X leaked into the output unescaped".format(code)
            )
        }
    }

    @Test
    fun `the common characters keep their short escape forms`() {
        assertEquals("\"a\\nb\"", Json.string("a\nb"))
        assertEquals("\"a\\rb\"", Json.string("a\rb"))
        assertEquals("\"a\\tb\"", Json.string("a\tb"))
        assertEquals("\"a\\\\b\"", Json.string("a\\b"))
        assertEquals("\"a\\\"b\"", Json.string("a\"b"))
        assertEquals("\"a\\bb\"", Json.string("a\bb"))
    }

    @Test
    fun `other control characters use the six-character unicode form`() {
        assertEquals("\"a\\u0001b\"", Json.string("a\u0001b"))
        assertEquals("\"\\u001f\"", Json.string("\u001F"))
        assertEquals("\"\\u0000\"", Json.string("\u0000"))
    }

    @Test
    fun `ordinary and non-ascii text passes through untouched`() {
        assertEquals("\"Plain question?\"", Json.string("Plain question?"))
        // No escaping is required for non-ASCII in a UTF-8 body.
        assertEquals("\"naïve — ✅\"", Json.string("naïve — ✅"))
        assertEquals("\"\"", Json.string(""))
    }

    @Test
    fun `carriage returns survive rather than being dropped`() {
        // The previous routine replaced \r with the empty string, silently altering the
        // text a speaker sees. It is data, not formatting.
        assertEquals("\"a\\r\\nb\"", Json.string("a\r\nb"))
    }

    // ---- the end-to-end reproduction ----

    @Test
    fun `a control character in an audience question cannot corrupt the state feed`() {
        val state = presentationState()
        RemoteCompanionServer.start(state, preferredPort = 18899)
        try {
            val base = "http://127.0.0.1:${RemoteCompanionServer.currentPort}"

            val submit = open("$base/api/qa/submit?author=Probe&text=broken%01question", "POST")
            assertEquals(200, submit.responseCode, submit.bodyText())

            val body = open("$base/api/state").bodyText()

            assertTrue(body.contains("question"), "the question should still be delivered")
            assertEquals(
                emptyList(),
                rawControlCharacters(body),
                "unescaped control characters make the body unparseable for every polling device"
            )
            assertTrue(body.contains("\\u0001"), "the control character should survive as an escape")
        } finally {
            RemoteCompanionServer.stop()
            state.dispose()
        }
    }

    @Test
    fun `a quote in an audience question cannot break out of its JSON string`() {
        val state = presentationState()
        RemoteCompanionServer.start(state, preferredPort = 18899)
        try {
            val base = "http://127.0.0.1:${RemoteCompanionServer.currentPort}"
            open("$base/api/qa/submit?author=Probe&text=%22%2C%22isAnswered%22%3Atrue%2C%22x%22%3A%22", "POST")
                .responseCode

            val body = open("$base/api/state").bodyText()
            assertFalse(
                body.contains("\"isAnswered\": true") && body.contains("\"x\""),
                "audience text must not be able to inject JSON fields"
            )
        } finally {
            RemoteCompanionServer.stop()
            state.dispose()
        }
    }
}
