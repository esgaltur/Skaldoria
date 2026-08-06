package com.skaldoria.remote

import com.skaldoria.core.json.Json
import com.skaldoria.core.models.SlideElement
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
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

    /** SEC-4: ceiling on concurrent request handlers. */
    private const val MAX_WORKER_THREADS = 16

    /** How long a client may hold a connection open without completing a request. */
    private const val SOCKET_TIMEOUT_MS = 10_000

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
    fun start(deck: DeckControl, preferredPort: Int = 8888): String {
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
                            handleClientSocket(clientSocket, deck)
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

    private fun handleClientSocket(socket: Socket, deck: DeckControl) {
        try {
            socket.soTimeout = SOCKET_TIMEOUT_MS
            val inputStream = java.io.BufferedInputStream(socket.getInputStream())
            val outputStream = socket.getOutputStream()

            // F-09: reading and validating the request is HttpRequestParser's job; this
            // method now only owns the socket and the response.
            when (val result = HttpRequestParser.parse(inputStream)) {
                is ParseResult.Incomplete -> return

                is ParseResult.Rejected ->
                    sendErrorResponse(outputStream, result.message, result.statusCode)

                is ParseResult.Ok -> {
                    val request = result.request
                    if (request.method == "OPTIONS") {
                        sendNoContentResponse(outputStream)
                        return
                    }

                    // SEC-5: on a LAN each audience device has its own address, so the peer
                    // address is a workable per-device identity for both rate limiting and
                    // one-ballot-per-device.
                    val clientKey = socket.inetAddress?.hostAddress ?: "unknown"
                    routeRequest(request, outputStream, deck, clientKey)
                }
            }
        } catch (_: Exception) {
            // Socket or I/O error on client disconnect
        } finally {
            try {
                socket.close()
            } catch (_: Exception) {}
        }
    }

    /**
     * The complete request surface, with each route's access-control policy attached.
     *
     * SEC-8: this table replaces `PRESENTER_ENDPOINTS`, `WRITE_ENDPOINTS` and a `when (path)`
     * dispatch — three places that had to be kept in agreement by hand, where forgetting one
     * failed **silently** and left an endpoint unauthenticated or unthrottled. A route cannot
     * be added now without stating its method and scope, and [routeRequest] derives the
     * policy from them. `RouteTableSecurityTest` asserts over the whole table.
     *
     * `/` and `/remote` serve the same portal: the pairing QR points at `/remote`, and `/`
     * is kept so a hand-typed host:port still lands somewhere useful.
     */
    val routes: List<Route> = listOf(
        // The portals carry no secrets and are inert without a token, so they are served
        // unconditionally rather than creating a pairing dead end.
        Route("/", HttpMethod.GET, RouteScope.PUBLIC) { sendHtmlResponse(it.output, PortalAssets.presenterHtml()) },
        Route("/remote", HttpMethod.GET, RouteScope.PUBLIC) { sendHtmlResponse(it.output, PortalAssets.presenterHtml()) },
        Route("/audience", HttpMethod.GET, RouteScope.PUBLIC) { sendHtmlResponse(it.output, PortalAssets.audienceHtml()) },

        // SEC-2: a read for everyone, but speaker notes are filtered by `authorized`.
        Route("/api/state", HttpMethod.GET, RouteScope.PUBLIC) {
            handleStateApi(it.output, it.deck, includeNotes = it.authorized)
        },

        // Audience scope: mutates without a token, by design. Rate-limited (SEC-5).
        Route("/api/poll/vote", HttpMethod.POST, RouteScope.AUDIENCE) {
            handlePollVoteApi(it.output, it.params, it.deck, it.clientKey)
        },
        Route("/api/qa/submit", HttpMethod.POST, RouteScope.AUDIENCE) {
            handleQaSubmitApi(it.output, it.params, it.deck)
        },
        Route("/api/qa/upvote", HttpMethod.POST, RouteScope.AUDIENCE) {
            handleQaUpvoteApi(it.output, it.params, it.deck)
        },
        Route("/api/parking-lot/add", HttpMethod.POST, RouteScope.AUDIENCE) {
            handleParkingLotAddApi(it.output, it.params, it.deck)
        },

        // Presenter scope: drives the deck or moderates. Session token required (SEC-2).
        Route("/api/action", HttpMethod.POST, RouteScope.PRESENTER) {
            handleActionApi(it.output, it.params, it.deck)
        },
        Route("/api/qa/dismiss", HttpMethod.POST, RouteScope.PRESENTER) {
            handleQaDismissApi(it.output, it.params, it.deck)
        }
    )

    private val routesByPath: Map<String, Route> = routes.associateBy { it.path }

    private fun routeRequest(
        request: HttpRequest,
        output: OutputStream,
        deck: DeckControl,
        clientKey: String
    ) {
        val route = routesByPath[request.path]
        if (route == null) {
            sendErrorResponse(output, "Endpoint not found: ${request.path}", 404)
            return
        }

        // SEC-3: a cross-origin page can only issue a GET (a bare `<img src>` or a link), so
        // refusing it on anything that mutates closes the drive-by path.
        if (route.method.name != request.method) {
            sendErrorResponse(output, "This endpoint requires ${route.method.name}", 405)
            return
        }

        // SEC-5: throttle writes only — polling /api/state must stay free.
        if (route.mutating && !allowRequest(clientKey)) {
            sendErrorResponse(output, "Too many requests", 429)
            return
        }

        val authorized = isAuthorized(request.params, request.headers)

        // SEC-2: derived from the route's declared scope, not from set membership.
        if (route.requiresToken && !authorized) {
            sendErrorResponse(output, "Presenter session token required", 401)
            return
        }

        route.handler(
            RequestContext(
                params = request.params,
                deck = deck,
                output = output,
                clientKey = clientKey,
                authorized = authorized
            )
        )
    }

    // ==========================================
    // API IMPLEMENTATIONS
    // ==========================================

    private fun handleStateApi(output: OutputStream, deck: DeckControl, includeNotes: Boolean) {
        try {
            // SEC-2: speaker notes are presenter-only. Audience devices reach this same
            // endpoint, so the field is emitted empty rather than omitted — the portal JS
            // treats "no notes" identically and needs no special case.
            val visibleNotes = if (includeNotes) deck.currentSlideNotes else emptyList()
            val notesJson = visibleNotes.joinToString(prefix = "[", postfix = "]") { n ->
                "\"${escapeJson(n)}\""
            }

            val pollElement = deck.currentSlidePoll
            val pollJson = if (pollElement != null) {
                val votes = deck.audience.votesForSlide(deck.currentSlideIndex)
                val optionsJson = pollElement.options.mapIndexed { idx, opt ->
                    val count = votes[idx] ?: 0
                    """{"index": $idx, "text": "${escapeJson(opt)}", "votes": $count}"""
                }.joinToString(prefix = "[", postfix = "]")
                """{"hasPoll": true, "slideIndex": ${deck.currentSlideIndex}, "question": "${escapeJson(pollElement.question)}", "options": $optionsJson}"""
            } else {
                """{"hasPoll": false}"""
            }

            // PRF-1: a consistent snapshot. This used to be a bare `.toList()` wrapped in a
            // try/catch that swallowed the resulting ConcurrentModificationException.
            val questionsList = deck.audience.snapshot()
            val questionsJson = questionsList.joinToString(prefix = "[", postfix = "]") { q ->
                // The id is generated, not user-supplied — escaped anyway so no field in
                // this object depends on an assumption about its contents.
                """{"id": "${escapeJson(q.id)}", "author": "${escapeJson(q.author)}", "text": "${escapeJson(q.text)}", "upvotes": ${q.upvotes}, "isAnswered": ${q.isAnswered}}"""
            }

            val title = escapeJson(deck.currentSlideTitle)
            val json = """
                {
                    "currentSlideIndex": ${deck.currentSlideIndex},
                    "totalSlides": ${deck.totalSlides},
                    "title": "$title",
                    "notes": $notesJson,
                    "elapsedSeconds": ${deck.elapsedSeconds},
                    "isTimerRunning": ${deck.isTimerRunning},
                    "isBlackout": ${deck.isBlackoutActive},
                    "isWhiteout": ${deck.isWhiteoutActive},
                    "poll": $pollJson,
                    "questions": $questionsJson
                }
            """.trimIndent()

            sendJsonResponse(output, json)
        } catch (t: Throwable) {
            sendErrorResponse(output, "Failed to retrieve presentation state: ${t.message}")
        }
    }

    private fun handleActionApi(output: OutputStream, params: Map<String, String>, deck: DeckControl) {
        try {
            val action = params["action"] ?: params["cmd"]
            // PRF-1: every mutation from an HTTP worker thread goes through a snapshot.
            deck.applyFromBackgroundThread {
                when (action) {
                    "next" -> deck.nextSlide()
                    "prev" -> deck.previousSlide()
                    "jump" -> (params["index"] ?: params["slideIndex"] ?: params["slide"])?.toIntOrNull()?.let { deck.goToSlide(it) }
                    "blackout" -> deck.toggleBlackout()
                    "whiteout" -> deck.toggleWhiteout()
                    "toggleTimer" -> deck.toggleTimer()
                    "resetTimer" -> deck.resetTimer()
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
        deck: DeckControl,
        clientKey: String
    ) {
        try {
            val slideIdx = (params["slideIndex"] ?: params["slide"])?.toIntOrNull() ?: deck.currentSlideIndex
            val optionIdx = (params["optionIndex"] ?: params["option"])?.toIntOrNull()

            if (optionIdx != null && optionIdx >= 0) {
                deck.applyFromBackgroundThread { deck.audience.recordVote(slideIdx, optionIdx, voterKey = clientKey) }
                sendJsonResponse(output, """{"status":"ok", "votedSlide": $slideIdx, "votedOption": $optionIdx}""")
            } else {
                sendJsonResponse(output, """{"status":"error", "message":"Invalid option"}""", 400)
            }
        } catch (t: Throwable) {
            sendErrorResponse(output, "Poll voting failed: ${t.message}")
        }
    }

    private fun handleQaSubmitApi(output: OutputStream, params: Map<String, String>, deck: DeckControl) {
        try {
            val author = params["author"]?.trim()?.ifBlank { "Anonymous" } ?: "Anonymous"
            val text = params["text"]?.trim() ?: ""

            if (text.isNotBlank()) {
                val q = deck.applyFromBackgroundThread { deck.audience.submit(author, text) }
                sendJsonResponse(output, """{"status":"ok", "id":"${q.id}"}""")
            } else {
                sendJsonResponse(output, """{"status":"error", "message":"Empty question"}""", 400)
            }
        } catch (t: Throwable) {
            sendErrorResponse(output, "Q&A submission failed: ${t.message}")
        }
    }

    private fun handleQaUpvoteApi(output: OutputStream, params: Map<String, String>, deck: DeckControl) {
        try {
            val id = params["id"]
            if (!id.isNullOrBlank()) {
                deck.applyFromBackgroundThread { deck.audience.upvote(id) }
                sendJsonResponse(output, """{"status":"ok"}""")
            } else {
                sendJsonResponse(output, """{"status":"error", "message":"Missing question id"}""", 400)
            }
        } catch (t: Throwable) {
            sendErrorResponse(output, "Upvote failed: ${t.message}")
        }
    }

    private fun handleQaDismissApi(output: OutputStream, params: Map<String, String>, deck: DeckControl) {
        try {
            val id = params["id"]
            if (!id.isNullOrBlank()) {
                deck.applyFromBackgroundThread { deck.audience.dismiss(id) }
                sendJsonResponse(output, """{"status":"ok"}""")
            } else {
                sendJsonResponse(output, """{"status":"error", "message":"Missing question id"}""", 400)
            }
        } catch (t: Throwable) {
            sendErrorResponse(output, "Dismiss failed: ${t.message}")
        }
    }

    private fun handleParkingLotAddApi(output: OutputStream, params: Map<String, String>, deck: DeckControl) {
        try {
            val text = params["question"] ?: params["text"] ?: ""
            val author = params["author"]
            if (text.isNotBlank()) {
                deck.applyFromBackgroundThread {
                    deck.parkQuestion(text.trim(), author?.trim()?.ifBlank { null })
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

    /**
     * COR-12: delegates to the single JSON encoder.
     *
     * This was a local five-replacement chain missing every C0 control character other than
     * `\n`, `\r` and `\t` — which RFC 8259 requires to be escaped. Audience text arrives
     * URL-decoded, so `%01` reached the response raw and made `/api/state` unparseable for
     * every polling device. Do not reintroduce a local escape routine here; twelve call
     * sites means one encoder.
     */
    private fun escapeJson(str: String): String = Json.escape(str)

}
