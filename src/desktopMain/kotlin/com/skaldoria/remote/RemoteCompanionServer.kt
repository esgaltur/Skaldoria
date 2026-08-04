package com.skaldoria.remote

import com.skaldoria.core.models.SlideElement
import com.skaldoria.state.PresentationState
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Pure Kotlin/Java standard socket HTTP/1.1 micro-server for wireless speaker remote controls
 * and real-time live audience interaction (in-slide polling, Q&A, and follow-up parking lot).
 *
 * Guaranteed 100% dependency-free using java.base standard sockets (no com.sun.net.httpserver or external jars).
 */
object RemoteCompanionServer {

    private var serverSocket: ServerSocket? = null
    private var executor: ExecutorService? = null
    private var listenerThread: Thread? = null
    private val isRunningFlag = AtomicBoolean(false)

    var currentPort: Int = 8888
        private set

    /**
     * SEC-2: per-session presenter credential, regenerated on every [start]. Presenter-scope
     * routes require it; audience-scope routes do not. Delivered to the speaker's phone
     * inside the pairing URL/QR ([presenterUrl]) and returned by the portal on each request
     * as the `X-Skaldoria-Token` header.
     *
     * Sending it as a *custom header* is deliberate: it forces a CORS preflight for any
     * cross-origin attempt, which closes the residual CSRF gap that POST-only (SEC-3) leaves
     * open to cross-origin form submissions.
     */
    @Volatile
    private var sessionToken: String = ""

    /** Routes that drive the presentation or moderate the audience. Token required. */
    private val PRESENTER_ENDPOINTS = setOf("/api/action", "/api/qa/dismiss")

    /** SEC-4: ceiling on concurrent request handlers. */
    private const val MAX_WORKER_THREADS = 16

    /** SEC-7: caps on a hand-written parser reading from an untrusted network. */
    private const val MAX_REQUEST_LINE_BYTES = 8 * 1024
    private const val MAX_HEADER_COUNT = 64
    private const val MAX_BODY_BYTES = 1024 * 1024

    /**
     * SEC-5: per-client token bucket over write endpoints. Sized so ordinary use — voting,
     * asking a question, upvoting a few — never trips it, while a script hammering the
     * endpoint does.
     */
    private const val RATE_LIMIT_BURST = 12
    private const val RATE_LIMIT_REFILL_PER_SECOND = 0.5

    private class TokenBucket(var tokens: Double, var lastRefillNanos: Long)

    private val rateBuckets = java.util.concurrent.ConcurrentHashMap<String, TokenBucket>()

    private fun allowRequest(clientKey: String): Boolean {
        val now = System.nanoTime()
        val bucket = rateBuckets.computeIfAbsent(clientKey) { TokenBucket(RATE_LIMIT_BURST.toDouble(), now) }
        synchronized(bucket) {
            val elapsedSeconds = (now - bucket.lastRefillNanos) / 1_000_000_000.0
            bucket.tokens = minOf(
                RATE_LIMIT_BURST.toDouble(),
                bucket.tokens + elapsedSeconds * RATE_LIMIT_REFILL_PER_SECOND
            )
            bucket.lastRefillNanos = now
            if (bucket.tokens < 1.0) return false
            bucket.tokens -= 1.0
            return true
        }
    }

    fun isRunning(): Boolean = isRunningFlag.get()

    /** Pairing URL for the speaker's own device — carries the session token. Treat as a secret. */
    fun presenterUrl(): String =
        "http://${getLocalIpAddress()}:$currentPort/remote?t=$sessionToken"

    /** Pairing URL handed to the audience. Deliberately carries no token. */
    fun audienceUrl(): String =
        "http://${getLocalIpAddress()}:$currentPort/audience"

    private fun generateSessionToken(): String {
        val bytes = ByteArray(16)
        java.security.SecureRandom().nextBytes(bytes)
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /** Constant-time comparison so a token cannot be recovered by timing the response. */
    private fun isAuthorized(params: Map<String, String>, headers: Map<String, String>): Boolean {
        val expected = sessionToken
        if (expected.isEmpty()) return false
        val supplied = headers["x-skaldoria-token"] ?: params["t"] ?: params["token"] ?: return false
        return java.security.MessageDigest.isEqual(
            supplied.toByteArray(StandardCharsets.UTF_8),
            expected.toByteArray(StandardCharsets.UTF_8)
        )
    }

    /**
     * Starts the embedded server with automatic port-fallback if the preferred port is occupied.
     */
    @Synchronized
    fun start(state: PresentationState, preferredPort: Int = 8888): String {
        stop()

        var boundSocket: ServerSocket? = null
        var boundPort = preferredPort

        // Try preferred port and up to 50 consecutive ports
        for (portCandidate in preferredPort..(preferredPort + 50)) {
            try {
                val ss = ServerSocket()
                ss.reuseAddress = true
                ss.bind(InetSocketAddress(portCandidate))
                boundSocket = ss
                boundPort = portCandidate
                break
            } catch (_: Exception) {
                // Port occupied or restricted, try next
            }
        }

        // If specific ports failed, bind to ephemeral port (0)
        if (boundSocket == null) {
            try {
                val ss = ServerSocket()
                ss.reuseAddress = true
                ss.bind(InetSocketAddress(0))
                boundSocket = ss
                boundPort = ss.localPort
            } catch (e: Exception) {
                throw IllegalStateException("Failed to bind HTTP server to any available network port: ${e.message}", e)
            }
        }

        serverSocket = boundSocket
        currentPort = boundPort
        sessionToken = generateSessionToken()
        isRunningFlag.set(true)

        // SEC-4: bounded. `newCachedThreadPool` spawned a thread per connection with no
        // ceiling, so a connection flood was an easy denial of service — and the portals'
        // sub-second polling with `Connection: close` already churns connections hard.
        val exec = Executors.newFixedThreadPool(MAX_WORKER_THREADS) { runnable ->
            Thread(runnable, "Skaldoria-HTTP-Worker").apply {
                isDaemon = true
            }
        }
        executor = exec

        val listener = Thread({
            try {
                while (isRunningFlag.get() && !boundSocket.isClosed) {
                    try {
                        val clientSocket = boundSocket.accept()
                        exec.submit {
                            handleClientSocket(clientSocket, state)
                        }
                    } catch (se: SocketException) {
                        // Socket closed during stop()
                        break
                    } catch (_: Exception) {}
                }
            } finally {
                isRunningFlag.set(false)
            }
        }, "Skaldoria-HTTP-Listener").apply {
            isDaemon = true
        }

        listenerThread = listener
        listener.start()

        val localIp = getLocalIpAddress()
        return "http://$localIp:$boundPort"
    }

    @Synchronized
    fun stop() {
        isRunningFlag.set(false)
        sessionToken = ""
        rateBuckets.clear()
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null

        try {
            listenerThread?.interrupt()
        } catch (_: Exception) {}
        listenerThread = null

        try {
            executor?.shutdownNow()
            executor?.awaitTermination(1, TimeUnit.SECONDS)
        } catch (_: Exception) {}
        executor = null
    }

    /** One reachable address the companion could advertise. */
    data class NetworkCandidate(
        val address: String,
        val interfaceName: String,
        /** A host-only / NAT adapter from a hypervisor — routable for the host, not for a phone. */
        val isLikelyVirtual: Boolean,
        /** The address the OS would actually use for outbound traffic. */
        val isRouted: Boolean
    ) {
        val label: String get() = if (isRouted) "$interfaceName (active)" else interfaceName
    }

    /**
     * Overrides automatic detection. Set when the speaker picks an address in the pairing
     * dialog — no heuristic can be right on a multi-homed machine, so the choice has to be
     * available.
     */
    @Volatile
    var preferredAddress: String? = null

    /**
     * Display names that mark a hypervisor or tunnelling adapter.
     *
     * `NetworkInterface.isVirtual` cannot be used for this: it reports whether the interface
     * is a *sub-interface* (an alias), and is `false` for VirtualBox, VMware and Hyper-V
     * adapters — which is exactly why they were being picked.
     */
    private val VIRTUAL_ADAPTER_HINTS = listOf(
        "virtualbox", "vmware", "vmnet", "hyper-v", "vethernet", "docker",
        "wsl", "loopback", "tunnel", "tap-", "tun", "npcap", "bluetooth", "vpn"
    )

    /**
     * The address the OS would use to reach the outside world.
     *
     * A connected UDP socket sends nothing — it only performs a route lookup — so this is
     * cheap, needs no reachable internet, and reports the interface holding the default
     * route. That is almost always the adapter a phone on the same wifi can reach.
     */
    private fun routedAddress(): String? = runCatching {
        java.net.DatagramSocket().use { socket ->
            socket.connect(InetAddress.getByName("8.8.8.8"), 10002)
            (socket.localAddress as? java.net.Inet4Address)
                ?.hostAddress
                ?.takeUnless { it == "0.0.0.0" }
        }
    }.getOrNull()

    /**
     * Every usable IPv4 address, best first.
     *
     * Ranked rather than "first match wins", which is what made the companion advertise a
     * VirtualBox host-only address that no phone could ever reach.
     */
    fun availableAddresses(): List<NetworkCandidate> {
        val routed = routedAddress()

        val candidates = runCatching {
            NetworkInterface.getNetworkInterfaces().asSequence()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { nif ->
                    val virtual = VIRTUAL_ADAPTER_HINTS.any { hint ->
                        nif.displayName?.contains(hint, ignoreCase = true) == true ||
                            nif.name.contains(hint, ignoreCase = true)
                    }
                    nif.inetAddresses.asSequence()
                        .filterIsInstance<java.net.Inet4Address>()
                        .filter { !it.isLoopbackAddress && !it.isLinkLocalAddress }
                        .map { addr ->
                            NetworkCandidate(
                                address = addr.hostAddress,
                                interfaceName = nif.displayName ?: nif.name,
                                isLikelyVirtual = virtual,
                                isRouted = addr.hostAddress == routed
                            )
                        }
                }
                .toList()
        }.getOrDefault(emptyList())

        // Routed first (it is the one that actually works), then real adapters, then the
        // hypervisor ones, which are kept only as a last resort rather than hidden.
        return candidates.sortedWith(
            compareByDescending<NetworkCandidate> { it.isRouted }
                .thenBy { it.isLikelyVirtual }
                .thenBy { it.address }
        )
    }

    fun getLocalIpAddress(): String {
        preferredAddress?.takeIf { it.isNotBlank() }?.let { return it }
        return availableAddresses().firstOrNull()?.address
            ?: runCatching { InetAddress.getLocalHost()?.hostAddress }.getOrNull()
            ?: "127.0.0.1"
    }

    // ==========================================
    // HTTP REQUEST / RESPONSE PROCESSING
    // ==========================================

    /**
     * Reads one CRLF-terminated line as ASCII, without buffering beyond it.
     *
     * SEC-7: aborts past [limit] bytes so an endless request line cannot be read into
     * memory. Returns null at end of stream or when the limit is exceeded.
     */
    private fun readAsciiLine(input: java.io.InputStream, limit: Int): String? {
        val buffer = StringBuilder()
        while (true) {
            val b = input.read()
            if (b == -1) return if (buffer.isEmpty()) null else buffer.toString()
            if (b == '\n'.code) return buffer.toString().removeSuffix("\r")
            if (buffer.length >= limit) return null
            buffer.append(b.toChar())
        }
    }

    private fun handleClientSocket(socket: Socket, state: PresentationState) {
        try {
            socket.soTimeout = 10000 // 10s socket timeout
            // Byte-oriented throughout: the request line and headers are ASCII, and the
            // body is decoded once its exact byte length is known (EXP-4). A decoding
            // reader here would consume bytes past the header block into its buffer.
            val inputStream = java.io.BufferedInputStream(socket.getInputStream())
            val outputStream = socket.getOutputStream()

            val requestLine = readAsciiLine(inputStream, MAX_REQUEST_LINE_BYTES) ?: return
            val parts = requestLine.trim().split(" ")
            if (parts.size < 2) return

            val method = parts[0].uppercase()
            val rawUri = parts[1]

            // Read Headers
            var contentLength = 0
            var isChunked = false
            val headers = mutableMapOf<String, String>()
            while (headers.size < MAX_HEADER_COUNT) {
                val line = readAsciiLine(inputStream, MAX_REQUEST_LINE_BYTES) ?: break
                if (line.isEmpty()) break
                val colonIdx = line.indexOf(':')
                if (colonIdx > 0) {
                    val hName = line.substring(0, colonIdx).trim().lowercase()
                    val hVal = line.substring(colonIdx + 1).trim()
                    headers[hName] = hVal
                    if (hName == "content-length") {
                        contentLength = hVal.toIntOrNull() ?: 0
                    }
                    if (hName == "transfer-encoding" && hVal.contains("chunked", ignoreCase = true)) {
                        isChunked = true
                    }
                }
            }

            // SEC-7: this parser only understands Content-Length framing. Say so rather
            // than mis-reading a chunked body as if it were raw bytes.
            if (isChunked) {
                sendErrorResponse(outputStream, "Chunked transfer-encoding is not supported", 411)
                return
            }

            // EXP-4: Content-Length counts BYTES. The previous code read that many CHARS
            // through a decoding reader, so any multi-byte body (any non-ASCII question)
            // never satisfied the loop and blocked until the 10s socket timeout.
            var body = ""
            if (contentLength > 0) {
                if (contentLength > MAX_BODY_BYTES) {
                    sendErrorResponse(outputStream, "Request body too large", 413)
                    return
                }
                val raw = ByteArray(contentLength)
                var readTotal = 0
                while (readTotal < contentLength) {
                    val read = inputStream.read(raw, readTotal, contentLength - readTotal)
                    if (read == -1) break
                    readTotal += read
                }
                body = String(raw, 0, readTotal, StandardCharsets.UTF_8)
            }

            if (method == "OPTIONS") {
                sendNoContentResponse(outputStream)
                return
            }

            val questionMarkIdx = rawUri.indexOf('?')
            val path = if (questionMarkIdx >= 0) rawUri.substring(0, questionMarkIdx) else rawUri
            val queryString = if (questionMarkIdx >= 0) rawUri.substring(questionMarkIdx + 1) else ""

            val queryParams = parseQueryParams(queryString).toMutableMap()
            if (body.isNotBlank() && (headers["content-type"]?.contains("application/x-www-form-urlencoded") == true)) {
                queryParams.putAll(parseQueryParams(body))
            }

            // SEC-5: on a LAN each audience device has its own address, so the peer
            // address is a workable per-device identity for both rate limiting and
            // one-ballot-per-device.
            val clientKey = socket.inetAddress?.hostAddress ?: "unknown"

            // Route request
            routeRequest(path, method, queryParams, headers, body, outputStream, state, clientKey)
        } catch (_: Exception) {
            // Socket or I/O error on client disconnect
        } finally {
            try {
                socket.close()
            } catch (_: Exception) {}
        }
    }

    /**
     * Endpoints that mutate presentation or audience state. These require POST so that
     * a cross-origin page cannot trigger them with a bare `<img src="...">` or link
     * (SEC-3). Combined with the session token (SEC-2) this closes the drive-by path.
     */
    private val WRITE_ENDPOINTS = setOf(
        "/api/action",
        "/api/poll/vote",
        "/api/qa/submit",
        "/api/qa/upvote",
        "/api/qa/dismiss",
        "/api/parking-lot/add"
    )

    private fun routeRequest(
        path: String,
        method: String,
        params: Map<String, String>,
        headers: Map<String, String>,
        body: String,
        output: OutputStream,
        state: PresentationState,
        clientKey: String
    ) {
        if (path in WRITE_ENDPOINTS && method != "POST") {
            sendErrorResponse(output, "This endpoint requires POST", 405)
            return
        }

        // SEC-5: throttle writes only — polling /api/state must stay free.
        if (path in WRITE_ENDPOINTS && !allowRequest(clientKey)) {
            sendErrorResponse(output, "Too many requests", 429)
            return
        }

        val authorized = isAuthorized(params, headers)

        if (path in PRESENTER_ENDPOINTS && !authorized) {
            sendErrorResponse(output, "Presenter session token required", 401)
            return
        }

        when (path) {
            "/", "/remote" -> {
                // The portal itself carries no secrets; it is inert without a valid token,
                // so it is served unconditionally rather than creating a pairing dead end.
                sendHtmlResponse(output, getCompanionHtml())
            }
            "/audience" -> {
                sendHtmlResponse(output, getAudienceHtml())
            }
            "/api/state" -> {
                handleStateApi(output, state, includeNotes = authorized)
            }
            "/api/action" -> {
                handleActionApi(output, params, state)
            }
            "/api/poll/vote" -> {
                handlePollVoteApi(output, params, state, clientKey)
            }
            "/api/qa/submit" -> {
                handleQaSubmitApi(output, params, state)
            }
            "/api/qa/upvote" -> {
                handleQaUpvoteApi(output, params, state)
            }
            "/api/qa/dismiss" -> {
                handleQaDismissApi(output, params, state)
            }
            "/api/parking-lot/add" -> {
                handleParkingLotAddApi(output, params, state)
            }
            else -> {
                sendErrorResponse(output, "Endpoint not found: $path", 404)
            }
        }
    }

    // ==========================================
    // API IMPLEMENTATIONS
    // ==========================================

    private fun handleStateApi(output: OutputStream, state: PresentationState, includeNotes: Boolean) {
        try {
            val current = state.currentSlide
            // SEC-2: speaker notes are presenter-only. Audience devices reach this same
            // endpoint, so the field is emitted empty rather than omitted — the portal JS
            // treats "no notes" identically and needs no special case.
            val visibleNotes = if (includeNotes) (current?.notes ?: emptyList()) else emptyList()
            val notesJson = visibleNotes.joinToString(prefix = "[", postfix = "]") { n ->
                "\"${escapeJson(n)}\""
            }

            val pollElement = current?.elements?.filterIsInstance<SlideElement.Poll>()?.firstOrNull()
            val pollJson = if (pollElement != null) {
                val votes = state.getVotesForSlide(state.currentSlideIndex)
                val optionsJson = pollElement.options.mapIndexed { idx, opt ->
                    val count = votes[idx] ?: 0
                    """{"index": $idx, "text": "${escapeJson(opt)}", "votes": $count}"""
                }.joinToString(prefix = "[", postfix = "]")
                """{"hasPoll": true, "slideIndex": ${state.currentSlideIndex}, "question": "${escapeJson(pollElement.question)}", "options": $optionsJson}"""
            } else {
                """{"hasPoll": false}"""
            }

            // PRF-1: a consistent snapshot. This used to be a bare `.toList()` wrapped in a
            // try/catch that swallowed the resulting ConcurrentModificationException.
            val questionsList = state.audienceQuestionsSnapshot()
            val questionsJson = questionsList.joinToString(prefix = "[", postfix = "]") { q ->
                """{"id": "${q.id}", "author": "${escapeJson(q.author)}", "text": "${escapeJson(q.text)}", "upvotes": ${q.upvotes}, "isAnswered": ${q.isAnswered}}"""
            }

            val title = escapeJson(current?.title ?: "Untitled Slide")
            val json = """
                {
                    "currentSlideIndex": ${state.currentSlideIndex},
                    "totalSlides": ${state.slides.size},
                    "title": "$title",
                    "notes": $notesJson,
                    "elapsedSeconds": ${state.elapsedSeconds},
                    "isTimerRunning": ${state.isTimerRunning},
                    "isBlackout": ${state.isBlackoutActive},
                    "isWhiteout": ${state.isWhiteoutActive},
                    "poll": $pollJson,
                    "questions": $questionsJson
                }
            """.trimIndent()

            sendJsonResponse(output, json)
        } catch (t: Throwable) {
            sendErrorResponse(output, "Failed to retrieve presentation state: ${t.message}")
        }
    }

    private fun handleActionApi(output: OutputStream, params: Map<String, String>, state: PresentationState) {
        try {
            val action = params["action"] ?: params["cmd"]
            // PRF-1: every mutation from an HTTP worker thread goes through a snapshot.
            state.applyFromBackgroundThread {
                when (action) {
                    "next" -> state.nextSlide()
                    "prev" -> state.previousSlide()
                    "jump" -> (params["index"] ?: params["slideIndex"] ?: params["slide"])?.toIntOrNull()?.let { state.goToSlide(it) }
                    "blackout" -> state.toggleBlackout()
                    "whiteout" -> state.toggleWhiteout()
                    "toggleTimer" -> state.toggleTimer()
                    "resetTimer" -> state.resetTimer()
                }
            }
            sendJsonResponse(output, """{"status":"ok"}""")
        } catch (t: Throwable) {
            sendErrorResponse(output, "Action execution failed: ${t.message}")
        }
    }

    private fun handlePollVoteApi(
        output: OutputStream,
        params: Map<String, String>,
        state: PresentationState,
        clientKey: String
    ) {
        try {
            val slideIdx = (params["slideIndex"] ?: params["slide"])?.toIntOrNull() ?: state.currentSlideIndex
            val optionIdx = (params["optionIndex"] ?: params["option"])?.toIntOrNull()

            if (optionIdx != null && optionIdx >= 0) {
                state.applyFromBackgroundThread { state.recordVote(slideIdx, optionIdx, voterKey = clientKey) }
                sendJsonResponse(output, """{"status":"ok", "votedSlide": $slideIdx, "votedOption": $optionIdx}""")
            } else {
                sendJsonResponse(output, """{"status":"error", "message":"Invalid option"}""", 400)
            }
        } catch (t: Throwable) {
            sendErrorResponse(output, "Poll voting failed: ${t.message}")
        }
    }

    private fun handleQaSubmitApi(output: OutputStream, params: Map<String, String>, state: PresentationState) {
        try {
            val author = params["author"]?.trim()?.ifBlank { "Anonymous" } ?: "Anonymous"
            val text = params["text"]?.trim() ?: ""

            if (text.isNotBlank()) {
                val q = state.applyFromBackgroundThread { state.submitQuestion(author, text) }
                sendJsonResponse(output, """{"status":"ok", "id":"${q.id}"}""")
            } else {
                sendJsonResponse(output, """{"status":"error", "message":"Empty question"}""", 400)
            }
        } catch (t: Throwable) {
            sendErrorResponse(output, "Q&A submission failed: ${t.message}")
        }
    }

    private fun handleQaUpvoteApi(output: OutputStream, params: Map<String, String>, state: PresentationState) {
        try {
            val id = params["id"]
            if (!id.isNullOrBlank()) {
                state.applyFromBackgroundThread { state.upvoteQuestion(id) }
                sendJsonResponse(output, """{"status":"ok"}""")
            } else {
                sendJsonResponse(output, """{"status":"error", "message":"Missing question id"}""", 400)
            }
        } catch (t: Throwable) {
            sendErrorResponse(output, "Upvote failed: ${t.message}")
        }
    }

    private fun handleQaDismissApi(output: OutputStream, params: Map<String, String>, state: PresentationState) {
        try {
            val id = params["id"]
            if (!id.isNullOrBlank()) {
                state.applyFromBackgroundThread { state.dismissQuestion(id) }
                sendJsonResponse(output, """{"status":"ok"}""")
            } else {
                sendJsonResponse(output, """{"status":"error", "message":"Missing question id"}""", 400)
            }
        } catch (t: Throwable) {
            sendErrorResponse(output, "Dismiss failed: ${t.message}")
        }
    }

    private fun handleParkingLotAddApi(output: OutputStream, params: Map<String, String>, state: PresentationState) {
        try {
            val text = params["question"] ?: params["text"] ?: ""
            val author = params["author"]
            if (text.isNotBlank()) {
                state.applyFromBackgroundThread {
                    state.addFollowUpQuestion(
                        question = text.trim(),
                        slideIndex = state.currentSlideIndex,
                        author = author?.trim()?.ifBlank { null }
                    )
                }
                sendJsonResponse(output, """{"status":"ok"}""")
            } else {
                sendJsonResponse(output, """{"status":"error", "message":"Empty question"}""", 400)
            }
        } catch (t: Throwable) {
            sendErrorResponse(output, "Parking lot addition failed: ${t.message}")
        }
    }

    // ==========================================
    // UTILITY & RESPONSE WRITERS
    // ==========================================

    /**
     * SEC-3: no `Access-Control-Allow-*` headers are emitted anywhere. Both bundled
     * portals are same-origin and do not need CORS; the previous wildcard let any
     * website the presenter visited drive the deck. Do not reintroduce a wildcard —
     * allow-list explicit origins if cross-origin access is ever required.
     */
    private fun sendNoContentResponse(output: OutputStream) {
        val raw = "HTTP/1.1 204 No Content\r\n" +
                "Content-Length: 0\r\n" +
                "Connection: close\r\n\r\n"
        output.write(raw.toByteArray(StandardCharsets.UTF_8))
        output.flush()
    }

    private fun statusTextFor(statusCode: Int): String = when (statusCode) {
        200 -> "OK"
        400 -> "Bad Request"
        401 -> "Unauthorized"
        404 -> "Not Found"
        405 -> "Method Not Allowed"
        411 -> "Length Required"
        413 -> "Payload Too Large"
        429 -> "Too Many Requests"
        else -> "Internal Server Error"
    }

    private fun sendJsonResponse(output: OutputStream, json: String, statusCode: Int = 200) {
        sendResponse(output, statusCode, statusTextFor(statusCode), "application/json; charset=utf-8", json.toByteArray(StandardCharsets.UTF_8))
    }

    private fun sendHtmlResponse(output: OutputStream, html: String) {
        sendResponse(output, 200, "OK", "text/html; charset=utf-8", html.toByteArray(StandardCharsets.UTF_8))
    }

    private fun sendErrorResponse(output: OutputStream, error: String, statusCode: Int = 500) {
        val json = """{"status":"error", "message":"${escapeJson(error)}"}"""
        sendJsonResponse(output, json, statusCode)
    }

    private fun sendResponse(
        output: OutputStream,
        statusCode: Int,
        statusText: String,
        contentType: String,
        body: ByteArray
    ) {
        val header = "HTTP/1.1 $statusCode $statusText\r\n" +
                "Content-Type: $contentType\r\n" +
                "Content-Length: ${body.size}\r\n" +
                "X-Content-Type-Options: nosniff\r\n" +
                "Connection: close\r\n\r\n"
        output.write(header.toByteArray(StandardCharsets.UTF_8))
        output.write(body)
        output.flush()
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

    private fun escapeJson(str: String): String {
        return str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "")
            .replace("\t", "\\t")
    }

    // ==========================================
    // EMBEDDED HTML PORTALS
    // ==========================================

    private fun getCompanionHtml(): String = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
    <title>Skaldoria Presenter Remote</title>
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
        body { background: #0b0f19; color: #f1f5f9; display: flex; flex-direction: column; min-height: 100vh; padding: 14px; }
        .header { display: flex; justify-content: space-between; align-items: center; padding-bottom: 12px; border-bottom: 1px solid #1e293b; }
        .logo { font-weight: 800; font-size: 1.1rem; color: #38bdf8; display: flex; align-items: center; gap: 6px; }
        .timer-badge { background: #1e293b; padding: 6px 12px; border-radius: 20px; font-family: monospace; font-size: 1.1rem; color: #38bdf8; font-weight: bold; border: 1px solid rgba(56,189,248,0.2); }
        .tabs { display: flex; gap: 8px; margin-top: 12px; }
        .tab-btn { flex: 1; padding: 10px; background: #131b2e; border: 1px solid #1e293b; border-radius: 10px; color: #94a3b8; font-weight: bold; cursor: pointer; font-size: 0.85rem; }
        .tab-btn.active { background: #0284c7; color: #fff; border-color: #38bdf8; }
        .slide-card { background: #131b2e; border: 1px solid #1e293b; border-radius: 14px; padding: 16px; margin: 12px 0; flex: 1; display: flex; flex-direction: column; }
        .slide-meta { font-size: 0.8rem; color: #38bdf8; font-weight: 700; text-transform: uppercase; letter-spacing: 1px; }
        .slide-title { font-size: 1.25rem; font-weight: 700; color: #ffffff; margin: 6px 0; }
        .notes-box { background: #0a0e1a; border-radius: 10px; padding: 12px; margin-top: 8px; flex: 1; overflow-y: auto; max-height: 220px; font-size: 0.95rem; line-height: 1.5; color: #cbd5e1; border: 1px solid #1e293b; }
        .controls { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-bottom: 10px; }
        .btn { padding: 18px; border: none; border-radius: 12px; font-size: 1.2rem; font-weight: bold; cursor: pointer; transition: transform 0.1s; display: flex; align-items: center; justify-content: center; user-select: none; }
        .btn:active { transform: scale(0.96); }
        .btn-prev { background: #1e293b; color: #f1f5f9; }
        .btn-next { background: #0284c7; color: #ffffff; }
        .actions-row { display: flex; gap: 8px; margin-bottom: 12px; }
        .btn-action { flex: 1; padding: 10px; border-radius: 8px; border: 1px solid #1e293b; background: #131b2e; color: #94a3b8; font-size: 0.85rem; font-weight: 600; cursor: pointer; }
        .btn-action.active { background: #dc2626; color: white; border-color: #ef4444; }
        .qa-list { display: flex; flex-direction: column; gap: 8px; margin-top: 10px; flex: 1; overflow-y: auto; }
        .qa-item { background: #0a0e1a; border: 1px solid #1e293b; border-radius: 10px; padding: 12px; display: flex; justify-content: space-between; align-items: center; }
        .qa-text { font-size: 0.95rem; color: #f1f5f9; font-weight: 500; }
        .qa-author { font-size: 0.75rem; color: #64748b; margin-top: 4px; }
        .qa-votes { background: #0369a1; color: white; padding: 4px 8px; border-radius: 12px; font-size: 0.8rem; font-weight: bold; }
    </style>
</head>
<body>
    <div class="header">
        <div class="logo">⚡ SKALDORIA <span style="font-size:0.7rem;color:#64748b;font-weight:600;">${com.skaldoria.BuildInfo.DISPLAY_VERSION}</span></div>
        <div class="timer-badge" id="timer">00:00</div>
    </div>

    <div class="tabs">
        <button class="tab-btn active" id="tab-remote-btn" onclick="showTab('remote')">📱 Slide Remote</button>
        <button class="tab-btn" id="tab-qa-btn" onclick="showTab('qa')">💬 Live Q&A (<span id="qa-count">0</span>)</button>
    </div>

    <div id="tab-remote" style="display:flex; flex-direction:column; flex:1;">
        <div class="slide-card">
            <div class="slide-meta" id="slide-index">Slide 1 of 1</div>
            <div class="slide-title" id="slide-title">Loading presentation...</div>
            <div class="notes-box" id="notes">No notes for this slide.</div>
        </div>

        <div class="controls">
            <button class="btn btn-prev" onclick="sendAction('prev')">◀ PREV</button>
            <button class="btn btn-next" onclick="sendAction('next')">NEXT ▶</button>
        </div>

        <div class="actions-row">
            <button class="btn-action" id="btn-blackout" onclick="sendAction('blackout')">⚫ Blackout (B)</button>
            <button class="btn-action" id="btn-whiteout" onclick="sendAction('whiteout')">⚪ Whiteout (W)</button>
            <button class="btn-action" onclick="sendAction('toggleTimer')">⏱ Timer</button>
        </div>
    </div>

    <div id="tab-qa" style="display:none; flex-direction:column; flex:1;">
        <div class="slide-card" style="margin-top:12px;">
            <div class="slide-meta">AUDIENCE QUESTIONS</div>
            <div class="qa-list" id="qa-container">
                <div style="text-align:center; color:#64748b; padding:20px;">No questions submitted yet.</div>
            </div>
        </div>
    </div>

    <script>
        let currentTab = 'remote';

        // SEC-2: the pairing QR points at /remote?t=<token>. Capture it, then strip it from
        // the visible address bar so the credential is not shoulder-surfed or leaked via a
        // shared screenshot. Sent as a custom header, which forces a CORS preflight on any
        // cross-origin attempt and thereby closes the residual CSRF gap.
        const SESSION_TOKEN = new URLSearchParams(location.search).get('t') || '';
        if (SESSION_TOKEN && history.replaceState) {
            history.replaceState(null, '', location.pathname);
        }

        function authFetch(url, options) {
            const opts = options || {};
            opts.headers = Object.assign({}, opts.headers, { 'X-Skaldoria-Token': SESSION_TOKEN });
            return fetch(url, opts);
        }

        // SEC-1: every value that originates from an audience device or the deck
        // author is inserted with textContent, never innerHTML. Do not "simplify"
        // these builders back into template literals — that reintroduces stored XSS.
        function el(tag, className, text) {
            const node = document.createElement(tag);
            if (className) node.className = className;
            if (text !== undefined && text !== null) node.textContent = text;
            return node;
        }

        function showTab(t) {
            currentTab = t;
            document.getElementById('tab-remote').style.display = t === 'remote' ? 'flex' : 'none';
            document.getElementById('tab-qa').style.display = t === 'qa' ? 'flex' : 'none';
            document.getElementById('tab-remote-btn').className = t === 'remote' ? 'tab-btn active' : 'tab-btn';
            document.getElementById('tab-qa-btn').className = t === 'qa' ? 'tab-btn active' : 'tab-btn';
        }

        async function sendAction(action) {
            try {
                await authFetch('/api/action?action=' + encodeURIComponent(action), { method: 'POST' });
                pollState();
            } catch(e) {}
        }

        async function dismissQa(id) {
            try {
                await authFetch('/api/qa/dismiss?id=' + encodeURIComponent(id), { method: 'POST' });
                pollState();
            } catch(e) {}
        }

        function formatTime(sec) {
            const m = Math.floor(sec / 60).toString().padStart(2, '0');
            const s = (sec % 60).toString().padStart(2, '0');
            return m + ':' + s;
        }

        async function pollState() {
            try {
                const res = await authFetch('/api/state');
                if (!res.ok) return;
                const data = await res.json();
                document.getElementById('slide-index').innerText = 'Slide ' + (data.currentSlideIndex + 1) + ' of ' + data.totalSlides;
                document.getElementById('slide-title').innerText = data.title;
                document.getElementById('timer').innerText = formatTime(data.elapsedSeconds);
                
                const notesContainer = document.getElementById('notes');
                notesContainer.textContent = '';
                if (data.notes && data.notes.length > 0) {
                    data.notes.forEach(n => {
                        const p = el('p', null, n);
                        p.style.marginBottom = '8px';
                        notesContainer.appendChild(p);
                    });
                } else {
                    const empty = el('span', null, 'No notes for this slide.');
                    empty.style.color = '#64748b';
                    empty.style.fontStyle = 'italic';
                    notesContainer.appendChild(empty);
                }

                document.getElementById('btn-blackout').className = data.isBlackout ? 'btn-action active' : 'btn-action';
                document.getElementById('btn-whiteout').className = data.isWhiteout ? 'btn-action active' : 'btn-action';

                // Q&A
                const qaCount = data.questions ? data.questions.length : 0;
                document.getElementById('qa-count').innerText = qaCount;
                const qaContainer = document.getElementById('qa-container');
                qaContainer.textContent = '';
                if (data.questions && data.questions.length > 0) {
                    data.questions.forEach(q => {
                        const item = el('div', 'qa-item');

                        const left = el('div');
                        left.appendChild(el('div', 'qa-text', q.text));
                        left.appendChild(el('div', 'qa-author', q.author));
                        item.appendChild(left);

                        const right = el('div');
                        right.style.cssText = 'display:flex; align-items:center; gap:8px;';
                        right.appendChild(el('span', 'qa-votes', '👍 ' + q.upvotes));

                        // Listener closure instead of an inline onclick attribute: a quote
                        // in q.id can no longer break out into markup.
                        const dismiss = el('button', null, 'Dismiss');
                        dismiss.style.cssText = 'background:#dc2626; color:white; border:none; border-radius:6px; padding:6px 10px; font-size:0.75rem; cursor:pointer;';
                        dismiss.addEventListener('click', () => dismissQa(q.id));
                        right.appendChild(dismiss);

                        item.appendChild(right);
                        qaContainer.appendChild(item);
                    });
                } else {
                    const empty = el('div', null, 'No questions submitted yet.');
                    empty.style.cssText = 'text-align:center; color:#64748b; padding:20px;';
                    qaContainer.appendChild(empty);
                }
            } catch(e) {}
        }

        setInterval(pollState, 700);
        pollState();
    </script>
</body>
</html>
""".trimIndent()

    private fun getAudienceHtml(): String = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
    <title>Skaldoria Audience Portal</title>
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
        body { background: #0b0f19; color: #f1f5f9; display: flex; flex-direction: column; min-height: 100vh; padding: 16px; }
        .header { text-align: center; padding-bottom: 16px; border-bottom: 1px solid #1e293b; }
        .brand { font-size: 1.3rem; font-weight: 800; color: #38bdf8; letter-spacing: 1px; }
        .subtitle { font-size: 0.85rem; color: #94a3b8; margin-top: 4px; }
        .card { background: #131b2e; border: 1px solid #1e293b; border-radius: 16px; padding: 18px; margin: 16px 0; }
        .card-title { font-size: 1.1rem; font-weight: 700; color: #ffffff; margin-bottom: 12px; display: flex; align-items: center; gap: 8px; }
        .poll-opt-btn { width: 100%; padding: 14px 16px; margin-bottom: 10px; background: #1e293b; border: 1px solid #334155; border-radius: 12px; color: #f8fafc; font-size: 1rem; font-weight: 600; text-align: left; cursor: pointer; display: flex; justify-content: space-between; align-items: center; transition: all 0.15s; }
        .poll-opt-btn:active { background: #0284c7; transform: scale(0.98); }
        .poll-opt-btn.voted { background: #0369a1; border-color: #38bdf8; }
        .input-group { margin-bottom: 12px; }
        .input-text { width: 100%; padding: 12px; background: #0a0e1a; border: 1px solid #1e293b; border-radius: 10px; color: #f1f5f9; font-size: 0.95rem; }
        .btn-submit { width: 100%; padding: 14px; background: #0284c7; border: none; border-radius: 10px; color: white; font-weight: bold; font-size: 1rem; cursor: pointer; }
        .btn-submit:active { transform: scale(0.98); }
        .qa-feed { display: flex; flex-direction: column; gap: 10px; }
        .qa-card { background: #0a0e1a; border: 1px solid #1e293b; border-radius: 12px; padding: 14px; display: flex; justify-content: space-between; align-items: center; }
        .qa-content { flex: 1; padding-right: 12px; }
        .qa-author-label { font-size: 0.75rem; color: #38bdf8; font-weight: bold; margin-bottom: 4px; }
        .qa-text-body { font-size: 0.95rem; color: #e2e8f0; line-height: 1.4; }
        .upvote-btn { background: #1e293b; border: 1px solid #334155; color: #38bdf8; padding: 8px 12px; border-radius: 20px; font-size: 0.9rem; font-weight: bold; cursor: pointer; display: flex; align-items: center; gap: 4px; }
        .upvote-btn:active { background: #0284c7; color: white; }
    </style>
</head>
<body>
    <div class="header">
        <div class="brand">⚡ SKALDORIA AUDIENCE</div>
        <div class="subtitle">Live Presentation Interactive Portal · ${com.skaldoria.BuildInfo.DISPLAY_VERSION}</div>
    </div>

    <!-- Active Poll Section -->
    <div class="card" id="poll-section" style="display:none;">
        <div class="card-title">📊 Live Poll: <span id="poll-question"></span></div>
        <div id="poll-options"></div>
        <div id="poll-voted-msg" style="display:none; color:#34d399; font-size:0.85rem; font-weight:bold; margin-top:8px; text-align:center;">✓ Your vote was recorded!</div>
    </div>

    <!-- Ask Question Section -->
    <div class="card">
        <div class="card-title">💬 Ask the Speaker</div>
        <div class="input-group">
            <input type="text" class="input-text" id="qa-author-input" placeholder="Your Name or Handle (optional)">
        </div>
        <div class="input-group">
            <textarea class="input-text" id="qa-text-input" rows="3" placeholder="Type your question for the presenter..."></textarea>
        </div>
        <button class="btn-submit" onclick="submitQuestion()">Submit Question</button>
    </div>

    <!-- Live Questions Feed -->
    <div class="card">
        <div class="card-title">🔥 Audience Questions</div>
        <div class="qa-feed" id="qa-feed">
            <div style="text-align:center; color:#64748b; padding:16px;">No questions submitted yet. Be the first!</div>
        </div>
    </div>

    <script>
        let currentActiveSlide = -1;
        let lastVotedSlide = -1;
        let lastVotedOption = -1;

        // SEC-1: every value that originates from another audience device is inserted
        // with textContent, never innerHTML. Do not "simplify" these builders back into
        // template literals — that reintroduces stored XSS.
        function el(tag, className, text) {
            const node = document.createElement(tag);
            if (className) node.className = className;
            if (text !== undefined && text !== null) node.textContent = text;
            return node;
        }

        async function votePoll(optIndex) {
            try {
                await fetch('/api/poll/vote?slideIndex=' + currentActiveSlide + '&optionIndex=' + optIndex, { method: 'POST' });
                lastVotedSlide = currentActiveSlide;
                lastVotedOption = optIndex;
                document.getElementById('poll-voted-msg').style.display = 'block';
                pollState();
            } catch(e) {}
        }

        async function submitQuestion() {
            const author = document.getElementById('qa-author-input').value.trim();
            const text = document.getElementById('qa-text-input').value.trim();
            if (!text) return;

            try {
                await fetch('/api/qa/submit?author=' + encodeURIComponent(author) + '&text=' + encodeURIComponent(text), { method: 'POST' });
                document.getElementById('qa-text-input').value = '';
                pollState();
            } catch(e) {}
        }

        async function upvoteQuestion(id) {
            try {
                await fetch('/api/qa/upvote?id=' + encodeURIComponent(id), { method: 'POST' });
                pollState();
            } catch(e) {}
        }

        async function pollState() {
            try {
                const res = await fetch('/api/state');
                if (!res.ok) return;
                const data = await res.json();
                currentActiveSlide = data.currentSlideIndex;

                // Check poll
                const pollSec = document.getElementById('poll-section');
                if (data.poll && data.poll.hasPoll) {
                    pollSec.style.display = 'block';
                    document.getElementById('poll-question').textContent = data.poll.question;

                    const isVotedOnThisSlide = (lastVotedSlide === currentActiveSlide);
                    document.getElementById('poll-voted-msg').style.display = isVotedOnThisSlide ? 'block' : 'none';

                    const optsContainer = document.getElementById('poll-options');
                    optsContainer.textContent = '';
                    data.poll.options.forEach(opt => {
                        const voted = isVotedOnThisSlide && lastVotedOption === opt.index;
                        const btn = el('button', voted ? 'poll-opt-btn voted' : 'poll-opt-btn');
                        btn.appendChild(el('span', null, opt.text));
                        const count = el('span', null, opt.votes + ' votes');
                        count.style.cssText = 'font-size:0.85rem; color:#94a3b8;';
                        btn.appendChild(count);
                        btn.addEventListener('click', () => votePoll(opt.index));
                        optsContainer.appendChild(btn);
                    });
                } else {
                    pollSec.style.display = 'none';
                }

                // Questions
                const feed = document.getElementById('qa-feed');
                feed.textContent = '';
                if (data.questions && data.questions.length > 0) {
                    data.questions.forEach(q => {
                        const card = el('div', 'qa-card');

                        const content = el('div', 'qa-content');
                        content.appendChild(el('div', 'qa-author-label', q.author));
                        content.appendChild(el('div', 'qa-text-body', q.text));
                        card.appendChild(content);

                        // Listener closure instead of an inline onclick attribute: a quote
                        // in q.id can no longer break out into markup.
                        const upvote = el('button', 'upvote-btn', '👍 ' + q.upvotes);
                        upvote.addEventListener('click', () => upvoteQuestion(q.id));
                        card.appendChild(upvote);

                        feed.appendChild(card);
                    });
                } else {
                    const empty = el('div', null, 'No questions submitted yet. Be the first!');
                    empty.style.cssText = 'text-align:center; color:#64748b; padding:16px;';
                    feed.appendChild(empty);
                }
            } catch(e) {}
        }

        setInterval(pollState, 1000);
        pollState();
    </script>
</body>
</html>
""".trimIndent()
}
