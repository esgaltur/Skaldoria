package com.skaldoria.remote

import com.skaldoria.core.models.SlideElement
import com.skaldoria.state.PresentationState
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

/**
 * Embedded zero-dependency HTTP server for wireless speaker remote controls
 * and real-time live audience interaction (in-slide polling and moderated Q&A).
 */
object RemoteCompanionServer {

    private var server: HttpServer? = null
    private val executor = Executors.newFixedThreadPool(4)

    var currentPort: Int = 8888
        private set

    fun isRunning(): Boolean = server != null

    /**
     * Starts the embedded server with automatic port-fallback if the preferred port is occupied.
     */
    @Synchronized
    fun start(state: PresentationState, preferredPort: Int = 8888): String {
        stop()

        var boundServer: HttpServer? = null
        var boundPort = preferredPort

        // Try preferred port and up to 20 consecutive ports
        for (portCandidate in preferredPort..(preferredPort + 20)) {
            try {
                val s = HttpServer.create(InetSocketAddress(portCandidate), 0)
                boundServer = s
                boundPort = portCandidate
                break
            } catch (_: Exception) {
                // Port occupied or restricted, try next
            }
        }

        // If specific ports failed, bind to ephemeral port (0)
        if (boundServer == null) {
            val s = HttpServer.create(InetSocketAddress(0), 0)
            boundServer = s
            boundPort = s.address.port
        }

        boundServer.executor = executor

        // Register Web and API Contexts
        boundServer.createContext("/", CompanionWebHandler(state))
        boundServer.createContext("/remote", CompanionWebHandler(state))
        boundServer.createContext("/audience", AudienceWebHandler(state))
        boundServer.createContext("/api/state", StateApiHandler(state))
        boundServer.createContext("/api/action", ActionApiHandler(state))
        boundServer.createContext("/api/poll/vote", PollVoteApiHandler(state))
        boundServer.createContext("/api/qa/submit", QaSubmitApiHandler(state))
        boundServer.createContext("/api/qa/upvote", QaUpvoteApiHandler(state))
        boundServer.createContext("/api/qa/dismiss", QaDismissApiHandler(state))

        boundServer.start()
        server = boundServer
        currentPort = boundPort

        val localIp = getLocalIpAddress()
        return "http://$localIp:$boundPort"
    }

    @Synchronized
    fun stop() {
        try {
            server?.stop(0)
        } catch (_: Exception) {}
        server = null
    }

    fun getLocalIpAddress(): String {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            var candidate: String? = null
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr.isSiteLocalAddress && addr.hostAddress.contains('.')) {
                        return addr.hostAddress
                    } else if (!addr.isLoopbackAddress && addr.hostAddress.contains('.')) {
                        candidate = addr.hostAddress
                    }
                }
            }
            candidate ?: InetAddress.getLocalHost().hostAddress ?: "127.0.0.1"
        } catch (_: Exception) {
            "127.0.0.1"
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

    // ==========================================
    // API HANDLERS
    // ==========================================

    private class StateApiHandler(private val state: PresentationState) : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            val current = state.currentSlide
            val notesJson = (current?.notes ?: emptyList()).joinToString(prefix = "[", postfix = "]") { n ->
                "\"${escapeJson(n)}\""
            }

            // Check if current slide has a poll element
            val pollElement = current?.elements?.filterIsInstance<SlideElement.Poll>()?.firstOrNull()
            val pollJson = if (pollElement != null) {
                val votes = state.getVotesForSlide(state.currentSlideIndex)
                val optionsJson = pollElement.options.mapIndexed { idx, opt ->
                    val count = votes[idx] ?: 0
                    """{"index": $idx, "text": "${escapeJson(opt)}", "votes": $count}"""
                }.joinToString(prefix = "[", postfix = "]")
                """{"hasPoll": true, "question": "${escapeJson(pollElement.question)}", "options": $optionsJson}"""
            } else {
                """{"hasPoll": false}"""
            }

            val questionsJson = state.audienceQuestions.joinToString(prefix = "[", postfix = "]") { q ->
                """{"id": "${q.id}", "author": "${escapeJson(q.author)}", "text": "${escapeJson(q.text)}", "upvotes": ${q.upvotes}, "isAnswered": ${q.isAnswered}}"""
            }

            val title = escapeJson(current?.title ?: "Untitled")
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

            sendJsonResponse(exchange, json)
        }
    }

    private class ActionApiHandler(private val state: PresentationState) : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            val params = parseQueryParams(exchange.requestURI.query)
            val action = params["action"] ?: params["cmd"]
            when (action) {
                "next" -> state.nextSlide()
                "prev" -> state.previousSlide()
                "jump" -> (params["index"] ?: params["slideIndex"] ?: params["slide"])?.toIntOrNull()?.let { state.goToSlide(it) }
                "blackout" -> state.toggleBlackout()
                "whiteout" -> state.toggleWhiteout()
                "toggleTimer" -> state.toggleTimer()
                "resetTimer" -> state.resetTimer()
            }
            sendJsonResponse(exchange, """{"status":"ok"}""")
        }
    }

    private class PollVoteApiHandler(private val state: PresentationState) : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            val params = parseQueryParams(exchange.requestURI.query)
            val slideIdx = (params["slideIndex"] ?: params["slide"])?.toIntOrNull() ?: state.currentSlideIndex
            val optionIdx = (params["optionIndex"] ?: params["option"])?.toIntOrNull()

            if (optionIdx != null && optionIdx >= 0) {
                state.recordVote(slideIdx, optionIdx)
                sendJsonResponse(exchange, """{"status":"ok", "votedOption": $optionIdx}""")
            } else {
                sendJsonResponse(exchange, """{"status":"error", "message":"Invalid option"}""", 400)
            }
        }
    }

    private class QaSubmitApiHandler(private val state: PresentationState) : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            val params = parseQueryParams(exchange.requestURI.query)
            val author = params["author"] ?: "Anonymous"
            val text = params["text"] ?: ""

            if (text.isNotBlank()) {
                val q = state.submitQuestion(author, text)
                sendJsonResponse(exchange, """{"status":"ok", "id":"${q.id}"}""")
            } else {
                sendJsonResponse(exchange, """{"status":"error", "message":"Empty question"}""", 400)
            }
        }
    }

    private class QaUpvoteApiHandler(private val state: PresentationState) : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            val params = parseQueryParams(exchange.requestURI.query)
            val id = params["id"]
            if (!id.isNullOrBlank()) {
                state.upvoteQuestion(id)
                sendJsonResponse(exchange, """{"status":"ok"}""")
            } else {
                sendJsonResponse(exchange, """{"status":"error"}""", 400)
            }
        }
    }

    private class QaDismissApiHandler(private val state: PresentationState) : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            val params = parseQueryParams(exchange.requestURI.query)
            val id = params["id"]
            if (!id.isNullOrBlank()) {
                state.dismissQuestion(id)
                sendJsonResponse(exchange, """{"status":"ok"}""")
            } else {
                sendJsonResponse(exchange, """{"status":"error"}""", 400)
            }
        }
    }

    private fun sendJsonResponse(exchange: HttpExchange, json: String, statusCode: Int = 200) {
        val bytes = json.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
        exchange.responseHeaders.set("Access-Control-Allow-Origin", "*")
        exchange.sendResponseHeaders(statusCode, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun escapeJson(str: String): String {
        return str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "")
            .replace("\t", "\\t")
    }

    // ==========================================
    // WEB PORTALS (HTML / JS / CSS)
    // ==========================================

    /**
     * Speaker's mobile remote control portal.
     */
    private class CompanionWebHandler(private val state: PresentationState) : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            val html = """
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
        .tab-btn { flex: 1; padding: 10px; background: #131b2e; border: 1px solid #1e293b; border-radius: 10px; color: #94a3b8; font-weight: bold; cursor: pointer; font-size: 0.9rem; }
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
        <div class="logo">⚡ SKALDORIA</div>
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

        function showTab(t) {
            currentTab = t;
            document.getElementById('tab-remote').style.display = t === 'remote' ? 'flex' : 'none';
            document.getElementById('tab-qa').style.display = t === 'qa' ? 'flex' : 'none';
            document.getElementById('tab-remote-btn').className = t === 'remote' ? 'tab-btn active' : 'tab-btn';
            document.getElementById('tab-qa-btn').className = t === 'qa' ? 'tab-btn active' : 'tab-btn';
        }

        async function sendAction(action) {
            try {
                await fetch('/api/action?action=' + action);
                pollState();
            } catch(e) {}
        }

        async function dismissQa(id) {
            try {
                await fetch('/api/qa/dismiss?id=' + encodeURIComponent(id));
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
                const res = await fetch('/api/state');
                const data = await res.json();
                document.getElementById('slide-index').innerText = 'Slide ' + (data.currentSlideIndex + 1) + ' of ' + data.totalSlides;
                document.getElementById('slide-title').innerText = data.title;
                document.getElementById('timer').innerText = formatTime(data.elapsedSeconds);
                
                const notesContainer = document.getElementById('notes');
                if (data.notes && data.notes.length > 0) {
                    notesContainer.innerHTML = data.notes.map(n => '<p style="margin-bottom:8px;">' + n + '</p>').join('');
                } else {
                    notesContainer.innerHTML = '<span style="color:#64748b;font-style:italic;">No notes for this slide.</span>';
                }

                document.getElementById('btn-blackout').className = data.isBlackout ? 'btn-action active' : 'btn-action';
                document.getElementById('btn-whiteout').className = data.isWhiteout ? 'btn-action active' : 'btn-action';

                // Q&A
                const qaCount = data.questions ? data.questions.length : 0;
                document.getElementById('qa-count').innerText = qaCount;
                const qaContainer = document.getElementById('qa-container');
                if (data.questions && data.questions.length > 0) {
                    qaContainer.innerHTML = data.questions.map(q => `
                        <div class="qa-item">
                            <div>
                                <div class="qa-text">${'$'}{q.text}</div>
                                <div class="qa-author">${'$'}{q.author}</div>
                            </div>
                            <div style="display:flex; align-items:center; gap:8px;">
                                <span class="qa-votes">👍 ${'$'}{q.upvotes}</span>
                                <button onclick="dismissQa('${'$'}{q.id}')" style="background:#dc2626; color:white; border:none; border-radius:6px; padding:6px 10px; font-size:0.75rem; cursor:pointer;">Dismiss</button>
                            </div>
                        </div>
                    `).join('');
                } else {
                    qaContainer.innerHTML = '<div style="text-align:center; color:#64748b; padding:20px;">No questions submitted yet.</div>';
                }
            } catch(e) {}
        }

        setInterval(pollState, 700);
        pollState();
    </script>
</body>
</html>
            """.trimIndent()

            val bytes = html.toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.set("Content-Type", "text/html; charset=utf-8")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
    }

    /**
     * Audience interaction web portal (voting on active polls + submitting Q&A).
     */
    private class AudienceWebHandler(private val state: PresentationState) : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            val html = """
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
        <div class="subtitle">Live Presentation Interactive Portal</div>
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
            <input type="text" class="input-text" id="qa-author-input" placeholder="Your Name or Twitter Handle (optional)">
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
        let lastVotedSlide = -1;
        let lastVotedOption = -1;

        async function votePoll(optIndex) {
            try {
                await fetch('/api/poll/vote?optionIndex=' + optIndex);
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
                await fetch('/api/qa/submit?author=' + encodeURIComponent(author) + '&text=' + encodeURIComponent(text));
                document.getElementById('qa-text-input').value = '';
                pollState();
            } catch(e) {}
        }

        async function upvoteQuestion(id) {
            try {
                await fetch('/api/qa/upvote?id=' + encodeURIComponent(id));
                pollState();
            } catch(e) {}
        }

        async function pollState() {
            try {
                const res = await fetch('/api/state');
                const data = await res.json();

                // Check poll
                const pollSec = document.getElementById('poll-section');
                if (data.poll && data.poll.hasPoll) {
                    pollSec.style.display = 'block';
                    document.getElementById('poll-question').innerText = data.poll.question;

                    const optsContainer = document.getElementById('poll-options');
                    optsContainer.innerHTML = data.poll.options.map(opt => `
                        <button class="poll-opt-btn ${'$'}{lastVotedOption === opt.index ? 'voted' : ''}" onclick="votePoll(${'$'}{opt.index})">
                            <span>${'$'}{opt.text}</span>
                            <span style="font-size:0.85rem; color:#94a3b8;">${'$'}{opt.votes} votes</span>
                        </button>
                    `).join('');
                } else {
                    pollSec.style.display = 'none';
                }

                // Questions
                const feed = document.getElementById('qa-feed');
                if (data.questions && data.questions.length > 0) {
                    feed.innerHTML = data.questions.map(q => `
                        <div class="qa-card">
                            <div class="qa-content">
                                <div class="qa-author-label">${'$'}{q.author}</div>
                                <div class="qa-text-body">${'$'}{q.text}</div>
                            </div>
                            <button class="upvote-btn" onclick="upvoteQuestion('${'$'}{q.id}')">👍 ${'$'}{q.upvotes}</button>
                        </div>
                    `).join('');
                } else {
                    feed.innerHTML = '<div style="text-align:center; color:#64748b; padding:16px;">No questions submitted yet. Be the first!</div>';
                }
            } catch(e) {}
        }

        setInterval(pollState, 1000);
        pollState();
    </script>
</body>
</html>
            """.trimIndent()

            val bytes = html.toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.set("Content-Type", "text/html; charset=utf-8")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
    }
}
