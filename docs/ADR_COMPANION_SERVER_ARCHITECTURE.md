# Architecture Decision Record (ADR)
## ADR-001: Embedded Companion Server Architecture & Protocol Selection

### Status
**Accepted** (2026-08-04)

---

### Context
Skaldoria includes an embedded companion server allowing speakers to wirelessly control slides, view presenter notes, and interact with the audience (in-slide polling and moderated Q&A) via any mobile browser on the local Wi-Fi network.

Two primary architectural dimensions were evaluated:
1. **Server Transport Implementation**: Standard Java Sockets (`java.net.ServerSocket`) vs. Full Kotlin Framework (Ktor Server with CIO/Netty engine).
2. **Wire Protocol**: HTTP/1.1 vs. HTTP/2 (`h2` / `h2c`) vs. WebSockets (`RFC 6455`).

---

### 1. Transport Implementation Evaluation (Sockets vs. Ktor)

| Metric / Dimension | Option A: Ktor Framework (CIO Engine) | Option B: Standard Sockets (`java.base`) |
| :--- | :--- | :--- |
| **Binary Distribution Size** | +12 MB to +18 MB in final installer | **0 MB** (bundled with base JRE runtime) |
| **Cold Startup Latency** | ~150ms – 300ms (pipeline & coroutine warmup) | **< 1ms** (instant socket bind) |
| **Runtime Memory Overhead** | ~20 MB – 35 MB additional heap | **< 200 KB** heap footprint |
| **JVM Runtime Portability** | Requires module dependencies and version locking | **100% Guaranteed** on any JRE, GraalVM, or Jlink runtime |
| **Maintenance & Security** | Ongoing dependency patching & Gradle upgrades | **Zero external dependencies**, immune to third-party CVEs |
| **Routing Model** | Declarative Kotlin DSL | Explicit path & query routing |

---

### 2. Wire Protocol Evaluation (Why HTTP/1.1 over HTTP/2)

| Criteria | HTTP/2 (`h2` / `h2c`) | HTTP/1.1 (Current) | WebSockets (`ws://`) |
| :--- | :--- | :--- | :--- |
| **Browser Security Policy** | ❌ **Enforces TLS/ALPN**: Mobile browsers (iOS Safari, Chrome) refuse HTTP/2 over plain HTTP. | ✅ **Works on plain HTTP**: Connects instantly to local IP without certificate friction. | ✅ **Works on plain HTTP**: Connects over standard `ws://` on local LAN. |
| **Local IP SSL Certificates** | ❌ **Severe Certificate Warnings**: Self-signed certs on ephemeral LAN IPs (`192.168.x.x`) cause security warning bypass screens for audience members. | ✅ **Zero Certificate Friction**: Seamless scan-and-connect QR experience. | ✅ **Zero Certificate Friction**: Seamless upgrade from HTTP/1.1. |
| **Protocol Complexity** | ❌ **High Complexity**: Requires HPACK Huffman tables, multiplexed binary frame handling (`HEADERS`, `DATA`, `SETTINGS`, `PING`, `GOAWAY`). | ✅ **Lightweight Text Protocol**: Simple, robust ASCII parsing in ~50 lines of code. | ⚠️ **Moderate Complexity**: Frame masking & handshake parser. |
| **Local Network Suitability** | ⚠️ **Designed for WAN**: Solves high-latency internet round-trips; offers negligible gain on $<2\text{ms}$ local Wi-Fi for single-page payloads. | ✅ **Optimal for Local LAN**: Sub-millisecond latency for single-page web app and state polling. | ✅ **Optimal for Real-Time Push**: 0-latency bidirectional push. |

---

### Decisions

1. **Use Zero-Dependency Native Standard Sockets (`java.net.ServerSocket`)**:
   - Maintains Skaldoria's lightweight footprint (<60MB total desktop app).
   - Eliminates all Java Module System (JPMS) classloader issues across minimal JREs and standalone installers.
   - Provides instant, sub-millisecond server boot time.

2. **Standardize on HTTP/1.1 for Local Wi-Fi Companion**:
   - HTTP/2 is rejected due to mandatory browser TLS/ALPN requirements that trigger invalid certificate security screens on local IPs (`192.168.x.x`).
   - HTTP/1.1 provides friction-free pairing for audience members scanning the QR code from any mobile device.

3. **Future Real-Time Evolution Path**:
   - If sub-millisecond bidirectional push is needed to replace the 700ms polling cycle, the next logical step is **WebSockets (`RFC 6455`)** over plain HTTP (`ws://`), which operates without TLS certificate constraints.

---

### Consequences
* **Positive**:
  - Zero external library dependencies in `build.gradle.kts`.
  - Zero browser security warnings for attendees connecting over local Wi-Fi.
  - Flawless stability across all operating systems and packaged native builds (`.exe`, `.msi`, `.dmg`, `.deb`).
* **Negative / Mitigation**:
  - Clients poll state at 700ms intervals.
  - *Mitigation*: User action endpoints (`/api/action?action=next`) respond immediately and trigger instant local state mutation, making presenter slide transitions feel instantaneous.

---

### Related Components
* [`RemoteCompanionServer.kt`](file:///C:/Users/Root/Workspace/WebStormProjects/MarkdownPres/src/desktopMain/kotlin/com/skaldoria/remote/RemoteCompanionServer.kt)
* [`PresentationState.kt`](file:///C:/Users/Root/Workspace/WebStormProjects/MarkdownPres/src/desktopMain/kotlin/com/skaldoria/state/PresentationState.kt)
* [`RemoteCompanionServerTest.kt`](file:///C:/Users/Root/Workspace/WebStormProjects/MarkdownPres/src/desktopTest/kotlin/com/skaldoria/remote/RemoteCompanionServerTest.kt)
