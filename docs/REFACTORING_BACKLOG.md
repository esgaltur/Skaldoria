# Refactoring Backlog

Companion to [ADR-003](./ADR_GOD_OBJECT_DECOMPOSITION.md). The ADR argues *why*; this file is
the actionable list — one row per fix, ordered by **risk reduction per unit of change**.

**Status — 2026-08-06.** 21 of 24 items are **done**, every one implemented test-first.
The suite went from **235 to 501 tests, 0 failures**.

| | Item | Outcome |
| :--- | :--- | :--- |
| ✅ | F-01 | Injectable config root — the suite no longer writes to the real `~/.skaldoria` |
| ✅ | F-02 | Ticker leak fixed; it now lives only while the timer runs |
| ✅ | F-03 | `lastError` split from `remoteServerError` |
| ✅ | F-04 | Complete C0 escaping — **the bug was reproduced before it was fixed** |
| ✅ | F-05 | `SlideSearch`, exhaustive over `SlideElement`, no `else` |
| ✅ | F-06 | `PacingCalculator` (pure) + `TalkTimer` (injected clock) |
| ✅ | F-07 | Declarative route table; security asserted over the table, not by path |
| ✅ | F-08 | `AudienceSession` + `DeckControl` port — the server no longer sees the app state |
| ✅ | F-09 | `HttpRequestParser`, pure over a stream |
| ✅ | F-10 | Portals moved to `resources/portal/`; packaging verified in the built jar |
| ✅ | F-11 | `FindReplaceController`, `AnnotationLayer`, `SampleDecks` extracted |
| ✅ | F-12 | `ParkingLotStore` extracted with its invariants |
| ✅ | F-15 | Duplicated poll-directive block removed |
| ✅ | F-16 | Setter lambdas replaced by `SectionDirectives` |
| ✅ | F-17 | `parseSlideSection` → ordered `BlockRule` chain (364 lines → ~35) |
| ✅ | F-20 | `DeckExporter.dispose()`, wired to the composition root |
| ✅ | F-21 | Export docs corrected — it was **not** self-contained, contrary to three claims |
| ✅ | F-22 | Doc test counts corrected |
| ✅ | F-23 | Coroutines versions unified behind one variable |
| ◐ | F-13 | `SlideNavigator` **done**; `DeckDocument` still outstanding — see below |
| ⬜ | F-14 | Invert the remaining singleton dependencies |
| ⬜ | F-18 | Split the oversized composables |
| ✅ | F-19 | `AppCommands` registry; both key handlers dispatch through it |
| ✅ | F-24 | All 8 uninspected renders looked at; 2 defects found, 1 fixed |

### Dead-code pass (2026-08-06)

The Kotlin compiler does not report unused *public* declarations, so these were found with a
reference scan rather than a build warning — a definitely-unused function compiles silently
here. Three of them turned out to be **features that were built but never reachable**, which is
why they were wired rather than deleted.

| Was | Action |
| :--- | :--- |
| `recoverableDraft` / `restoreDraft` / `discardDraft` | **Wired.** DED-1 claimed the welcome screen offered crash recovery; nothing called them, so a crash still lost the deck. Now offered on the welcome screen, guarded by `DraftRecoveryTest`. |
| `saveAsFile` | **Wired.** Save As was unreachable — a deck could only be written back to the file it came from. Now a TopBar button and `Ctrl+Shift+S`, ordered before plain `Ctrl+S` so it cannot be swallowed. |
| `ColorScience.isWcagAa` | **Wired.** `ThemePaletteValidator` re-typed the `4.5f` AA threshold at two sites instead of calling it — two sources of truth for the number this product's accessibility badge rests on. |
| `audienceQuestionsSnapshot`, `upvoteQuestion`, `resetVotesForSlide`, `submitQuestion` | **Removed.** Orphaned by F-08; the server now speaks to `AudienceSession` directly. |
| `updateLastStroke`, `clearAllAnnotations`, `openFind` | **Removed.** Verified redundant — `SlideAnnotationOverlay` accumulates points locally and commits on drag end, so pen drawing never needed the incremental API. |
| `isWcagAaa`, `computeSurfaceBorder`, `indexOfFile`, `openMarkdownFile`, `removeRecentProject`, `replaceSymbols` | **Removed.** Dead helpers with no feature and no doc behind them. |
| `DiagramCard` | **Rewritten and wired.** It had been written against a *guess* at the chrome (hardcoded `Info` icon, `Close` button, sans-serif label) — adopting it as-was would have silently restyled both renderers. Rewritten against what they actually draw, parameterised by icon and trailing action, and both `MermaidDiagramCanvas` and `MathFormulaRenderer` now use it. Verified visually in both directions: `11_math.png` and `05_vertical_flowchart.png` are unchanged. ADR-002's shared-chrome step is now genuinely complete. |

**Two defects found by actually looking** (F-24), which no passing test would have caught:
`PollSlide` silently dropped every element except the poll — bullets and paragraphs on a poll
slide vanished, the same class as EXP-3. **Fixed and re-verified.** And `else` inside an `alt`
block renders as a centred box rather than a labelled divider, so it reads as a message that
was never in the source — recorded in `RENDERING_STATUS.md`, not fixed.

Seven functions remain referenced only from tests (`allMessages`, `clearLastError`, `getById`,
`placementOf`, `resetVotes`, `serializeFollowUpQuestions`, `sourceOf`). Those are legitimate
test seams, not dead code, and are left alone.

**Measured effect**

| File | Before | After |
| :--- | ---: | ---: |
| `RemoteCompanionServer.kt` | 1122 | **594** |
| `PresentationState.kt` | 1323 | **842** |
| `MarkdownSlideParser.kt` | 716 | **375** |
| `parseSlideSection` (one function) | 364 | **~35** |

New invariants registered in [`QUALITY_BASELINE.md`](./QUALITY_BASELINE.md): **SEC-8**, **COR-11**,
**COR-12**, **COR-13**, **DED-6**, **DED-7**, **DED-8**, **DED-9**; **PRF-4** extended. The `F-nn`
identifiers in this file are backlog numbers only — code comments cite the permanent invariant
ids, never these.

### F-19 · one binding table (2026-08-06)

`AppCommands` is now the single declaration of the keyboard surface, free of Compose types so
it can be unit-tested; `KeyBindings` is the only place that knows about `Key`. Both `when`
blocks dispatch through it — `Main.kt` went from 15 key comparisons to 1 (the `Escape`
special case, which is conditional on the find bar rather than a binding), and
`FullscreenDeck.kt` from 15 to 0.

Two assertions exist because duplication kept breaking them: no chord bound twice in a scope,
and no chord shadowed by a less-specific one — the exact fault that would let `Ctrl+Shift+S`
be swallowed by `Ctrl+S`. `KeyBindingsTest` covers the seam the registry cannot see: a key
name with no `Key` behind it compiles, reads correctly, and never fires.

**A regression caught while wiring it.** Modelling `scope` as a single value silently dropped
`Ctrl+K` from the deck window, where the palette had always worked. `scopes` is a set.

### F-13 · half done

`SlideNavigator` is extracted and tested — the cursor, its bounds, and the two distinct
"where do I land" semantics (`goTo` refuses an out-of-range index; `moveTo` clamps, because
structural edits compute the landing index from a deck that has since been reparsed).

`DeckDocument` is **not** done, and is the last large piece. `PresentationState` has grown to
1082 lines during this work — undo/redo and HUD visibility landed alongside it — so the
extraction is now larger than when it was scoped, and it touches the file that is under active
development. It wants its own session against a quiet tree.

**One behaviour change found and preserved, not fixed.** The directive and note rules sit above
`InCodeBlockRule`, so a `<!-- note: … -->` inside a fenced block is still consumed as a directive
rather than as code. It is recorded in `BlockRules.kt`; correcting it changes output and belongs
in its own commit.

**Legend** — Effort: `S` ≤ half a day · `M` 1–2 days · `L` 3+ days.
Risk: chance of regression, given the existing test suite.

---

## Tier 0 — Do these regardless of whether the rest is ever scheduled

Small, isolated, and each removes a real defect or a real risk.

### F-01 · Make the test suite hermetic
- **Where:** `config/ConfigManager.kt:31-38`
- **Problem:** `configDir` is a `by lazy` resolving `System.getProperty("user.home")` with no
  injection point. `PresentationState()` is constructed in 20+ tests and its autosave path
  reaches `ConfigManager`, so `./gradlew desktopTest` writes to the developer's real
  `~/.skaldoria/` — verified: the last run wrote `autosave_draft.md` and `config.json`. A test
  can clobber a genuine recovered draft.
- **Fix:** give `ConfigManager` a settable/injectable root (`var rootDir: File = defaultRoot`,
  or a `ConfigStore` interface with the object as default wiring). Tests point it at
  `@TempDir`.
- **Guard:** new test asserting no write occurs outside the temp root.
- **Effort:** S · **Risk:** very low

### F-02 · Stop leaking a ticker coroutine per `PresentationState`
- **Where:** `state/PresentationState.kt:37, 329-343`
- **Problem:** `init` launches `while (true) { delay(200) … }` on a self-constructed
  `CoroutineScope(Dispatchers.Default)`. Only `dispose()` cancels it; no test calls `dispose()`.
  A suite run leaks one live ticker per constructed instance.
- **Fix:** accept the scope as a constructor parameter (`scope: CoroutineScope = CoroutineScope(Dispatchers.Default)`).
  Tests pass a scope they cancel in `@AfterTest`. Pairs naturally with **F-06**.
- **Guard:** test asserting the ticker job is cancelled after `dispose()`.
- **Effort:** S · **Risk:** very low

### F-03 · Fix the overloaded error channel
- **Where:** `state/PresentationState.kt:650`
- **Problem:** a *slide-file creation* failure is reported through `remoteServerError`, the
  property named for the companion server. Whatever surfaces it in the pairing dialog will
  show a file error to a speaker trying to pair a phone.
- **Fix:** introduce a general `lastError: String?` (or a small typed error channel) and leave
  `remoteServerError` to the server. Interim: rename to `statusError` and use it consistently.
- **Guard:** test asserting a failed `addNewSlideFile` does not set `remoteServerError`.
- **Effort:** S · **Risk:** low

### F-04 · Complete the JSON escaping and centralise it
- **Where:** `remote/RemoteCompanionServer.kt:736` (`escapeJson`) plus ~12 hand-built JSON
  strings (`handleStateApi` and every `handle*Api`)
- **Problem:** `escapeJson` covers `\`, `"`, `\n`, `\r`, `\t` but **not the other C0 control
  characters**, which JSON requires to be escaped. Audience text arrives URL-decoded, so `%01`
  yields a raw `0x01` that survives `trim()`/`take()` and is emitted raw. Expected result:
  invalid JSON → `res.json()` throws in the portal → swallowed by `catch(e){}` → **both portals
  stop updating for every device** until the question is dismissed.
- **⚠ Unverified:** this is a reading of the code, not a reproduced bug. **Write the failing
  test first** — submit a question containing `%01`, then assert `/api/state` parses.
- **Fix:** one small JSON writer (`JsonWriter.string(…)`, `.obj(…)`) with complete escaping
  (`U+0000`–`U+001F`); replace all 12 template sites.
- **Guard:** `RemoteCompanionServerTest` — control characters round-trip and the response parses.
- **Effort:** S–M · **Risk:** low

### F-05 · Remove the `else` from the `when` over `SlideElement`
- **Where:** `ui/components/CommandPalette.kt:58`
- **Problem:** `else -> false` on a **sealed** hierarchy. Search therefore finds nothing inside
  Mermaid source, poll options, math formulas or image alt text. It is also the exact shape
  that caused EXP-3 (tables/images/polls silently vanishing from PNG export).
- **Fix:** enumerate all cases explicitly; make searchable ones searchable. **Adopt as a
  standing rule: never write `else` on a `when` over `SlideElement`** — the sealed interface
  already gives the Visitor guarantee for free, so no pattern is needed, only the discipline.
- **Guard:** test searching for text that lives only inside a poll option and a diagram.
- **Effort:** S · **Risk:** very low

### F-06 · Extract `PacingCalculator` (pure) and `TalkTimer` (injected clock)
- **Where:** `state/PresentationState.kt:262-312, 329-349, 744-761`
- **Problem:** the pacing formula is a headline product feature and is entangled with a live
  monotonic clock, so it is verified only by reading it. `PRF-4`'s drift-free bookkeeping has
  no test.
- **Fix:** `PacingCalculator` becomes a pure function of
  `(elapsed, targetTotal, slideIndex, slideCount) → PacingStatus + delta`. `TalkTimer` owns
  run/pause bookkeeping behind an injected `Clock`/time source.
- **Guard:** new `PacingCalculatorTest` (boundary cases: ±20 s, +75 s, overtime, no target) and
  `TalkTimerTest` with a fake clock — neither is possible today.
- **Effort:** M · **Risk:** low (the arithmetic moves verbatim)

---

## Tier 1 — Highest structural payoff

### F-07 · Make the companion route table declarative
- **Where:** `remote/RemoteCompanionServer.kt:49` (`PRESENTER_ENDPOINTS`), `:430`
  (`WRITE_ENDPOINTS`), `:467` (`when (path)`)
- **Problem:** a route's security policy is **membership in two separate sets**, checked against
  a **third** dispatch. Adding an endpoint takes three coordinated edits and **nothing fails if
  one is forgotten**: omitted from `WRITE_ENDPOINTS` it is silently exempt from POST-only
  (SEC-3) *and* rate limiting (SEC-5); omitted from `PRESENTER_ENDPOINTS` it is silently
  unauthenticated (SEC-2). Three load-bearing invariants rest on remembering two set literals.
- **Fix:**
  ```kotlin
  private data class Route(
      val path: String,
      val method: HttpMethod,
      val scope: Scope,          // Public | Audience | Presenter
      val handler: (Request, AudienceSession) -> Response
  )
  ```
  Policy follows from `scope`/`method`; a route cannot exist without declaring its scope.
- **Guard:** **structural** assertions replacing today's path enumeration — "every non-`Public`
  route rejects a missing token", "every mutating route rejects GET", iterated over the table.
- **Effort:** M · **Risk:** medium — this is security-critical code; keep the existing
  `RemoteCompanionServerTest` green at every step.
- **This is the single highest-value item in the backlog.**

### F-08 · Extract `AudienceSession` and narrow the server's dependency
- **Where:** `state/PresentationState.kt:104-106, 797-861`; `remote/RemoteCompanionServer.kt:118`
- **Problem:** `start(state: PresentationState)` hands a network worker thread the entire
  application state — file dialogs, theme unlocking, structural slide editing — to use ~12
  members (ISP/DIP).
- **Fix:** `AudienceSession` owns the Q&A queue and ballots with their SEC-5 bounds. Define
  ports `DeckControl` (navigate/blackout/timer) and `AudienceSession`; `PresentationState`
  implements both; the server depends only on them.
- **Guard:** `RemoteCompanionServerTest` constructs a fake port instead of a full state.
- **Effort:** M · **Risk:** medium

### F-09 · Extract the HTTP request parser
- **Where:** `remote/RemoteCompanionServer.kt:321-423`
- **Problem:** the parser is fused to a live `Socket`, so the SEC-7 caps (request-line bytes,
  header count, body size, chunked rejection) and the EXP-4 byte-vs-char body fix can only be
  exercised by opening a real connection.
- **Fix:** `HttpRequestParser.parse(input: InputStream): Request?` — pure over a stream.
- **Guard:** unit tests feeding crafted byte arrays for each cap and each malformed shape.
- **Effort:** M · **Risk:** low–medium

### F-10 · Move the two web portals out of the Kotlin object
- **Where:** `remote/RemoteCompanionServer.kt:748-946` and `:948-1121` — **375 lines, 34% of the file**
- **Problem:** HTML/CSS/JS embedded in a Kotlin string literal gets no syntax highlighting, no
  linting and no tooling. The SEC-1 `textContent`-only discipline is enforced only by a comment
  asking future readers not to "simplify" it back into template literals.
- **Fix:** `resources/portal/remote.html` + `audience.html`, read at startup by a
  `PortalAssets` loader; keep the `${BuildInfo.DISPLAY_VERSION}` substitution as an explicit
  placeholder replace.
- **Verify:** packaging — confirm the resources ship in `createDistributable` output.
- **Guard:** existing SEC-1 tests; add one asserting the assets load and contain no `innerHTML`.
- **Effort:** M · **Risk:** medium (packaging is the real risk, not the code)

---

## Tier 2 — Decomposing `PresentationState`

Do these behind a **facade**: `PresentationState` keeps its public surface and delegates, so
the 15 UI call sites need not change in the same commit. Each row ships working.

### F-11 · Extract the easy, self-contained collaborators
- **Where:** `state/PresentationState.kt`
- **Fix:** four extractions, ~350 lines out of the god object:
  - `FindReplaceController` — lines 150-260 (8 properties, 7 functions, the `derivedStateOf` cache)
  - `AnnotationLayer` — lines 112-115, 673-709
  - `UiFlags` — the 7 dialog booleans + `showWelcome`
  - `SampleDecks` — lines 1198-1321 (~125 lines of markdown literals; content, not state)
- **Guard:** existing `EditorFindAndReplaceTest`, `PresentationStateTest`.
- **Effort:** M · **Risk:** low

### F-12 · Extract `ParkingLotStore`
- **Where:** `state/PresentationState.kt:108-110, 565-607, 863-982`
- **Note:** the most invariant-dense area in the class (the markdown-is-the-storage round trip,
  `directiveKey` matching, the resurrect-on-keystroke bug). **Move the comments with the code**
  — they are the record of why it is shaped this way.
- **Guard:** `ParkingLotDeleteTest`, `ParkingLotAndThemeTest`, `CharacterizationTest`.
- **Effort:** M · **Risk:** medium

### F-13 · Extract `DeckDocument` + `SlideNavigator` — the core of the god object
- **Where:** `state/PresentationState.kt:39-47, 429-448, 499-550, 609-671, 998-1160`
- **Problem:** document, project-file mapping, structural editing and navigation are one
  tangle. `COR-1`/`COR-2`/`COR-3` all live here.
- **Fix:** `DeckDocument` owns text + parsed slides + project files + structural edits +
  autosave scheduling (building on the existing `SlideDocument`). `SlideNavigator` owns index
  and bounds.
- **Do this last** of the state work — largest blast radius.
- **Guard:** `PresentationStateTest`, `CompanionDeckTest`, `DeckProjectManagerTest`, `SlideDocumentTest`.
- **Effort:** L · **Risk:** high

### F-14 · Invert the remaining singleton dependencies
- **Where:** `state/PresentationState.kt:467, 491, 648, 655, 722` (fully-qualified
  `DeckProjectManager` calls inside method bodies), plus `FileManager`, `DeckExporter`
- **Fix:** narrow interfaces (`ProjectRepository`, `FileDialogs`, `DeckExport`), objects as
  default wiring. **Precedent already in-repo:** `AdaptiveContrastEnforcer : IContrastEnforcer`,
  `ThemePaletteValidator : IThemeValidator`. No DI framework — constructor defaults suffice.
- **Effort:** M · **Risk:** low

---

## Tier 3 — Parser cognitive complexity

### F-15 · Remove the duplicated poll-directive block
- **Where:** `core/parser/MarkdownSlideParser.kt:246-252` and `:264-270` — copy-pasted verbatim
- **Fix:** one private `parsePollDirective(value, title)` helper.
- **Effort:** S · **Risk:** very low · *Do this before F-17; it shrinks the surface being moved.*

### F-16 · Replace the three setter lambdas in `applyDirective`
- **Where:** `core/parser/MarkdownSlideParser.kt:484-520`
- **Problem:** takes `setLayout`, `setBg`, `setTransition` callbacks purely because its targets
  are locals of the 364-line function — a smell that vanishes once state is an object.
- **Fix:** a `SectionContext` holder (Parameter Object), or return a `DirectiveResult`.
- **Effort:** S · **Risk:** low

### F-17 · Decompose `parseSlideSection` into ordered block rules
- **Where:** `core/parser/MarkdownSlideParser.kt:119-482` — **one 364-line function**, 12 mutable
  locals, 5 nested mutating closures, a 9-branch `if … continue` chain
- **Problem:** correctness depends on **implicit branch ordering** (tables before headings,
  metric before paragraph, comment-skip before paragraph) that is nowhere named or asserted;
  and the `flushX()` ritual is repeated ~20 times, where one omission is a silent element loss.
- **Fix:** Chain of Responsibility —
  ```kotlin
  private interface BlockRule {
      fun matches(line: String, ctx: SectionContext): Boolean
      fun consume(line: String, ctx: SectionContext)
  }
  private val RULES = listOf(CodeFenceRule, MathBlockRule, DirectiveRule, NoteRule,
      TableRule, HeadingRule, ListRule, QuoteRule, ImageRule, MetricRule,
      CommentRule, ParagraphRule)   // order is the specification
  ```
  `SectionContext` owns the accumulating state and `flushPending()`, called **once by the
  dispatcher** instead of by every branch. Rule bodies are today's branch bodies, moved verbatim.
- **Guard:** `MarkdownSlideParserTest`, `MarkdownDirectivesTest`, `CharacterizationTest`,
  `SmartLayoutClassifierTest` — plus a new test asserting rule *order*.
- **Effort:** L · **Risk:** medium

---

## Tier 4 — UI hygiene

### F-18 · Split the oversized composables
- **Where:** `PresenterView.kt` (597-line composable, nesting ~8), `EditorWorkspace.kt` (549,
  ~9), `FullscreenDeck.kt` (330, ~6)
- **Problem beyond readability:** `PresenterView.kt:58` reads `state.elapsedSeconds` in the
  **top-level body**, making the entire 597-line composable the invalidation scope for a value
  that changes every 200 ms. Children are skipped only when their parameters are stable, and
  `Slide` carries `List<SlideElement>`, which Compose treats as unstable — so both
  `SlideSurface` previews are likely re-executing several times a second. Real cost on a
  product that advertises 120 FPS.
- **Fix:** extract `PresenterHeader`, `SlidePreviewColumn`, `NotesPanel`, `QaPanel`,
  `DeliveryControlBar`; collapse the three copy-pasted tab buttons (`:268-330`) into one
  parameterised `PresenterTab`; extract the ~70-line inline Q&A card (`:440-512`). Same
  treatment for the filmstrip card in `EditorWorkspace`.
- **Working rule:** no composable over ~80 lines; a repeated block becomes a parameterised
  private composable rather than a third copy.
- **Guard:** `SlideRenderingTest` / `RenderAllProbe` PNG probes.
- **Effort:** M · **Risk:** low

### F-19 · One shortcut registry
- **Where:** `Main.kt:101-148`, `FullscreenDeck.kt:58-114`, `AppTooltip(shortcut = …)` literals
  scattered across the UI, and the README table — **four sources of truth**
- **Fix:** `AppCommand(id, label, shortcut, scope, action)` registry (Command pattern). Both key
  handlers dispatch through it; `AppTooltip` reads its label from it; the README table can be
  generated.
- **Bonus:** `CommandPalette` is documented as a "Spotlight Command Palette" but today only
  searches and jumps to slides — it executes no commands. This registry is what makes the name
  true, at almost no extra cost.
- **Guard:** new test asserting every command has a unique, non-conflicting binding per scope.
- **Effort:** M · **Risk:** low

---

## Tier 5 — Loose ends found while reading

Not refactoring; small correctness/accuracy items.

### F-20 · `DeckExporter` never cancels its scope
- **Where:** `export/DeckExporter.kt:55` — `CoroutineScope(Dispatchers.IO + SupervisorJob())`
  with no cancellation path. **Fix:** tie to the app lifecycle, or use a bounded dispatcher.
  **Effort:** S

### F-21 · Exported HTML is not self-contained
- **Where:** `export/DeckExporter.kt:431-435` — KaTeX and Mermaid load from
  `cdn.jsdelivr.net`. The README advertises "single-file **self-contained** HTML presentations
  with **embedded** KaTeX, Mermaid JS". The export needs network access to render, which
  defeats the purpose for an offline conference room. **Fix:** either inline the libraries or
  correct the claim. **Effort:** S (docs) / M (inlining)

### F-22 · Stale test counts in docs
- **Where:** `QUALITY_BASELINE.md` says 221 tests, `RENDERING_STATUS.md` says 204. Actual is
  **235**. Both carry a "last reviewed 2026-08-05" date predating the last three commits.
  **Effort:** S

### F-23 · `kotlinx-coroutines-swing:1.1.0`
- **Where:** `build.gradle.kts:72`, declared against core `1.11.0`. Harmless in practice — the
  coroutines BOM lifts it to 1.11.0 — but it reads as a typo, and nothing in `src/` references
  `Dispatchers.Swing`/`Main`, so the dependency looks unused entirely. **Fix:** align the
  version or drop the dependency. **Effort:** S

### F-24 · Five rendering cases still unverified
- **Where:** `RENDERING_STATUS.md` — 5 cases marked ❓ "rendered but not inspected" against 8
  verified. The doc is explicit that "tests pass" is not evidence here. **Fix:** regenerate via
  `RenderAllProbe` and actually look at the PNGs. **Effort:** S

---

## Suggested sequencing

```
F-01 F-02 F-03 F-05 → hermetic, leak-free, honest tests   (half a day, unblocks everything)
F-04 F-07           → companion server security made structural
F-06                → pacing formula finally testable
F-08 F-09 F-10      → server drops to ~600 lines, no longer holds the whole app
F-15 F-16 F-11      → cheap wins before the big moves
F-12 F-14 F-17      → the invariant-dense extractions
F-13                → the core of the god object, last
F-18 F-19           → UI hygiene, any time
F-20…F-24           → opportunistic
```

Rule for every step: the 235-test suite stays green, and the `SEC-*` / `COR-*` / `PRF-*` /
`MMD-*` / `EXP-*` / `DED-*` identifiers **move with the code they constrain** — they are
permanent per [`QUALITY_BASELINE.md`](./QUALITY_BASELINE.md) and must never be renumbered.
