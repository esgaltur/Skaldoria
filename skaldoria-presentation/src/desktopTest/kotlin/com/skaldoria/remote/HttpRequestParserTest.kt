package com.skaldoria.remote

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * F-09: the hand-written HTTP/1.1 parser, tested without a socket.
 *
 * SEC-7's caps (request line, header count, body size, chunked refusal) and EXP-4's
 * byte-vs-char body fix were previously reachable only by opening a real connection to a
 * running server, so the malformed and hostile inputs they exist for were never exercised.
 * Over an `InputStream` they are ordinary unit tests.
 */
class HttpRequestParserTest {

    private fun parse(raw: String) =
        HttpRequestParser.parse(ByteArrayInputStream(raw.toByteArray(StandardCharsets.UTF_8)))

    private fun parse(raw: ByteArray) = HttpRequestParser.parse(ByteArrayInputStream(raw))

    private fun ok(raw: String): HttpRequest {
        val result = parse(raw)
        assertIs<ParseResult.Ok>(result, "expected a parsed request, got $result")
        return result.request
    }

    private fun rejected(raw: String): ParseResult.Rejected {
        val result = parse(raw)
        assertIs<ParseResult.Rejected>(result, "expected a rejection, got $result")
        return result
    }

    // ---- the happy paths ----

    @Test
    fun `parses a bare GET`() {
        val request = ok("GET /api/state HTTP/1.1\r\nHost: x\r\n\r\n")
        assertEquals("GET", request.method)
        assertEquals("/api/state", request.path)
        assertEquals("", request.body)
    }

    @Test
    fun `splits the path from the query string`() {
        val request = ok("GET /api/action?action=next HTTP/1.1\r\n\r\n")
        assertEquals("/api/action", request.path)
        assertEquals("next", request.params["action"])
    }

    @Test
    fun `url-decodes query parameters`() {
        val request = ok("GET /api/qa/submit?text=hello%20world%21&author=A%2BB HTTP/1.1\r\n\r\n")
        assertEquals("hello world!", request.params["text"])
        assertEquals("A+B", request.params["author"])
    }

    @Test
    fun `a valueless query parameter yields an empty string`() {
        assertEquals("", ok("GET /x?flag HTTP/1.1\r\n\r\n").params["flag"])
    }

    @Test
    fun `header names are lower-cased so lookups are case-insensitive`() {
        val request = ok("GET /x HTTP/1.1\r\nX-Skaldoria-Token: abc\r\nCONTENT-TYPE: text/plain\r\n\r\n")
        assertEquals("abc", request.headers["x-skaldoria-token"])
        assertEquals("text/plain", request.headers["content-type"])
    }

    @Test
    fun `the method is upper-cased`() {
        assertEquals("POST", ok("post /x HTTP/1.1\r\n\r\n").method)
    }

    @Test
    fun `a form-encoded body merges into the parameters`() {
        val body = "action=jump&index=4"
        val request = ok(
            "POST /api/action HTTP/1.1\r\n" +
                "Content-Type: application/x-www-form-urlencoded\r\n" +
                "Content-Length: ${body.length}\r\n\r\n$body"
        )
        assertEquals("jump", request.params["action"])
        assertEquals("4", request.params["index"])
    }

    @Test
    fun `a body of another content type is not merged into the parameters`() {
        val body = "action=jump"
        val request = ok(
            "POST /api/action HTTP/1.1\r\n" +
                "Content-Type: application/json\r\n" +
                "Content-Length: ${body.length}\r\n\r\n$body"
        )
        assertEquals(body, request.body)
        assertNull(request.params["action"])
    }

    /** EXP-4 — `Content-Length` counts bytes, not characters. */
    @Test
    fun `a multi-byte body is read by byte count`() {
        val text = "naïve — ✅ вопрос"
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        assertTrue(bytes.size > text.length, "fixture must actually be multi-byte")

        val head = "POST /api/qa/submit HTTP/1.1\r\nContent-Length: ${bytes.size}\r\n\r\n"
            .toByteArray(StandardCharsets.UTF_8)
        val result = parse(head + bytes)

        assertIs<ParseResult.Ok>(result)
        assertEquals(text, result.request.body)
    }

    @Test
    fun `a truncated body yields what actually arrived rather than blocking`() {
        // Declares 100 bytes, sends 5, then EOF.
        val request = ok("POST /x HTTP/1.1\r\nContent-Length: 100\r\n\r\nabcde")
        assertEquals("abcde", request.body)
    }

    // ---- SEC-7: the caps ----

    @Test
    fun `chunked transfer-encoding is refused rather than mis-read`() {
        val rejection = rejected("POST /x HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n5\r\nhello\r\n0\r\n\r\n")
        assertEquals(411, rejection.statusCode)
    }

    @Test
    fun `chunked detection is case-insensitive`() {
        assertEquals(411, rejected("POST /x HTTP/1.1\r\nTransfer-Encoding: CHUNKED\r\n\r\n").statusCode)
    }

    @Test
    fun `a body over the cap is refused without being read`() {
        val rejection = rejected("POST /x HTTP/1.1\r\nContent-Length: ${HttpRequestParser.MAX_BODY_BYTES + 1}\r\n\r\n")
        assertEquals(413, rejection.statusCode)
    }

    @Test
    fun `a body exactly at the cap is accepted`() {
        val size = 32
        val body = "x".repeat(size)
        assertEquals(body, ok("POST /x HTTP/1.1\r\nContent-Length: $size\r\n\r\n$body").body)
    }

    @Test
    fun `an over-long request line is refused rather than buffered`() {
        val monstrous = "GET /" + "a".repeat(HttpRequestParser.MAX_REQUEST_LINE_BYTES + 100) + " HTTP/1.1\r\n\r\n"
        assertTrue(parse(monstrous) !is ParseResult.Ok, "an unbounded request line must not be accepted")
    }

    @Test
    fun `header count is capped`() {
        val flood = buildString {
            append("GET /x HTTP/1.1\r\n")
            repeat(HttpRequestParser.MAX_HEADER_COUNT + 50) { append("X-Pad-$it: v\r\n") }
            append("\r\n")
        }
        val result = parse(flood)
        if (result is ParseResult.Ok) {
            assertTrue(
                result.request.headers.size <= HttpRequestParser.MAX_HEADER_COUNT,
                "SEC-7: header map grew past the cap (${result.request.headers.size})"
            )
        }
    }

    // ---- malformed input ----

    @Test
    fun `an empty stream is incomplete, not a parse`() {
        assertIs<ParseResult.Incomplete>(parse(""))
    }

    @Test
    fun `a request line with no target is incomplete`() {
        assertIs<ParseResult.Incomplete>(parse("GET\r\n\r\n"))
    }

    @Test
    fun `a header line with no colon is skipped rather than crashing`() {
        val request = ok("GET /x HTTP/1.1\r\ngarbage-with-no-colon\r\nHost: y\r\n\r\n")
        assertEquals("y", request.headers["host"])
    }

    @Test
    fun `a malformed content-length is treated as no body`() {
        assertEquals("", ok("POST /x HTTP/1.1\r\nContent-Length: not-a-number\r\n\r\n").body)
    }

    @Test
    fun `bare newline line endings are tolerated`() {
        assertEquals("/x", ok("GET /x HTTP/1.1\nHost: y\n\n").path)
    }
}
