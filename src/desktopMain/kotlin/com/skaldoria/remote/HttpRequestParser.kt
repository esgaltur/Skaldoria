package com.skaldoria.remote

import java.io.InputStream
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** One parsed HTTP request. */
data class HttpRequest(
    val method: String,
    val path: String,
    val query: String,
    /** Header names lower-cased, so lookups need not guess the sender's capitalisation. */
    val headers: Map<String, String>,
    val body: String,
    /** Query parameters, with a form-encoded body merged over them. */
    val params: Map<String, String>
)

/** Outcome of reading one request off a connection. */
sealed interface ParseResult {
    /** A well-formed request. */
    data class Ok(val request: HttpRequest) : ParseResult

    /** Understood, but refused — the server should reply with [statusCode]. */
    data class Rejected(val statusCode: Int, val message: String) : ParseResult

    /** Nothing usable arrived: end of stream, or a request line that never terminated. */
    data object Incomplete : ParseResult
}

/**
 * Reads one HTTP/1.1 request from a stream.
 *
 * F-09: extracted from `RemoteCompanionServer.handleClientSocket`, where it was fused to a
 * live `Socket`. SEC-7's caps and EXP-4's byte-count fix exist precisely for malformed and
 * hostile input, and while the parser needed a real connection none of that input was ever
 * exercised. Over an `InputStream` it is `HttpRequestParserTest`.
 *
 * Byte-oriented throughout: the request line and headers are ASCII, and the body is decoded
 * only once its exact byte length is known. A decoding reader would consume bytes past the
 * header block into its own buffer and lose the start of the body.
 */
object HttpRequestParser {

    /** SEC-7: caps on a hand-written parser reading from an untrusted network. */
    const val MAX_REQUEST_LINE_BYTES = 8 * 1024
    const val MAX_HEADER_COUNT = 64
    const val MAX_BODY_BYTES = 1024 * 1024

    fun parse(input: InputStream): ParseResult {
        val requestLine = readAsciiLine(input, MAX_REQUEST_LINE_BYTES) ?: return ParseResult.Incomplete
        val parts = requestLine.trim().split(" ")
        if (parts.size < 2) return ParseResult.Incomplete

        val method = parts[0].uppercase()
        val rawUri = parts[1]

        val headers = mutableMapOf<String, String>()
        var contentLength = 0
        var isChunked = false

        while (headers.size < MAX_HEADER_COUNT) {
            val line = readAsciiLine(input, MAX_REQUEST_LINE_BYTES) ?: break
            if (line.isEmpty()) break

            val colonIndex = line.indexOf(':')
            // A line without a colon is not a header. Skipping beats aborting the request.
            if (colonIndex <= 0) continue

            val name = line.substring(0, colonIndex).trim().lowercase()
            val value = line.substring(colonIndex + 1).trim()
            headers[name] = value

            when (name) {
                "content-length" -> contentLength = value.toIntOrNull() ?: 0
                "transfer-encoding" -> if (value.contains("chunked", ignoreCase = true)) isChunked = true
            }
        }

        // SEC-7: this parser only understands Content-Length framing. Say so, rather than
        // mis-reading a chunked body as raw bytes.
        if (isChunked) {
            return ParseResult.Rejected(411, "Chunked transfer-encoding is not supported")
        }
        if (contentLength > MAX_BODY_BYTES) {
            return ParseResult.Rejected(413, "Request body too large")
        }

        val body = if (contentLength > 0) readBody(input, contentLength) else ""

        val questionMarkIndex = rawUri.indexOf('?')
        val path = if (questionMarkIndex >= 0) rawUri.substring(0, questionMarkIndex) else rawUri
        val query = if (questionMarkIndex >= 0) rawUri.substring(questionMarkIndex + 1) else ""

        val params = parseQueryParams(query).toMutableMap()
        if (body.isNotBlank() && headers["content-type"]?.contains("application/x-www-form-urlencoded") == true) {
            params.putAll(parseQueryParams(body))
        }

        return ParseResult.Ok(
            HttpRequest(
                method = method,
                path = path,
                query = query,
                headers = headers,
                body = body,
                params = params
            )
        )
    }

    /**
     * EXP-4: `Content-Length` counts **bytes**. The original read that many *chars* through a
     * decoding reader, so any multi-byte body — any non-ASCII question — never satisfied the
     * loop and blocked until the socket timed out ten seconds later.
     *
     * Returns whatever arrived if the peer disconnects early, rather than waiting forever.
     */
    private fun readBody(input: InputStream, contentLength: Int): String {
        val raw = ByteArray(contentLength)
        var readTotal = 0
        while (readTotal < contentLength) {
            val read = input.read(raw, readTotal, contentLength - readTotal)
            if (read == -1) break
            readTotal += read
        }
        return String(raw, 0, readTotal, StandardCharsets.UTF_8)
    }

    /**
     * Reads one CRLF-terminated line as ASCII, without buffering beyond it.
     *
     * SEC-7: aborts past [limit] bytes so an endless request line cannot be read into memory.
     * Returns null at end of stream or when the limit is exceeded.
     */
    private fun readAsciiLine(input: InputStream, limit: Int): String? {
        val buffer = StringBuilder()
        while (true) {
            val b = input.read()
            if (b == -1) return if (buffer.isEmpty()) null else buffer.toString()
            if (b == '\n'.code) return buffer.toString().removeSuffix("\r")
            if (buffer.length >= limit) return null
            buffer.append(b.toChar())
        }
    }

    private fun parseQueryParams(query: String?): Map<String, String> {
        if (query.isNullOrBlank()) return emptyMap()
        return query.split("&").associate { param ->
            val parts = param.split("=", limit = 2)
            val key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8.name())
            val value = if (parts.size == 2) URLDecoder.decode(parts[1], StandardCharsets.UTF_8.name()) else ""
            key to value
        }
    }
}
