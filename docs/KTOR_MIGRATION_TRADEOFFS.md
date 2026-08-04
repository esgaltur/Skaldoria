# Ktor vs. hand-rolled sockets — evaluation

**Created:** 2026-08-04
**Status:** Analysis only — no decision taken. Would supersede parts of [ADR-001](ADR_COMPANION_SERVER_ARCHITECTURE.md) as ADR-002 if accepted.
**Question asked:** would moving `RemoteCompanionServer` to Ktor eliminate the auth / CORS / routing code and make things simpler?

---

## TL;DR

**For auth and CORS specifically: no.** That premise doesn't hold. Hand-rolled token auth is ~8 lines; the Ktor equivalent is ~6 lines of plugin config plus a routing restructure. Net saving ≈ 2 lines. CORS is worse than a wash — we *deleted* CORS in SEC-3 because we don't want it, so Ktor's CORS plugin has nothing to replace.

**For three other things: yes, materially.** Ktor deletes EXP-4 (a real bug), makes SEC-4 moot, and turns SEC-5 into a plugin. It also closes robustness gaps we haven't ticketed yet — including one found while writing this doc (no chunked-encoding support).

**But it only removes ~30% of the file.** 41% is embedded HTML that Ktor cannot touch.

**The decision actually hinges on one product question:** do you ever want to replace the 700ms polling with WebSockets/SSE? If yes, migrate *before* hand-writing the rate limiter and body parser. If no, stay on sockets — the remaining hand-rolled work is about 75 lines.

**My lean: stay, finish the security work, revisit when you want push.** It's a close call, not an obvious one.

---

## 1. Does Ktor simplify auth? (measured, not hand-waved)

This is the core of the question, so here is the actual side-by-side for SEC-2.

**Hand-rolled, in the current architecture:**

```kotlin
private fun isAuthorized(params: Map<String, String>, headers: Map<String, String>): Boolean {
    val supplied = headers["x-skaldoria-token"] ?: params["token"] ?: return false
    return MessageDigest.isEqual(supplied.toByteArray(), sessionToken.toByteArray())
}

// one guard in routeRequest, next to the existing WRITE_ENDPOINTS check:
if (path in PRESENTER_ENDPOINTS && !isAuthorized(params, headers)) {
    sendErrorResponse(output, "Unauthorized", 401); return
}
```

**Ktor:**

```kotlin
install(Authentication) {
    bearer("presenter") {
        authenticate { cred -> if (cred.token == sessionToken) UserIdPrincipal("presenter") else null }
    }
}
routing {
    authenticate("presenter") {
        post("/api/action") { /* ... */ }
        post("/api/qa/dismiss") { /* ... */ }
    }
    // audience scope outside the authenticate block
}
```

Roughly the same size. Ktor's auth plugin earns its keep with OAuth, JWT rotation, multiple providers, or session cookies — **none of which apply to one shared LAN token**. The scope split (presenter vs. audience) is genuinely more readable as nested routing blocks, but that's a legibility win, not a code-volume win.

**Verdict: auth is not a reason to migrate.**

## 2. Does Ktor simplify CORS?

No — the question is moot. SEC-3 **removed** all `Access-Control-Allow-*` headers because the wildcard was the CSRF vector. Ktor's `CORS` plugin exists to *add* CORS. There is nothing for it to replace, and its permissive defaults (`anyHost()`) are an invitation to reintroduce exactly the bug we just fixed.

**Verdict: CORS is not a reason to migrate.**

## 3. What Ktor would actually replace — measured

`RemoteCompanionServer.kt` is 882 lines. Broken down by what a Ktor migration would do to each region:

| Region | Lines | Share | Ktor replaces it? |
|---|---:|---:|---|
| Embedded HTML portals (`getCompanionHtml`, `getAudienceHtml`) | 360 | 41% | **No** — untouched |
| API handlers (poll, Q&A, parking lot, state JSON) | 151 | 17% | **No** — business logic |
| HTTP request parsing + routing dispatch | 134 | 15% | **Yes** |
| Response writers + utilities | 75 | 9% | **Yes** (mostly) |
| Lifecycle: executor, listener thread, socket accept loop | ~55 | 6% | **Yes** |
| `getLocalIpAddress`, port fallback, misc | ~107 | 12% | **No** — still needed |

**≈ 264 lines (30%) removed. The single largest block — 360 lines of embedded HTML — is unaffected.**

That is the number that should drive the decision, and it's smaller than "replace the server" intuitively suggests. Ktor does not shrink this file dramatically because the file is mostly not an HTTP server.

## 4. Where Ktor genuinely wins

These are real and they map directly onto open plan items:

| Plan item | Hand-rolled cost | With Ktor |
|---|---|---|
| **EXP-4** — `Content-Length` bytes read as chars, hangs 10s on non-ASCII | ~15 lines to fix properly | `call.receiveText()` — **bug cannot exist** |
| **SEC-4** — unbounded thread pool, connection-per-poll | Bounded pool + keep-alive, ~40 lines | CIO engine handles it — **item disappears** |
| **SEC-5** — rate limiting | Token bucket, ~50 lines | `install(RateLimit)` — ~6 lines |
| **PRF-1** — HTTP threads mutating Compose state | Marshal to UI dispatcher | Same work, but coroutine-native so `withContext` is idiomatic |

Plus robustness gaps that are **not yet in the plan**, all free with Ktor:

- **No chunked transfer-encoding support.** `handleClientSocket` only honours `Content-Length`. A client sending `Transfer-Encoding: chunked` gets its body silently mis-parsed. Browsers rarely chunk small POSTs, so this is latent rather than active — but it is a real hole in a hand-written parser. *(New finding from this analysis — should be added to the plan regardless of the Ktor decision.)*
- No request-line or header-count size limits (memory exhaustion via a huge request line).
- No slowloris protection beyond a blunt 10s `SO_TIMEOUT`.

There is an honest counterpoint to ADR-001 here. The ADR lists "**Zero external dependencies, immune to third-party CVEs**" as a security advantage. That framing is one-sided: **we are writing our own HTTP parser, and we have already shipped a bug in it** (EXP-4) plus the three gaps above. Trading a maintained parser's CVE stream for our own unaudited parser's defect stream is not automatically a security win.

## 5. Where ADR-001's numbers don't hold up

ADR-001 rejected Ktor **today** (2026-08-04), so this is not a stale decision — but three of its figures are materially overstated:

| ADR-001 claim | Reality |
|---|---|
| "+12 MB to +18 MB in final installer" | **~3–4 MB.** Measured from this machine's Gradle cache: the shared transitive tree a CIO server needs — `ktor-http` (392 KB), `ktor-utils` (322 KB), `ktor-io` (238 KB), `ktor-network` (186 KB), `ktor-http-cio` (119 KB), `ktor-events` (6 KB) — totals **1.26 MB**. Add `ktor-server-core` (~1.5–2 MB, not cached so estimated), `ktor-server-cio` (~0.3 MB) and `slf4j-api` (~70 KB). Coroutines are **already a project dependency**, so they cost nothing new. The ADR overstates by roughly 4×. |
| "~150ms–300ms cold startup latency" | Largely irrelevant. The server is **lazily started** by `toggleRemoteServer()` when the user turns the remote on — it is not on the app's boot path. A one-time 150ms on a deliberate user action is invisible. This is the weakest argument in the ADR. |
| "~20 MB – 35 MB additional heap" | Overstated for a CIO server with a handful of LAN connections; realistically single-digit MB. |

Two ADR-001 arguments **do** hold up:

- **JPMS / jlink portability.** Real for `jpackage` native distributions across six formats. Not a blocker (Compose Desktop bundles a full JRE by default), but genuine friction.
- **SLF4J noise.** Ktor pulls `slf4j-api`; with no backend bound it prints `SLF4J: No providers were found` to stderr. Cosmetic, but visible in a polished desktop product, and needs a deliberate no-op binding.

One thing this environment surfaced: **Maven Central is behind TLS interception here** (`PKIX path building failed` on a clean resolve). The main project builds only because its dependencies are already cached. Adding a new dependency has real friction in this specific setup — worth verifying before committing to a migration.

## 6. What de-risks a migration

The SEC-1 / SEC-3 regression tests written today are **black-box HTTP calls** — they open a socket, send a request, assert on status codes, headers, and body. They contain no reference to `ServerSocket`, executors, or any internal type. **They would survive a Ktor migration nearly unchanged**, and would independently verify that the migration preserved security behaviour.

That is a meaningful de-risking factor and an argument for migrating sooner rather than later if you're going to do it at all — the safety net exists *now*.

## 7. Options

### Option A — Stay on sockets, finish the plan *(recommended)*

- **Cost:** SEC-2 ~8 lines, SEC-5 ~50 lines, EXP-4 ~15 lines, SEC-4 ~40 lines. Plus the three unticketed robustness gaps. Call it ~150 lines total.
- **Pro:** ADR-001 stands; zero new dependencies; no churn on code hardened today; no TLS/proxy friction.
- **Con:** we keep maintaining a hand-written HTTP parser with known gaps; polling architecture stays.

### Option B — Migrate to Ktor now, before writing the remaining server code

- **Cost:** rewrite ~264 lines of plumbing; port 4 tests (mostly mechanical); add SLF4J no-op binding; verify `jpackage` output on all six target formats; resolve the Maven Central TLS issue.
- **Pro:** EXP-4, SEC-4 and the robustness gaps evaporate; SEC-5 becomes a plugin; opens the door to SSE/WebSockets; existing black-box tests validate the move.
- **Con:** ~3–4 MB installer growth; ADR-001 reversed within a day; 41% of the file (HTML) unimproved; migration churn mid-remediation.

### Option C — Stay now, migrate when you want push

- Finish Phase 1 on sockets. Revisit Ktor as ADR-002 at the point WebSockets/SSE becomes a real requirement — which **ADR-001 itself names as the evolution path** (Decision 3).
- **Pro:** sequences the dependency cost to the moment it buys something hand-rolling genuinely can't.
- **Con:** SEC-4/SEC-5/EXP-4 get written by hand and then thrown away if push arrives later. That is the ~105 lines of waste you'd be accepting.

## 8. The pivot question

Everything reduces to this:

> **Will Skaldoria ever replace 700ms polling with real-time push?**

The current design opens a new TCP connection per client per poll (`Connection: close`). At 50 audience devices that's ~70 connections/sec — the load profile SEC-4 exists to contain. If conference-scale audience interaction is a real product goal, push is not optional, and hand-rolling RFC 6455 frame masking is exactly the kind of work Ktor should be doing for you.

- **Push is a real goal** → **Option B.** Migrate before writing SEC-4/SEC-5/EXP-4 by hand.
- **Polling is fine; the remote is a convenience feature** → **Option A/C.** The socket server is adequate and ~150 lines finishes it.

## 9. Recommendation

**Option A/C — stay on sockets for now.** Reasons, in order of weight:

1. The stated motivation (auth, CORS) is the one place Ktor **doesn't** help. Migrating for that reason would be migrating for a benefit that isn't there.
2. Only 30% of the file goes away; the 41% that dominates it is HTML.
3. Remaining hand-rolled work is ~150 lines against ~264 lines rewritten plus packaging re-verification across six installer formats.
4. Reversing an ADR one day old warrants a stronger trigger than we currently have.

**Regardless of the decision, do these now:**

- Add **chunked transfer-encoding** handling (or an explicit `411 Length Required` rejection) to the plan — it's a real gap found here.
- Add request-line and header-count limits.
- Record this evaluation's outcome so ADR-001 isn't re-litigated a third time.

**Revisit immediately if** push/WebSockets gets prioritised — at that point Option B is clearly correct and the sunk hand-rolled work should be abandoned rather than extended.

---

## 10. Addendum — what about the Ktor **client**?

Asked separately. Short answer: **no, and it doesn't touch auth or CORS at all** — those are server-side concerns, and CORS was deliberately removed in SEC-3.

**The app currently has no HTTP client of any kind.** The only client-shaped code is browser-side `fetch()` in the two embedded portals and `HttpURLConnection` in `RemoteCompanionServerTest`. Three candidate uses, none of which favour Ktor Client:

| Candidate | Verdict |
|---|---|
| The portals' `fetch()` calls | **Not applicable.** That is JavaScript executing in the audience's browser. Reaching it with Ktor Client would mean compiling Kotlin/JS for a ~200-line page. |
| `HttpURLConnection` in tests | **Neutral, mildly negative.** The four security tests are deliberately black-box — no reference to `ServerSocket` or any internal type — which is what lets them survive a future Ktor *server* migration (§6). Swapping the client library adds a test dependency for ergonomics only. |
| Loading slide images | **The real gap — but Ktor Client is the wrong layer.** See below. |

### Why image loading doesn't want Ktor Client

This investigation surfaced a genuine defect, now tracked as **COR-10**: `SlideElement.Image` is parsed, drives layout classification, and is emitted to HTML export — but **never rendered on a slide**. `SplitTextMediaSlide.kt:113-142` draws a placeholder icon and prints the URL as text. No image decoding exists anywhere in `desktopMain`.

Fixing that needs, in order:

1. **Local file rendering** — the common case (`![](diagram.png)` beside the deck). Needs `File.readBytes()` + Skia decode. **No HTTP at all.**
2. **Remote URL fetch** — `java.net.URL.openStream()` on `Dispatchers.IO` with timeout and size cap. ~10 lines, zero dependencies.
3. **Async loading, caching, placeholder/error states** — these are the parts that are actually hard, and they are *Compose* concerns, not HTTP concerns. Ktor Client provides none of them.

If a library is wanted for step 3, the correct choice is a **Compose image loader** (Coil 3, which supports Compose Multiplatform desktop, or Kamel) — not an HTTP client. Notably, Kamel uses Ktor Client *underneath*: picking Ktor Client directly means selecting the transport when the problem is the loader.

**Recommendation:** implement COR-10 with local-file support plus `URL.openStream()`. Revisit a Compose image loader only if caching and load-state UX become a real requirement. Ktor Client does not belong in this project on current evidence.

---

## Change log

| Date | Change |
|---|---|
| 2026-08-04 | Initial evaluation. Measured Ktor footprint from local Gradle cache; corrected three ADR-001 figures; found the chunked-encoding gap. |
| 2026-08-04 | Added §10 on Ktor **client**. Found COR-10: images parsed and exported but never rendered in-app. |
