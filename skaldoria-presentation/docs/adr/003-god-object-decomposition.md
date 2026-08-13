# Architecture Decision Record (ADR)
## ADR-003: Decomposing the God Objects — Cohesion, Boundaries and Cognitive Complexity

### Status
**Proposed** (2026-08-06)

> This ADR is a design proposal. **No code has been changed.** It records where responsibility
> has accumulated into a small number of very large types, what that costs today in concrete
> and measurable terms, and the smallest structural change that removes each cost.
>
> It follows the precedent of [ADR-002](../../../skaldoria-shared-ui/docs/adr/002-diagram-geometry-architecture.md): prefer the
> single maintainable solution over a patch, but size the abstraction to the domain and stop
> there. Several recommendations below are deliberately *"do not introduce a pattern here"* —
> the language or the existing design already provides the guarantee, and adding a pattern on
> top would be ceremony, not structure.

---

### Contents

- [Context](#context)
- [Finding 1 — `PresentationState` is a god object](#finding-1--presentationstate-is-a-god-object)
- [Finding 2 — `RemoteCompanionServer` is a god object, and its security policy is duplicated](#finding-2--remotecompanionserver-is-a-god-object-and-its-security-policy-is-duplicated)
- [Finding 3 — `parseSlideSection` is the cognitive-complexity hotspot](#finding-3--parseslidesection-is-the-cognitive-complexity-hotspot)
- [Finding 4 — Composables written as long procedures](#finding-4--composables-written-as-long-procedures)
- [Finding 5 — Keyboard shortcuts have four sources of truth](#finding-5--keyboard-shortcuts-have-four-sources-of-truth)
- [Finding 6 — Singletons block substitution, and tests write to the real home directory](#finding-6--singletons-block-substitution-and-tests-write-to-the-real-home-directory)
- [Where a pattern would be the wrong answer](#where-a-pattern-would-be-the-wrong-answer)
- [Decision](#decision)
- [Options considered](#options-considered)
- [Deliberate scope boundaries](#deliberate-scope-boundaries)
- [Consequences](#consequences)
- [Migration order](#migration-order)
- [Appendix — observations outside this ADR's scope](#appendix--observations-outside-this-adrs-scope)

---

### Context

Skaldoria is ~21.5k lines of Kotlin across 97 files, with a 235-test suite that passes. The
code is unusually well *documented* — [`QUALITY_BASELINE.md`](../QUALITY_BASELINE.md) assigns
permanent identifiers to invariants and those identifiers appear beside the logic they
constrain. Nothing in this ADR disputes that work; the invariants are sound and the reasoning
behind the non-obvious ones is recorded properly.

The problem is **distribution**, not correctness. Two types hold a disproportionate share of
the system:

| File | Lines | Share of `desktopMain` (16 554 lines) |
| :--- | ---: | ---: |
| `state/PresentationState.kt` | 1323 | 8.0% |
| `remote/RemoteCompanionServer.kt` | 1122 | 6.8% |
| `ui/screens/PresenterView.kt` | 789 | 4.8% |
| `core/parser/MarkdownSlideParser.kt` | 716 | 4.3% |
| **Top 4 of 64 files** | **3950** | **23.9%** |

ADR-002 already fixed this shape *once*, for diagram geometry: it moved pure logic out of the
draw pass into tested `core/` functions. That boundary worked. This ADR applies the same
reasoning to application state, the companion server, and the parser — the three places where
the same accumulation has happened and has not yet been addressed.

---

### Finding 1 — `PresentationState` is a god object

#### The measurement

`PresentationState` exposes **60 properties** (38 of them Compose `mutableStateOf`) and
**72 public functions** — a public surface of ~132 members on one class. It is passed whole
into **15 different UI and infrastructure files**.

It owns at least twelve unrelated responsibilities:

| # | Responsibility | Representative members |
| :--- | :--- | :--- |
| 1 | Deck document | `markdownText`, `slides`, `updateMarkdown`, `updateEditorContent` |
| 2 | Navigation | `currentSlideIndex`, `next/prev/goToSlide`, `hasNext/hasPrev` |
| 3 | Structural slide editing | `moveSlide`, `duplicateSlide`, `deleteSlide`, `insertSlide`, `editOwningFile` |
| 4 | Project & file I/O | `openPath`, `openDeckProject`, `saveFile`, `saveAsFile`, `exportHtml` |
| 5 | Find & replace | 8 properties + `findNext`, `replaceCurrent`, `replaceAll`, … |
| 6 | Talk timer & pacing | `elapsedSeconds`, `accumulatedSeconds`, `pacingStatus`, `pacingDeltaSeconds` |
| 7 | Audience Q&A | `audienceQuestions`, `submitQuestion`, `upvoteQuestion`, `dismissQuestion` |
| 8 | Poll ballots | `pollVotesMap`, `recordVote`, `getVotesForSlide` |
| 9 | Parking lot | `followUpQuestions`, `reconcileFollowUpQuestions`, `persistFollowUpQuestions`, … |
| 10 | Annotation strokes | `annotations`, `addStroke`, `undoStroke`, `clearAllAnnotations` |
| 11 | Ephemeral UI flags | `isCommandPaletteOpen`, `isGridOverviewOpen`, `isCustomThemeDialogOpen`, … (7 dialogs) |
| 12 | Theme, unlock code, preferences, companion-server lifecycle | `unlockCorporateTheme`, `toggleRemoteServer`, `persistUiPreferences` |

Plus ~125 lines of the file are two sample-deck string literals
(`DEFAULT_SAMPLE_MARKDOWN`, `BLANK_STARTER_MARKDOWN`) — content, living in a state class.

#### Why this is not merely aesthetic

Four costs are already being paid, and each is observable rather than theoretical.

**1. The error channel has been overloaded.** `addNewSlideFile` reports a *file-creation*
failure through `remoteServerError` (`PresentationState.kt:650`):

```kotlin
} catch (e: Exception) {
    remoteServerError = "Could not create slide file: ${e.message}"
```

That is the classic god-object symptom: with every field in one bag, the nearest field wins,
and a property named for the companion server becomes the app's generic error channel.
Whatever surfaces `remoteServerError` in the pairing dialog will one day show a slide-file
error to a speaker who is trying to pair a phone.

**2. Unit tests mutate the developer's real home directory.** The constructor starts an
autosave path that reaches `ConfigManager`, which writes to `~/.skaldoria/`. `PresentationState()`
is constructed in **20+ test cases**. Running `./gradlew :skaldoria-presentation:desktopTest` wrote
`~/.skaldoria/autosave_draft.md` and `~/.skaldoria/config.json` on this machine — verified by
timestamp against the test run. Tests are not hermetic, and a test can in principle clobber a
real recovered draft.

**3. Every test leaks a coroutine.** The `init` block launches an unbounded
`while (true) { delay(200) … }` ticker on `Dispatchers.Default`. Only `dispose()` cancels it,
and no test calls `dispose()`. A full suite run leaks one live ticker per constructed instance.

**4. The public surface is the wrong shape for almost every consumer (ISP).**
`SlideAnnotationOverlay` needs 4 members; it receives ~132. `RemoteCompanionServer.start()`
takes the entire application state — including file dialogs, theme unlocking and structural
slide editing — to use about twelve members. A compromised or buggy call path has the whole
app reachable from a network worker thread.

Additionally, the class violates **DIP** by construction: it instantiates its own
`CoroutineScope(Dispatchers.Default)`, reads wall-clock time directly via `System.nanoTime()`,
and calls four singletons by fully-qualified name *inside method bodies*
(`com.skaldoria.project.DeckProjectManager` at lines 467, 491, 648, 655, 722). None of these
can be substituted in a test, which is precisely why the tests touch the real filesystem.

#### Proposed structure

Extract **cohesive collaborators**, and keep `PresentationState` as a **thin facade** that
delegates to them. The facade is the important half of this proposal: it means the 15 UI call
sites do not have to change in the same commit as the extraction, so each step ships working.

| New type | Absorbs | Becomes testable as |
| :--- | :--- | :--- |
| `DeckDocument` | markdown text, parsed slides, project files, structural edits, autosave scheduling | already partly exists as `SlideDocument`; this is its stateful owner |
| `SlideNavigator` | index, `next/prev/goToSlide`, `hasNext/hasPrev` | trivial, pure |
| `TalkTimer` | monotonic run/pause bookkeeping, injected `Clock` and scope | deterministic with a fake clock — today untestable |
| `PacingCalculator` | `pacingStatus`, `pacingDelta`, `targetSecondsPerSlide`, `remainingSeconds` | **pure function** of `(elapsed, target, index, count)` |
| `AudienceSession` | Q&A queue + ballots + SEC-5 bounds | the *only* thing the companion server needs to mutate |
| `ParkingLotStore` | reconcile / persist / add / answer / delete / export | already the most invariant-dense area |
| `FindReplaceController` | 8 properties + 7 functions + the `derivedStateOf` cache | pure over a text buffer |
| `AnnotationLayer` | per-slide strokes | pure |
| `UiFlags` | the 7 dialog booleans + `showWelcome` | no logic — a plain holder |
| `SampleDecks` | the two markdown literals | content, not state |

`PacingCalculator` deserves emphasis. The pacing formula is a headline feature of this product
and it is currently entangled with a live clock, so it is verified only by reading it. As a
pure function it is four assertions.

For DIP, inject rather than construct: `Clock`, `CoroutineScope`, and narrow interfaces for
`ConfigStore`, `ProjectRepository`, `FileDialogs`, `CompanionServer`. **The codebase already
does this** — `AdaptiveContrastEnforcer : IContrastEnforcer` and
`ThemePaletteValidator : IThemeValidator` are exactly this pattern, with the rationale in
their KDoc. This finding asks only that the precedent be applied where it pays most.

---

### Finding 2 — `RemoteCompanionServer` is a god object, and its security policy is duplicated

One `object`, 1122 lines, holding eight distinct concerns:

1. Socket lifecycle and port fallback (`start`, `stop`)
2. Network-interface ranking and virtual-adapter detection (`availableAddresses`, ~90 lines)
3. A hand-written HTTP/1.1 request parser (`readAsciiLine`, `handleClientSocket`)
4. Routing (`routeRequest`)
5. Authentication and rate limiting (`isAuthorized`, `allowRequest`, `TokenBucket`)
6. Eight endpoint handlers
7. A hand-rolled JSON serializer (string templates at 12 call sites)
8. **375 lines of embedded HTML/CSS/JavaScript** for two web portals — 34% of the file

#### The structural risk worth acting on first

The security policy for a route is expressed as **membership in two separate sets**, checked
against a **third** `when (path)` dispatch:

```kotlin
private val PRESENTER_ENDPOINTS = setOf("/api/action", "/api/qa/dismiss")
private val WRITE_ENDPOINTS = setOf("/api/action", "/api/poll/vote", /* …4 more */)
// …and then:
when (path) { "/api/action" -> handleActionApi(…); /* …7 more */ }
```

Adding an endpoint therefore requires up to **three coordinated edits in three places**, and
**nothing fails if one is forgotten**. A new write endpoint omitted from `WRITE_ENDPOINTS` is
silently exempt from both the POST-only requirement (SEC-3) and rate limiting (SEC-5); omitted
from `PRESENTER_ENDPOINTS`, it is silently unauthenticated (SEC-2). Three invariants that the
quality baseline treats as load-bearing currently depend on a human remembering two set
literals.

**Proposal:** make the route table *data*, declared once:

```kotlin
private data class Route(
    val path: String,
    val method: HttpMethod,
    val scope: Scope,        // Public | Audience | Presenter
    val handler: (Request, AudienceSession) -> Response
)
```

The policy then follows from `scope` and `method` rather than from set membership, a route
cannot exist without declaring its scope, and `RemoteCompanionServerTest` can assert
*structurally* — "every non-`Public` route requires a token", "every mutating route is POST" —
instead of enumerating known paths one by one. This is the single highest-value change in this
ADR: it converts three remembered rules into one compiler-enforced declaration.

#### The other three extractions

- **`HttpRequestParser`** — bytes → `Request`. Currently the parser is fused to a live
  `Socket`, so its caps (SEC-7: request-line, header-count, body-size, chunked rejection) can
  only be tested by opening a real connection. As a pure function over an `InputStream` they
  are ordinary unit tests.
- **`PortalAssets`** — move the two portals to `resources/portal/{remote,audience}.html`, read
  at startup. This removes a third of the file, gives the HTML/JS real syntax highlighting and
  tooling, and lets the SEC-1 `textContent`-only discipline be checked by something other than
  a code comment asking future readers not to "simplify" it back.
- **A single JSON writer.** `escapeJson` handles `\`, `"`, `\n`, `\r`, `\t` — but **not the
  other C0 control characters**, which JSON requires to be escaped. Audience text arrives
  URL-decoded, so `%01` yields a raw `0x01` that survives `trim()` and `take()` and is emitted
  raw into the response. The likely result is invalid JSON → `res.json()` throws in the portal
  → the failure is swallowed by `catch(e){}` → **both portals silently stop updating for
  every device** until that question is dismissed. *This is a reading of the code, not a
  reproduced bug — it should be confirmed with a test before being treated as a defect.*
  Either way, twelve hand-built JSON strings is twelve chances to make this class of mistake;
  one writer is one chance.

#### Coupling to Finding 1

`start(state: PresentationState)` should narrow to the ports the server actually uses —
`DeckControl` (navigate, blackout, timer) and `AudienceSession` (vote, ask, upvote, dismiss).
`PresentationState` implements both. That is ISP and DIP together, and it makes the server's
tests independent of the whole application state.

---

### Finding 3 — `parseSlideSection` is the cognitive-complexity hotspot

`MarkdownSlideParser.parseSlideSection` (lines 119–482) is a **single 364-line function**
holding:

- 12 mutable locals (`title`, `inCodeBlock`, `inMathBlock`, `currentListItems`,
  `currentQuoteLines`, `currentTableLines`, `currentMathLines`, `currentCodeLines`, …)
- 5 nested closures that mutate them (`flushMath`, `flushList`, `flushQuote`, `flushTable`,
  `flushCode`)
- a 9-branch `if (…) { … continue }` chain over each line

Two properties make it hard to change safely:

1. **Correctness depends on implicit branch ordering.** Tables must be tested before headings;
   the metric rule before the paragraph fallback; the HTML-comment skip before paragraphs.
   None of that ordering is named or asserted — it is just the order the `if`s happen to
   appear in, and the comments record several past defects caused by exactly this.
2. **The `flushX()` ritual is repeated ~20 times.** Nearly every branch opens with two to four
   flush calls. Missing one is a silent element loss, and there is no structural reason a
   reader would notice.

There is also verbatim duplication: the poll-directive block at lines 246–252 is copy-pasted
at 264–270. And `applyDirective` takes **three setter lambdas** purely because its targets are
locals of the giant function — a smell that disappears once the state is an object.

**Proposal — Chain of Responsibility, ordering made explicit:**

```kotlin
private interface BlockRule {
    fun matches(line: String, ctx: SectionContext): Boolean
    fun consume(line: String, ctx: SectionContext)
}

// Order is the specification, in one readable place, and testable.
private val RULES = listOf(
    CodeFenceRule, MathBlockRule, DirectiveRule, NoteRule,
    TableRule, HeadingRule, ListRule, QuoteRule,
    ImageRule, MetricRule, CommentRule, ParagraphRule
)
```

`SectionContext` (a Builder / Parameter Object) holds the accumulating state and owns
`flushPending()`, called **once by the dispatcher** rather than by every branch. The three
setter lambdas collapse into `ctx.layout = …`. Each rule becomes independently testable, and
the ordering that correctness depends on becomes a list a reviewer can read in ten seconds.

This is a *decomposition*, not a rewrite: the rule bodies are the existing branch bodies moved
verbatim, and `MarkdownSlideParserTest` plus `CharacterizationTest` already guard the
behaviour during the move.

---

### Finding 4 — Composables written as long procedures

| Composable | Lines | Deepest nesting |
| :--- | ---: | ---: |
| `PresenterView` | 597 | 8 |
| `EditorWorkspace` | 549 | 9 |
| `FullscreenDeck` | 330 | 6 |

These are Clean Code "extract till you drop" cases, with obvious duplication inside them: the
`PresenterView` tab strip is three copy-pasted ~20-line `Button` blocks differing only by
index, icon, label and colour; the Q&A card body (lines 440–512) is ~70 lines inline.

The argument for fixing this is **not** only readability — there is a measurable cost on a
product that advertises 120 FPS:

`PresenterView` reads `state.elapsedSeconds` at line 58, in the top-level body. That makes the
**entire 597-line composable the invalidation scope for a value that changes every 200 ms**.
Children are skipped only when their parameters are stable, and `Slide` carries
`List<SlideElement>`, which Compose treats as unstable — so both `SlideSurface` previews are
likely re-executing several times a second while the presenter console is open.

Extracting `PresenterHeader`, `PacingRibbon` (already separate), `SlidePreviewColumn`,
`NotesPanel`, `QaPanel` and `DeliveryControlBar` costs nothing at runtime — composables are
just functions — and confines each state read to the scope that needs it. A suggested working
rule: **no composable over ~80 lines**, and a repeated element becomes a parameterised private
composable rather than a third copy.

---

### Finding 5 — Keyboard shortcuts have four sources of truth

The same key-to-action mapping is written in four unrelated places:

1. `Main.kt` — an 11-branch `when` for the studio window
2. `FullscreenDeck.kt` — a 12-branch `when` for the deck window
3. `AppTooltip(shortcut = "Ctrl+F" | "G" | "B" | …)` — literal strings scattered across the UI
4. `README.md` — the user-facing shortcut table

Nothing keeps these consistent. A rebind requires four edits and the documentation drifts
silently. **Proposal (Command pattern):** one `AppCommand(id, label, shortcut, scope, action)`
registry; the two key handlers dispatch through it, `AppTooltip` reads its shortcut label from
it, and the README table can be generated from it.

A useful side effect: `CommandPalette` is described in the README as a "Spotlight Command
Palette" but today only searches and jumps to slides — it executes no commands. A command
registry is exactly what would make the name true, at almost no extra cost.

---

### Finding 6 — Singletons block substitution, and tests write to the real home directory

Seven infrastructure types are Kotlin `object`s with mutable global state or filesystem
access: `ConfigManager`, `FileManager`, `DeckExporter`, `DeckProjectManager`,
`RemoteCompanionServer`, `ImageResolver`, `QrCodeGenerator`.

Consequences already visible:

- `ConfigManager` resolves `~/.skaldoria` from `System.getProperty("user.home")` in a `by lazy`
  with no injection point — which is why the test suite writes to the developer's real
  configuration (Finding 1).
- `RemoteCompanionServer` holds process-global socket, executor and token state, so its tests
  must run strictly sequentially and can never be parallelised.
- `DeckExporter` owns a private `CoroutineScope(Dispatchers.IO + SupervisorJob())` that nothing
  ever cancels.

**Proposal:** keep the objects as the default *composition-root wiring*, but define a narrow
interface for each and depend on the interface. `IContrastEnforcer` / `IThemeValidator` are the
in-repo precedent. The minimum viable step — and the one that fixes the hermeticity problem
immediately — is giving `ConfigManager` an injectable base directory.

---

### Where a pattern would be the wrong answer

Three places look like textbook pattern candidates and should be **left alone**. Recording
them matters as much as the recommendations: applying a pattern here would add indirection
without adding a guarantee.

| Candidate | Why not |
| :--- | :--- |
| **Strategy/registry for slide layouts** | `SlideSurface.SlideLayoutContent` already documents this decision, and it is correct. `SlideLayoutType` is a closed enum; the exhaustive `when` makes a missing layout a **compile error**, whereas a `Map<SlideLayoutType, …>` would make it a blank slide discovered live in front of an audience. Open/closed earns its keep against *unbounded* extension; this set is bounded. |
| **Visitor for `SlideElement`** | `SlideElement` is a **sealed interface**. A `when` over it with no `else` is already exhaustive and already a compile error when a case is missing — Kotlin gives the Visitor guarantee for free. The EXP-3 bug (tables, images and polls vanishing from PNG export) was caused by writing `else -> {}`, not by the absence of Visitor. **The rule to adopt is "never write `else` on a `when` over `SlideElement`."** There is one live instance to fix: `CommandPalette.kt:58`, whose `else -> false` means searching finds nothing inside diagram source, poll options, math formulas or image alt text. |
| **Abstracting the two diagram renderers** | ADR-002 already settled this and its scene layer is implemented. Do not re-litigate it here. |

---

### Decision

Adopt the same boundary ADR-002 established, in three more places:

1. **`PresentationState` becomes a facade over cohesive collaborators**, each owning one
   responsibility, with `Clock`, scope and infrastructure **injected** rather than constructed.
2. **`RemoteCompanionServer` becomes a composed server**: a declarative route table carrying
   its own security scope, a pure request parser, externalised portal assets, one JSON writer,
   and dependence on narrow ports rather than on the whole application state.
3. **`parseSlideSection` becomes an ordered list of block rules** over a shared context,
   making the ordering that correctness depends on explicit and each rule independently
   testable.

Supported by three lower-risk hygiene changes: extract oversized composables, unify the
shortcut registry, and give `ConfigManager` an injectable root so the suite stops writing to
the developer's home directory.

---

### Options considered

| Option | Description | Verdict |
| :--- | :--- | :--- |
| **A. Do nothing** | The classes are documented and the tests pass. | ❌ The costs are already being paid — overloaded error channel, non-hermetic tests, leaked coroutines, three-place security policy. They compound with each feature. |
| **B. Split files only** | Move the god objects into `partial`-style extension files for readability. | ❌ Cosmetic. Coupling, testability and the ISP problem are all unchanged; the class is still ~132 members wide. |
| **C. Full architectural rewrite** (MVI / redux store, DI container, module split) | Replace state management wholesale. | ❌ Disproportionate. It would invalidate the 235-test suite and the invariant identifiers that make this codebase reviewable — the very assets worth keeping. |
| **D. Facade + incremental extraction (this ADR)** | Extract collaborators behind the existing surface; make the security policy declarative; decompose the parser loop. | ✅ Each step is independently shippable, guarded by the existing suite, and leaves the app working. Matches the ADR-002 precedent that already worked here. |

---

### Deliberate scope boundaries

- **Not a rewrite.** Every extraction moves existing bodies verbatim. The invariant identifiers
  (`SEC-*`, `COR-*`, `PRF-*`, `MMD-*`, `EXP-*`, `DED-*`) and their comments **move with the
  code they constrain** — they are permanent per `QUALITY_BASELINE.md` and must not be
  renumbered.
- **No DI framework.** Constructor injection with default arguments is sufficient at this size.
- **No new patterns where the language already provides the guarantee.** See
  [above](#where-a-pattern-would-be-the-wrong-answer).
- **Out of scope:** the diagram engine (ADR-002 governs it), theme/colour science (already
  small, pure and tested), and the choice of a hand-rolled server over Ktor (settled in
  [ADR-001](./001-companion-server-architecture.md) and
  [`KTOR_MIGRATION_TRADEOFFS.md`](../KTOR_MIGRATION_TRADEOFFS.md)).

---

### Consequences

**Positive**
- Route security becomes a declaration rather than three remembered set memberships — the
  single largest risk reduction here.
- The pacing formula, the HTTP parser's caps, and the timer become **unit-testable**; today
  each is verified only by reading it or by opening a socket.
- Tests stop writing to `~/.skaldoria` and stop leaking a ticker coroutine per instance.
- The companion server can no longer reach file dialogs or structural editing from a network
  worker thread.
- Tighter recomposition scopes in the presenter console, which is a real concern on a product
  that advertises 120 FPS.

**Negative / costs**
- One delegation hop for every call that currently reaches straight into `PresentationState`.
- The facade is transitional. It must be kept honest — if it accumulates logic of its own, the
  god object has simply been renamed.
- Externalising the portal HTML means it is loaded from resources at runtime; packaging must
  be verified for the native distributions.
- Up-front test-writing for the newly extracted units.

**Neutral**
- `RenderAllProbe` / `SlideRenderingTest` PNG probes remain the final visual guard, unchanged.

---

### Migration order

Ordered by *risk reduction per unit of change*, each step independently shippable and guarded
by the existing suite.

| # | Step | Risk | Payoff |
| :--- | :--- | :--- | :--- |
| 1 | Injectable root for `ConfigManager`; tests use a temp dir | very low | tests become hermetic immediately |
| 2 | Declarative route table in `RemoteCompanionServer` + structural security assertions | low | closes the three-place-policy risk |
| 3 | One JSON writer with complete C0 escaping | low | removes 12 duplicated escape sites |
| 4 | Extract `PacingCalculator` (pure) and `TalkTimer` (injected `Clock`) | low | headline feature becomes testable |
| 5 | Extract `AudienceSession`; narrow the server's dependency to it | medium | server stops holding the whole app |
| 6 | Extract `HttpRequestParser`; move portals to resources | medium | −34% of the server file |
| 7 | Extract `FindReplaceController`, `AnnotationLayer`, `UiFlags`, `SampleDecks` | low | −350 lines from the god object |
| 8 | Extract `DeckDocument` + `SlideNavigator` (largest, do last) | high | the core of Finding 1 |
| 9 | `parseSlideSection` → ordered `BlockRule` list | medium | the cognitive-complexity hotspot |
| 10 | Split oversized composables; unify the shortcut registry | low | readability + recomposition scope |

Steps 1–4 are worth doing regardless of whether the rest is ever scheduled.

---

### References
- [`ADR-001: Companion Server Architecture`](./001-companion-server-architecture.md) — why the server is hand-rolled; unchanged by this ADR.
- [`ADR-002: Diagram Geometry Architecture`](../../../skaldoria-shared-ui/docs/adr/002-diagram-geometry-architecture.md) — the pure-core boundary this ADR extends.
- [`QUALITY_BASELINE.md`](../QUALITY_BASELINE.md) — the invariants and identifiers that must survive every extraction.
- `theme/AdaptiveContrastEnforcer.kt`, `theme/ThemePaletteValidator.kt` — the existing in-repo precedent for interface-backed singletons.
- `core/layout/SlideCanvasFit.kt`, `core/diagram/FlowchartLayoutEngine.kt` — the "pure logic in `core/`, tested" pattern being applied to state and parsing.
