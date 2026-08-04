package com.skaldoria.remote

import com.skaldoria.state.PresentationState
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.util.concurrent.Executors

/**
 * Lightweight zero-dependency embedded web server that allows controlling
 * presentation slides, reading speaker notes, and monitoring timers from any
 * smartphone or tablet browser on the local Wi-Fi.
 */
object RemoteCompanionServer {

    private var server: HttpServer? = null
    private val executor = Executors.newFixedThreadPool(4)

    var currentPort: Int = 8888
        private set

    fun isRunning(): Boolean = server != null

    @Synchronized
    fun start(state: PresentationState, port: Int = 8888): String {
        stop()
        currentPort = port
        val s = HttpServer.create(InetSocketAddress(port), 0)
        s.executor = executor

        s.createContext("/", CompanionWebHandler(state))
        s.createContext("/api/state", StateApiHandler(state))
        s.createContext("/api/action", ActionApiHandler(state))

        s.start()
        server = s

        val localIp = getLocalIpAddress()
        return "http://$localIp:$port"
    }

    @Synchronized
    fun stop() {
        server?.stop(0)
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

    private class StateApiHandler(private val state: PresentationState) : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            val current = state.currentSlide
            val notesJson = (current?.notes ?: emptyList()).joinToString(prefix = "[", postfix = "]") { n ->
                "\"${n.replace("\"", "\\\"").replace("\n", " ")}\""
            }

            val title = (current?.title ?: "Untitled").replace("\"", "\\\"").replace("\n", " ")
            val json = """
                {
                    "currentSlideIndex": ${state.currentSlideIndex},
                    "totalSlides": ${state.slides.size},
                    "title": "$title",
                    "notes": $notesJson,
                    "elapsedSeconds": ${state.elapsedSeconds},
                    "isTimerRunning": ${state.isTimerRunning},
                    "isBlackout": ${state.isBlackoutActive},
                    "isWhiteout": ${state.isWhiteoutActive}
                }
            """.trimIndent()

            val bytes = json.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
            exchange.responseHeaders.set("Access-Control-Allow-Origin", "*")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
    }

    private class ActionApiHandler(private val state: PresentationState) : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            val query = exchange.requestURI.query ?: ""
            val params = query.split("&").associate {
                val p = it.split("=")
                if (p.size == 2) p[0] to p[1] else p[0] to ""
            }

            when (params["action"]) {
                "next" -> state.nextSlide()
                "prev" -> state.previousSlide()
                "jump" -> params["index"]?.toIntOrNull()?.let { state.goToSlide(it) }
                "blackout" -> state.toggleBlackout()
                "whiteout" -> state.toggleWhiteout()
                "toggleTimer" -> state.toggleTimer()
                "resetTimer" -> state.resetTimer()
            }

            val response = """{"status":"ok"}""".toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
            exchange.responseHeaders.set("Access-Control-Allow-Origin", "*")
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
    }

    private class CompanionWebHandler(private val state: PresentationState) : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            val html = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
    <title>Skaldoria Remote Companion</title>
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
        body { background: #0b0f19; color: #f1f5f9; display: flex; flex-direction: column; min-height: 100vh; padding: 16px; }
        .header { display: flex; justify-content: space-between; align-items: center; padding-bottom: 12px; border-bottom: 1px solid #1e293b; }
        .logo { font-weight: 800; font-size: 1.1rem; color: #38bdf8; }
        .timer-badge { background: #1e293b; padding: 6px 12px; border-radius: 20px; font-family: monospace; font-size: 1rem; color: #38bdf8; font-weight: bold; }
        .slide-card { background: #131b2e; border: 1px solid #1e293b; border-radius: 14px; padding: 16px; margin: 16px 0; flex: 1; display: flex; flex-direction: column; }
        .slide-meta { font-size: 0.8rem; color: #64748b; font-weight: 700; text-transform: uppercase; letter-spacing: 1px; }
        .slide-title { font-size: 1.3rem; font-weight: 700; color: #ffffff; margin: 8px 0; }
        .notes-box { background: #0a0e1a; border-radius: 10px; padding: 12px; margin-top: 10px; flex: 1; overflow-y: auto; max-height: 280px; font-size: 0.95rem; line-height: 1.5; color: #cbd5e1; border: 1px solid #1e293b; }
        .controls { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-bottom: 12px; }
        .btn { padding: 18px; border: none; border-radius: 12px; font-size: 1.2rem; font-weight: bold; cursor: pointer; transition: transform 0.1s; display: flex; align-items: center; justify-content: center; user-select: none; }
        .btn:active { transform: scale(0.96); }
        .btn-prev { background: #1e293b; color: #f1f5f9; }
        .btn-next { background: #0284c7; color: #ffffff; }
        .actions-row { display: flex; gap: 8px; }
        .btn-action { flex: 1; padding: 10px; border-radius: 8px; border: 1px solid #1e293b; background: #131b2e; color: #94a3b8; font-size: 0.85rem; font-weight: 600; cursor: pointer; }
        .btn-action.active { background: #dc2626; color: white; border-color: #ef4444; }
    </style>
</head>
<body>
    <div class="header">
        <div class="logo">⚡ SKALDORIA</div>
        <div class="timer-badge" id="timer">00:00</div>
    </div>

    <div class="slide-card">
        <div class="slide-meta" id="slide-index">Slide 1 of 1</div>
        <div class="slide-title" id="slide-title">Loading...</div>
        <div class="notes-box" id="notes">No speaker notes for this slide.</div>
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

    <script>
        async function sendAction(action) {
            try {
                await fetch('/api/action?action=' + action);
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
            } catch(e) {}
        }

        setInterval(pollState, 600);
        pollState();
    </script>
</body>
</html>
            """.trimIndent()

            val bytes = html.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.set("Content-Type", "text/html; charset=utf-8")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
    }
}
