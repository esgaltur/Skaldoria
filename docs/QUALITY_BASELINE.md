# Quality Baseline

**Version:** 1.3.0 · **Last reviewed:** 2026-08-06 · **Suite:** 583 tests, 0 failures

This document is the reference for the invariants this codebase holds, established during a
systematic pre-release review of `src/desktopMain`. Every entry has a stable identifier, and
those identifiers appear in code comments beside the logic they constrain.

**Read this when:** a comment cites an identifier such as `SEC-2` or `MMD-6`; you are changing
parsing, layout, rendering, or the companion server; or you are about to simplify something whose
shape looks arbitrary. Several implementations here are deliberately non-obvious, and the
rationale for each is recorded below precisely so the reasoning is not lost and re-derived
incorrectly.

**Identifiers are permanent.** Do not renumber them. If an invariant is retired, mark it and keep
the identifier.

---

## Contents

- [How to use this document](#how-to-use-this-document)
- [Index](#index)
- [Companion server — access control and input handling](#companion-server--access-control-and-input-handling)
- [Diagram engine](#diagram-engine)
- [Document model and editing](#document-model-and-editing)
- [Rendering and layout](#rendering-and-layout)
- [Export](#export)
- [Presentation, delivery and connectivity](#presentation-delivery-and-connectivity)
- [Concurrency and performance](#concurrency-and-performance)
- [Design decisions worth preserving](#design-decisions-worth-preserving)
- [Verification approach](#verification-approach)
- [Known limitations](#known-limitations)

---

## How to use this document

Each entry states:

| Field | Meaning |
| :--- | :--- |
| **Invariant** | What must remain true. Present tense. |
| **Implementation** | How it is achieved, where it lives. |
| **Rationale** | Why this approach and not the obvious alternative. The part worth reading. |
| **Guard** | The test that fails if the invariant is broken. |

Entries without a **Rationale** are self-evident and need none.

---

## Index

| ID | Area | Invariant | Guard |
| :--- | :--- | :--- | :--- |
| SEC-1 | Companion | Audience-supplied text never reaches the DOM as markup | `RemoteCompanionServerTest` |
| SEC-2 | Companion | Presenter-scope routes require the session token; notes are presenter-only | `RemoteCompanionServerTest` |
| SEC-3 | Companion | State-changing endpoints are POST-only; no CORS headers are emitted | `RemoteCompanionServerTest` |
| SEC-4 | Companion | Worker pool is bounded | — |
| SEC-5 | Companion | One ballot per device; audience input is rate-limited and bounded | `AudienceLimitsTest` |
| SEC-6 | Projects | Manifest paths resolve inside the project root, on read and write | `DeckProjectManagerTest` |
| SEC-7 | Companion | Request line, header count and body size are capped; chunked encoding is refused | `RemoteCompanionServerTest` |
| OVF-1 | Rendering | Fit-to-content wraps intrinsically-sized content only | `SlideRenderingTest` |
| OVF-2 | Diagrams | Superseded by MMD-1 | — |
| MMD-1 | Diagrams | Flowchart layout follows graph topology, not source order | `FlowchartLayoutEngineTest` |
| MMD-2 | Diagrams | Sequence diagrams render with lifelines and a time axis | `SlideRenderingTest` |
| MMD-3 | Diagrams | Sequence parsing preserves participants, arrow kinds, ordering and nesting | `SequenceDiagramParserTest` |
| MMD-4 | Diagrams | An edge label attaches to the edge it belongs to | `SubgraphLayoutTest` |
| MMD-5 | Diagrams | Every edge in a chain is captured | `MermaidParserTest` |
| MMD-6 | Diagrams | `((circle))` is matched before `(round)` | `MermaidParserTest` |
| MMD-7 | Diagrams | `{{hexagon}}` is matched before `{diamond}`; mid-link labels are parsed | `MermaidParserTest` |
| MMD-9 | Diagrams | `[(cylinder)]` is matched before `[rect]` | `MermaidParserTest` |
| MMD-10 | Diagrams | Subgraph frames enclose only their members and never overlap | `SubgraphLayoutTest` |
| COR-1 | Editing | Slide boundaries come from the parser | `SlideDocumentTest` |
| COR-2 | Editing | Structural edits in project mode are written to the owning file | `SlideDocumentTest` |
| COR-3 | Editing | Slide-to-file mapping is derived, not positional | `CompanionDeckTest` |
| COR-4 | Parsing | A metric requires a unit | `CharacterizationTest` |
| COR-5 | Parsing | Heading markers never reach rendered text | `CharacterizationTest` |
| COR-6 | State | Audience question identifiers are collision-free | `AudienceSessionTest` |
| COR-7 | Projects | Manifest values are JSON-escaped | `DeckProjectManagerTest` |
| COR-8 | Config | Configuration writes are atomic | — |
| COR-9 | Projects | Slide files sort in natural order | `DeckProjectManagerTest` |
| COR-10 | Rendering | Images are resolved, loaded and drawn | `ImageResolverTest` |
| COR-11 | Config | Persistence has one injection point; the suite never writes to the real home | `ConfigStorageLocationTest` |
| COR-12 | Companion | JSON string encoding escapes the whole C0 range | `JsonEscapingTest` |
| COR-13 | Search | Slide search covers every `SlideElement` variant | `SlideSearchTest` |
| EXP-1 | Export | Exported colours match the source theme | `CharacterizationTest` |
| EXP-2 | Export | Exported HTML cannot be broken out of by content | `CharacterizationTest` |
| EXP-3 | Export | Every slide element reaches the image export | — |
| EXP-4 | Companion | `Content-Length` is interpreted as bytes | `RemoteCompanionServerTest` |
| EXP-5 | Export | Subprocess output is drained | — |
| EXP-6 | Export | Archive streams close on failure | — |
| PRF-1 | Concurrency | State mutated off the UI thread is applied in a snapshot | — |
| PRF-2 | Performance | Autosave is debounced | — |
| PRF-3 | Performance | Search results are cached | `EditorFindAndReplaceTest` |
| PRF-4 | State | Elapsed time derives from a monotonic clock | `PresentationStateTest` |
| PRF-5 | Performance | Export work runs off the UI thread | — |
| DED-1 | Config | The autosave draft is recoverable | `DraftRecoveryTest` |
| DED-2 | Config | UI preferences persist across launches | — |
| DED-3 | Projects | One implementation of slide-file creation | — |
| DED-4 | Diagrams | A shape exists only if the parser can emit it | `MermaidParserTest` |
| DED-5 | State | `remoteServerUrl` is display-only | — |
| DED-6 | State | Application errors and companion errors use separate channels | `ErrorChannelTest` |
| DED-7 | Parsing | Block-rule order is explicit and asserted | `BlockRuleOrderTest` |
| DED-8 | Companion | The portals are assets, and their SEC-1/SEC-2 properties are asserted | `PortalAssetsTest` |
| DED-9 | Companion | The request parser is pure, so SEC-7's caps are unit-tested | `HttpRequestParserTest` |
| SEC-8 | Companion | Every route declares its method and scope; policy is derived from them | `RouteTableSecurityTest` |
| R-1 | Diagrams | Sequence diagrams scale to fit | `SlideRenderingTest` |
| R-2 | Diagrams | The diagram header **and footer** reflect the parsed type | `SlideFooterLabelTest` |
| HUD-1 | Presentation | The HUD can always be brought back without a mouse | `HudVisibilityTest` |
| HUD-2 | Presentation | HUD visibility never changes the slide canvas or its fit scale | `HudVisibilityTest` |
| HUD-3 | Presentation | The progress indicator is not part of the HUD | — |
| DEL-1 | Presentation | Every `SlideTransition` value renders as itself | `TransitionResolverTest` |
| DEL-2 | Presentation | A per-slide `transition:` beats the deck default | `TransitionResolverTest` |
| UND-1 | Editing | Every structural slide edit is undoable | `StructuralUndoTest` |
| UND-2 | Editing | Undo history never crosses a deck boundary | `StructuralUndoTest` |
| UND-3 | Editing | A snapshot captures per-file content, not just the combined markdown | `DeckHistoryTest` |
| LNK-1 | Companion | The advertised address is one the *client* can reach; the default route is a hint, not the authority | `LinkRankingTest` |
| LNK-2 | Companion | An interface is judged by whether it carries a usable address, not by its name | `LinkRankingTest` |
| OUT-1 | Export | An exported deck renders with no network access | `OfflineHtmlExportTest` |
| EDT-1 | Editor | The editor's text is derived from the deck; only *selection* is editor-owned state | `EditorRevealTest` |
| EDT-2 | Editor | A reveal request is published by explicit navigation only, never by caret movement | `EditorRevealTest` |
| EDT-3 | Editor | Slide ⇄ offset mapping derives from `Slide.sourceLineRange`; nothing re-derives boundaries (extends COR-1) | `SlideSourceLocatorTest` |
| EDT-4 | Editor | Every match-navigation action scrolls its match into view — asserted on the **rendered** pane, not on the index | `EditorWorkspaceRenderingTest` |
| EDT-5 | Editor | Selection is clamped to the new text length before it reaches the field | `EditorRevealTest` |
| CLK-1 | Presentation | The key codes an off-the-shelf presenter clicker emits reach a deck command | `PresenterClickerTest` |
| KEY-1 | Presentation | Every window that hosts the deck answers to the whole `DECK` keyboard surface — asserted by sending keys into a real composition, not by resolving the registry | `FullscreenDeckKeyTest` |

---

## Companion server — access control and input handling

The companion binds to the local network and accepts input from untrusted devices. The threat
model is a shared conference network: audience devices are untrusted input sources, and the
presenter's own browser is a potential source of cross-origin requests.

### SEC-1 — Audience text is never markup

**Invariant.** Text originating from an audience device reaches the DOM only via `textContent`.

**Implementation.** Both portals build nodes through a shared `el(tag, className, text)` helper.
Event handlers are attached with `addEventListener`, never inline `onclick`.

**Rationale.** Submitted text is redistributed to the presenter's device and to every other
audience device, so a single submission reaches every participant. Two vectors existed: template
interpolation into `innerHTML`, and identifier interpolation into an inline `onclick` attribute,
where a quote character escapes the attribute. The server deliberately does **not** HTML-escape
the JSON payload: escaping there would double-escape against `textContent` and display entity
references to users. The client-side contract is the control.

**Guard.** `SEC-1 audience input is JSON-escaped and never rendered as markup` — submits a script
payload and asserts no `innerHTML` assignment exists in either portal.

### SEC-2 — Scoped authority

**Invariant.** Presenter-scope routes (`/api/action`, `/api/qa/dismiss`) require the session
token. Speaker notes are returned only to the presenter scope.

**Implementation.** 128-bit `SecureRandom` token, regenerated on `start()` and cleared on
`stop()`, compared with `MessageDigest.isEqual`. Carried in the pairing QR and returned as an
`X-Skaldoria-Token` header. `handleStateApi(includeNotes = authorized)` emits `"notes": []` to
unauthorised clients rather than omitting the field.

**Rationale.** A *custom header* is used rather than a query parameter or cookie because it forces
a CORS preflight, which is what closes cross-origin form submission — POST alone does not. The
notes field stays present but empty so the portal requires no special case. The presenter portal
strips the token from the address bar with `history.replaceState` after reading it, so it is not
captured in a screenshot. The portal HTML itself is served unauthenticated: it holds no secrets
and is inert without a token, and gating it would create a pairing dead end.

**Guard.** Four cases in `RemoteCompanionServerTest`, covering missing and incorrect tokens, note
scoping, per-session regeneration, and the audience URL carrying no token.

### SEC-3 — Method and origin

**Invariant.** State-changing endpoints accept `POST` only. No `Access-Control-Allow-*` header is
emitted.

**Rationale.** Both portals are same-origin and require no CORS. A permissive `Access-Control-Allow-Origin`
allows any page the presenter visits to issue authenticated cross-origin requests, so removing it
is a control rather than a regression. If cross-origin access is ever required, allow-list
explicit origins; do not reintroduce a wildcard.

### SEC-8 — Access control is declared, not remembered

**Invariant.** Every route declares its HTTP method and its [RouteScope]; SEC-2, SEC-3 and SEC-5
are *derived* from that declaration. A route cannot exist without one.

**Implementation.** `RemoteCompanionServer.routes` is a `List<Route>`; `routeRequest` reads
`route.method`, `route.mutating` and `route.requiresToken` rather than consulting any set.

**Rationale.** The policy previously lived in two `Set<String>` literals checked against a third
`when (path)` dispatch. Adding an endpoint took three coordinated edits, and omitting one **failed
silently**: a write endpoint missing from `WRITE_ENDPOINTS` was exempt from both POST-only and
rate limiting; missing from `PRESENTER_ENDPOINTS` it was unauthenticated. Three load-bearing
invariants rested on a human remembering two lists. `AUDIENCE` scope exists to name the case the
two-set arrangement could not express — *mutates state, deliberately without a token* — because
that is the case an omission silently produces.

**Guard.** `RouteTableSecurityTest` iterates the table: every presenter route rejects a missing
token and accepts a valid one, every mutating route refuses `GET`, every declared route is
reachable, and no path is declared twice. A new route is covered the moment it is declared.

### COR-12 — JSON encoding is complete and single-sourced

**Invariant.** All JSON string encoding goes through `core/json/Json`, which escapes the entire
C0 range (`U+0000`–`U+001F`).

**Rationale.** The server's local `escapeJson` handled `\`, `"`, `\n`, `\r` and `\t` only. Audience
text arrives URL-decoded, so `%01` produced a raw `0x01` that survived `trim()` and `take()` and
reached `/api/state` verbatim — reproduced, not theorised. Because both portals poll that endpoint
sub-second and swallow parse failures in `catch(e){}`, one crafted submission stopped **every**
connected device from updating until the question was dismissed. Twelve hand-built JSON strings
were twelve chances to make this mistake; there is now one encoder. Do not reintroduce a local
escape routine. The old routine also *dropped* `\r` rather than escaping it, silently altering
text the speaker was shown.

**Guard.** `JsonEscapingTest` — the whole C0 range at the unit level, plus an end-to-end
submission proving no raw control character reaches the state feed.

### SEC-5 — Ballot integrity

**Invariant.** A device holds one ballot per poll. Re-voting replaces it.

**Implementation.** `pollVotesMap` stores `slideIndex → (voterKey → option)`; counts are derived.

**Rationale.** Storing `option → count` and incrementing cannot express a changed vote, and any
client-side "already voted" flag is advisory only. Keying by voter makes stuffing impossible and
vote-changing natural, from the same data structure.

**Guard.** `AudienceLimitsTest` — twenty submissions from one device count once.

### SEC-7 — Parser limits

**Invariant.** Request line, header count and body size are bounded. Chunked transfer-encoding is
refused with `411`.

**Rationale.** This is a hand-written HTTP parser reading from an untrusted network. It frames
bodies by `Content-Length` only; refusing chunked encoding explicitly is correct, whereas
mis-reading a chunked body silently is not. Recorded because the trade-off of hand-rolling the
parser (see [ADR-001](./ADR_COMPANION_SERVER_ARCHITECTURE.md)) is that these limits are ours to
maintain.

---

## Diagram engine

### MMD-1 — Layout follows topology

**Invariant.** Flowchart node positions derive from the parsed graph, not from source order.

**Implementation.** `FlowchartLayoutEngine` — longest-path layer assignment with cycle breaking,
layer compaction, and barycentre crossing reduction. `FlowchartScene.arrange` converts the layer
model into rectangles; `FlowchartGraphView` draws them.

**Rationale.** Emitting nodes in parse order renders a branch as a queue: the arrows describe a
topology the diagram does not have. Layer compaction exists because breaking a cycle can push a
node's longest path beyond the node count — a three-node loop otherwise occupies four layers.
Compaction also guarantees `layerCount <= nodeCount`.

**Guard.** `FlowchartLayoutEngineTest` — a star fans into one shared layer; cycles terminate.

### MMD-6, MMD-7, MMD-9 — Bracket alternation order

**Invariant.** In `NODE_TOKEN`, longer bracket forms are matched before shorter ones:
`[(cylinder)]` before `[rect]`, `((circle))` before `(round)`, `{{hexagon}}` before `{diamond}`.

**Rationale.** Regex alternation is ordered. With the shorter form first, `A((Round))` matches the
single-paren branch and captures `(Round`, which both corrupts the label and makes the
corresponding shape unreachable. This has been the cause of three separate defects; if a bracket
form is added, place it in length order and add a test asserting the label is clean.

**Guard.** `MermaidParserTest` — one case per bracket form asserting shape *and* label.

### MMD-5 — Chain scanning

**Invariant.** Every edge on a line is captured, including `A --> B --> C`.

**Implementation.** `parseEdgeChain` walks the line as `node (arrow node)*`.

**Rationale.** The intuitive fix — `Regex.findAll` with a two-node pattern — does not work:
matches do not overlap, so the first match consumes `B` and the second edge is lost. A scanner is
required, not a different regex API.

### MMD-10 — Subgraph frames are honest

**Invariant.** A subgraph frame encloses only its own members, and no two frames overlap.

**Implementation.** Membership follows *mention* inside the block, first group winning.
`positionNodes` reserves a cross-axis band per group; `FlowchartScene.arrangeGroups` derives each
frame from its members' bounding box.

**Rationale.** A frame is a bounding box, so it is only truthful if nothing else occupies that
space. Without reserved bands it lied in two ways, both observable: it overlapped a neighbouring
frame, and a group spanning several layers enclosed an unrelated node sitting between its members.
Reserving a band makes the bounding box correct by construction. Membership follows mention rather
than node creation because a subgraph body most often lists identifiers declared earlier —
capturing only newly-created nodes yields empty groups, which are then discarded, and the subgraph
disappears entirely.

Keyword lines (`subgraph`, `end`, `classDef`, `class`, `style`, `linkStyle`, `click`, `direction`)
are consumed before node scanning, because the node scanner claims an identifier as soon as it
matches and would otherwise register keywords as nodes.

**Guard.** `SubgraphLayoutTest` — frames do not overlap, do not enclose non-members, contain their
own members, and a diagram without subgraphs stays on a single row.

---

## Document model and editing

### COR-1 — One definition of a slide boundary

**Invariant.** Slide boundaries come from `Slide.sourceLineRange`, produced by the parser.

**Implementation.** `SlideDocument` performs every structural edit against those ranges.

**Rationale.** Editing previously re-derived boundaries with an independent splitter recognising
only a literal `---`. The parser also splits on `#`/`##` headings and on `-{3,}` rules, so the two
index spaces diverged: a heading-delimited deck presented three slides but one editable chunk.
Any future editing feature must consume the parser's ranges rather than re-deriving them.

**Guard.** `SlideDocumentTest` — delete, move, duplicate and insert across horizontal-rule,
heading, and four-dash decks.

### COR-2, COR-3 — Project mode

**Invariant.** In project mode the per-file content is authoritative; edits are applied to the
owning file. Slide-to-file mapping is derived from parsing each file.

**Rationale.** The combined markdown is a derived artefact. Writing an edit to it leaves
`activeProject` holding stale per-file content, and the next recompile discards the edit.
Positional mapping (`slideFiles[slideIndex]`) holds only while every file contains exactly one
slide; one `---` anywhere shifts every subsequent mapping.

**Known limit.** Moving a slide *between* files is supported only when each file holds one slide;
otherwise it is a no-op rather than a partial write.

### COR-4 — Metric detection requires a unit

**Invariant.** A paragraph is promoted to a metric only if its leading value carries a unit
(`%`, `x`, currency, or a magnitude suffix).

**Rationale.** Matching a bare leading number captures ordinary prose — `2024 Roadmap Overview`
became a full-slide KPI.

### Parking lot — markdown is the system of record

**Invariant.** Every mutation is written to the deck markdown before it is considered applied.
`reconcileFollowUpQuestions` then treats the file as authoritative.

**Implementation.** `MarkdownSlideParser.rewriteFollowUpDirectives` edits directives in place.
Each carries a persisted `id:`.

**Rationale.** This is the load-bearing invariant of the feature, and the reconcile step is only
safe *because* of it: adopting the file wholesale is lossless only when no in-memory state exists
that the file lacks. **If an in-memory-only edit path is ever added, this breaks.** The `id:`
field exists so identity survives a round trip through the file — matching on question text alone
turns a re-wording into a delete plus a create. Directives are rewritten in place so one authored
beside the slide it refers to stays there. Fields after the question are matched by prefix, so
hand-authored directives without an `id:` continue to work.

**Guard.** `ParkingLotDeleteTest` — eleven cases including deletion surviving a subsequent
keystroke and a reload.

### Project classification is validated, not inferred

**Invariant.** A file is treated as a deck project only if it parses as a manifest *and* declares
slides that resolve inside the project root.

**Rationale.** Classifying by file extension is unsafe: it makes every `.json` a manifest, and the
manifest loader adopts sibling `.md` files when a manifest declares none — so opening an unrelated
`package.json` would assemble a deck from whatever markdown shared its directory. Validation, not
inference.

**Guard.** `CompanionDeckTest` — an unrelated JSON beside markdown is not a project; manifests
whose entries escape the root or do not exist are rejected.

---

## Rendering and layout

### OVF-1 — Fit-to-content applies to intrinsically-sized content only

**Invariant.** `FitToCanvas` wraps content that measures to its own natural size. It must **not**
wrap a layout that sizes itself with `Modifier.weight` or `fillMaxHeight`.

**Rationale.** `FitToCanvas` measures with `maxHeight = Constraints.Infinity` to discover natural
height. Compose allocates weighted children **zero** space on an unbounded main axis, so any
layout using `weight(1f)` collapses. Applying it at slide level once produced a title on an
otherwise empty slide for every layout in the application, while the unit suite passed and the
application launched without error. This constraint is stated in the composable's own
documentation; `SlideSurface` carries a comment explaining why it is not used there.

Measurement is width-bounded and height-free deliberately. Measuring unbounded on both axes
prevents text wrapping, so a paragraph reports an enormous natural width and scales to nothing.

A second measurement pass at `width / scale` follows, because uniform scaling shrinks width as
well as height and would otherwise render a full-width list as a narrow centred column.

**Guard.** `SlideRenderingTest` — content pixels must exceed a title-only floor.

### COR-10 — Images

**Invariant.** `![alt](src)` resolves to a local file, a `file:` URL, or an `http(s)` URL, and is
drawn. Anything unresolvable displays the alt text and the reason.

**Rationale.** Resolution is separated from loading (`ImageResolver` / `SlideImage`) so path rules
are testable without filesystem or network access. Other schemes — `data:`, `javascript:` — are
refused at resolution rather than passed to the runtime. The remote size ceiling is enforced
*mid-stream*, so an oversized or endless response is not buffered into memory first. A blank panel
is not an acceptable failure mode: an author needs to discover a broken path while authoring.

**Guard.** `ImageResolverTest` — eleven cases covering each source form and each refusal.

---

## Export

### EXP-1 — Colour conversion

**Invariant.** Exported colours are read via component accessors or `toArgb()`.

**Rationale.** `Color.value` packs channels into the **high** bits of a `ULong`. Reading the low
32 bits yields the colour space identifier and alpha, not the colour. Every colour in an exported
deck was wrong while the in-application rendering, which used the component accessors, was
correct.

### EXP-2 — Export escaping

**Invariant.** `escapeHtml` escapes `'` in addition to `& < > "`. Image URLs are sanitised.

**Rationale.** Generated attributes are single-quoted, so an apostrophe in alt text closes the
attribute. `javascript:` and `data:` URLs are dropped rather than emitted, since an exported deck
may be shared onward.

### EXP-4 — Content-Length is a byte count

**Invariant.** The request body is read as bytes and then decoded.

**Rationale.** Reading `Content-Length` characters through a decoding reader never completes for a
multi-byte body, so any non-ASCII submission blocked until the socket timeout. ASCII-only tests
cannot detect this; the guard uses a Cyrillic and emoji payload.

---

## Presentation, delivery and connectivity

### HUD-2 — The HUD never changes the canvas

**Invariant.** Showing or hiding the presentation HUD leaves the slide canvas and its
fit-to-canvas scale untouched.

**Implementation.** The HUD stays an overlay inside the root `Box`, wrapped in
`AnimatedVisibility`. It is never given reserved layout space.

**Rationale.** Reserving space is the obvious fix and the wrong one: it shrinks the 16:9
canvas, which changes the fit scale, which makes the projected deck geometrically different
from the exported one. The overlay was covering the bottom ~68dp of every slide — the last
lines of a `FULL_CODE` slide and the deck's own footer — but the fix is *when* it draws, not
*where* the slide lives.

**Guard.** `HudVisibilityTest`.

### DEL-1 — Every transition value renders as itself

**Invariant.** Each `SlideTransition` produces a visually distinct animation.

**Implementation.** `TransitionResolver.resolve` picks the value; `transitionSpecFor` maps it to
a `ContentTransform` with an **exhaustive `when` over the closed enum**.

**Rationale.** All four values were modelled, parsed, persisted and offered in a picker, and the
renderer hardcoded a fade — so choosing "Zoom Scale" did nothing. The `when` is exhaustive
deliberately: adding a fifth value must be a compile error, not a silent fade. This is the same
argument that keeps `SlideLayoutContent` an exhaustive `when` rather than a registry.

**Guard.** `TransitionResolverTest`.

### UND-3 — A snapshot is the whole deck

**Invariant.** An undo snapshot captures per-file content, not only the combined markdown.

**Rationale.** Project-mode structural edits rewrite `activeProject.slideFiles` and can add or
remove whole files. Restoring `markdownText` alone leaves `activeProject` holding the pre-edit
files, and the next recompile overwrites the restored text — the undo silently undoes itself.
`DeckHistory` is generic over the snapshot type precisely so the caller decides what "the deck"
means in each mode.

**Guard.** `DeckHistoryTest`, `StructuralUndoTest`.

### LNK-1 — Advertise what the *client* can reach

**Invariant.** Address ranking is decided by how the phone is attached, not by which adapter
holds this machine's default route.

**Implementation.** `LinkRanking.classify` labels each interface (`HOTSPOT`, `TETHER`,
`BLUETOOTH_PAN`, `ROUTED_LAN`, `ORDINARY`, `VIRTUAL`); `priorityOf` ranks direct links above the
routed LAN.

**Rationale.** `routedAddress()` finds the interface holding the default route, which is almost
always right on a shared wifi — and exactly wrong the moment a hotspot exists. A hotspot or
tether interface carries *no* default route, so ranking on it put the laptop's own ethernet
first: an address the phone, now attached to the hotspot, has no path to. A direct link exists
only because the user deliberately created it, and whatever is on the other end is reachable by
construction.

Two traps are guarded because both were live: `"bluetooth"` was on the virtual-adapter denylist,
which ruled out Bluetooth PAN — the one Bluetooth mode that carries IP and needs no new code at
all (LNK-2); and Windows names its hotspot adapter *"Microsoft Wi-Fi Direct **Virtual**
Adapter"*, so the direct-link checks must run **before** the virtual denylist or the fix defeats
itself.

See [ADR-005](./ADR_COMPANION_LINK_ESTABLISHMENT.md).

**Guard.** `LinkRankingTest`.

### OUT-1 — An exported deck needs no network

**Invariant.** Exported HTML loads nothing over the network.

**Implementation.** `ElementImageRenderer` renders maths and diagrams through
`ImageComposeScene` at export time and embeds them as `data:` URIs.

**Rationale.** The export pulled KaTeX and Mermaid from a CDN, so an exported deck degraded to
raw LaTeX and raw Mermaid source on any machine without internet — precisely the conference-wifi
case an offline export exists for. The alternative was vendoring ~3.5 MB of JavaScript, which
would have shipped a *second* implementation of what the application already draws natively.
The trade-off accepted: maths and diagrams are images rather than selectable text, so the source
is preserved in `alt` text and a `<details>` fallback.

**Guard.** `OfflineHtmlExportTest`, plus the re-pinned assertion in `CharacterizationTest`.

---

## Concurrency and performance

### PRF-1 — Snapshot boundary

**Invariant.** State mutated from a companion worker thread is applied via
`applyFromBackgroundThread`, which wraps `Snapshot.withMutableSnapshot`.

**Rationale.** Compose snapshot state tolerates concurrent reads but not un-snapshotted concurrent
writes. The prior symptom was a `ConcurrentModificationException` being caught and discarded
around a list copy.

### PRF-4 — Monotonic timing

**Invariant.** Elapsed talk time derives from an injected monotonic source plus banked paused
duration. The publishing ticker lives only as long as the timer runs.

**Implementation.** `core/pacing/TalkTimer`. `currentElapsedSeconds()` is the authority; the
coroutine only publishes a snapshot of it, so a late or coalesced tick cannot affect the value.

**Rationale.** Counting `delay(1000)` iterations accumulates scheduling jitter and drifts over a
talk. `startPresenting` routes through `toggleTimer` rather than setting the flag directly, which
would leave the start timestamp unset and freeze the clock. The clock is injected because with
`System.nanoTime()` called inline this invariant was verifiable only by reading it. The ticker was
previously started unconditionally in `PresentationState.init` and cancelled only by `dispose()`,
which no test called — so a suite run leaked one live coroutine per constructed state object, and
a paused timer woke the CPU five times a second for nothing.

**Guard.** `TalkTimerTest` — pause/resume accumulation, a 3600 s jump reported in full, twenty
pause cycles without drift, and the ticker's lifetime.

### COR-13 — Slide search is exhaustive

**Invariant.** `SlideSearch` matches over **every** `SlideElement` variant. The `when` has no
`else`.

**Rationale.** The matcher lived inline in `CommandPalette` and ended in `else -> false` over a
*sealed* hierarchy, so polls, diagrams, formulas and images were silently unsearchable — a speaker
could not jump to a slide by its poll option. Kotlin already gives the Visitor guarantee for a
sealed hierarchy (a missing branch is a compile error) but only while nobody writes `else`. This
is the same habit that let EXP-3 ship, where tables, images and polls vanished from PNG export.
**Never write `else` on a `when` over `SlideElement`.**

**Guard.** `SlideSearchTest` — one case per variant, including the four the `else` swallowed.

### DED-6 — An error channel means what its name says

**Invariant.** `remoteServerError` carries companion-server failures only. Everything else the
user should see goes to `lastError`.

**Rationale.** `addNewSlideFile` reported a *slide-file creation* failure by assigning to
`remoteServerError` — the property named for, and rendered by, the pairing dialog. A speaker
trying to pair a phone would have been shown a file-system error. This is what a class holding
sixty properties does to error handling: the nearest field wins. Adding a channel is cheaper than
the confusion of a shared one.

**Guard.** `ErrorChannelTest` — a project whose `slides` path is a regular file fails to create a
slide, and the failure must land on `lastError` with `remoteServerError` untouched.

### COR-11 — One place decides where configuration is written

**Invariant.** `ConfigManager.rootDir` is the sole location authority, overridable at startup via
the `skaldoria.configDir` system property.

**Rationale.** It was a `by lazy` resolving `user.home` with no way in. `PresentationState`
autosaves through this object and is constructed in 20+ test cases, so `./gradlew desktopTest`
wrote a real `autosave_draft.md` and `config.json` into the developer's home — capable of
clobbering a draft recovered from a genuinely crashed session. The Gradle test task now points the
property at `build/test-config`, which makes the whole suite hermetic without each test having to
remember to redirect.

**Guard.** `ConfigStorageLocationTest` — asserts the suite's root is not the real home, and that
writes follow the configured root.

---

## Design decisions worth preserving

Four choices are deliberate and look like oversights if the reasoning is missing.

**Layout dispatch stays an exhaustive `when`.** `SlideLayoutContent` maps `SlideLayoutType` to a
composable with a `when` rather than a strategy registry, despite the open/closed argument for the
latter. `SlideLayoutType` is a closed enum, so exhaustiveness makes adding a layout a compile
error until it is wired up. A `Map<SlideLayoutType, …>` trades that guarantee for a blank slide at
runtime — the wrong trade for software whose failure is discovered in front of an audience. Open/
closed earns its keep against unbounded extension; this set is bounded and curated.

**The companion server is not framework-based.** Evaluated and re-evaluated; see
[ADR-001](./ADR_COMPANION_SERVER_ARCHITECTURE.md) and
[the Ktor analysis](./KTOR_MIGRATION_TRADEOFFS.md). The decision holds on packaging and JPMS
portability across six installer formats, not on the dependency-size figures originally quoted,
which were overstated. Revisit if push (WebSockets/SSE) replaces polling.

**Geometry is separated from drawing.** Diagram layout produces a `FlowchartScene` of pure
rectangles and offsets; renderers walk it. See
[ADR-002](./ADR_DIAGRAM_GEOMETRY_ARCHITECTURE.md). This is what allows subgraph invariants to be
asserted without rendering.

**Compact text inputs are not Material text fields.** `CompactTextField` exists because Material 3
enforces `MinHeight = 56.dp` and 16.dp vertical content padding; constraining one below its content
height crops rather than compresses. The same applies to buttons via `contentPadding`. See
[CONTRIBUTING](../CONTRIBUTING.md).

---

## Verification approach

**Regression tests must fail before the fix.** A guard that passes both before and after is
decoration. Where a guard covers a subtle invariant, it has been validated by temporarily
reverting the implementation and confirming the failure — the blanking regression (OVF-1) and the
subgraph frame invariants (MMD-10) were both verified this way.

**Rendering is verified by rendering.** A passing unit suite and a clean launch are not evidence
that a slide draws correctly; OVF-1 demonstrated that a correct-looking layout tree with
zero-height children satisfies both. `SlideRenderingTest` renders slides headlessly through
`ImageComposeScene` and asserts content pixels exceed a title-only floor, and `RenderAllProbe`
writes every layout to `build/render-all/` for inspection.

That guard detects *nothing drawn*; it cannot detect *drawn incorrectly*. Visual inspection
remains necessary for layout and diagram changes.

**Characterization before refactoring.** `CharacterizationTest` pins parser output for the bundled
decks, so a change to slide splitting or classification is visible rather than silent.

---

## Known limitations

| Area | Limitation |
| :--- | :--- |
| Diagrams | State, class, ER and Gantt diagram types are not supported; such blocks display their source. |
| Diagrams | Nested subgraphs are flattened — a node joins the innermost group that declares it. |
| Diagrams | The slide footer label reports the layout type rather than the parsed diagram type. |
| Editing | Moving a slide between files requires one slide per file. |
| Export | ~~Exported HTML loads KaTeX and Mermaid from a CDN.~~ **Resolved (OUT-01):** both are rendered at export time and embedded, so an exported deck needs no network. Maths and diagrams are now images rather than selectable text. |
| Media | Video is not supported; only raster images. |
| Build | `./gradlew --warning-mode all` reports one Gradle deprecation (`archives configuration`) originating in the Kotlin Multiplatform plugin. Present through Kotlin 2.3.10; requires an upstream fix. See [CONTRIBUTING](../CONTRIBUTING.md). |
