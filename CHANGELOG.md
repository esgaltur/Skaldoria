# Changelog

All notable changes to **Skaldoria** will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

Roadmap and delivery work. The candidate feature set is catalogued in
[`docs/FEATURE_INDEX.md`](./docs/FEATURE_INDEX.md); the connectivity design is
[ADR-005](./docs/ADR_COMPANION_LINK_ESTABLISHMENT.md).
Test suite: **235 to 617 tests** (551 in the app, 66 in `:markdown-core`), zero compiler warnings.

### Performance

Measured, not guessed — see [`docs/PERFORMANCE_BASELINE.md`](./docs/PERFORMANCE_BASELINE.md).

- **The editor's syntax highlighter compiled three regexes per line, on every keystroke.** They
  were `Regex(...)` literals inside the per-line loop, and the highlighter runs on every
  composition of the text field. On an 886-line deck that was roughly 900 `Pattern.compile`
  calls per character typed. **-37%** on the highlighter.
- **Project mode reparsed every file in the deck just to display the editor's text.** The
  slide→file map is derived by parsing every file, nothing cached it, and `currentEditorText`
  reaches it on every composition. Now computed once per project change: **1.09 ms to 1 µs** on
  that path, with four guards against the stale-cache case (which would be COR-3 — the editor
  writing to the wrong file).
- **Caret tracking allocated the whole document per cursor move.** Slide ⇄ offset mapping built
  a list of every line and a boxed integer per line start to answer a question about one line.
  Now a forward scan that stops. **-70%**.
- **Moving the caret re-highlighted the entire document.** `VisualTransformation.filter()` runs on
  every recomposition, not only on text change, so an arrow key rebuilt every span in the deck to
  produce a byte-identical result. Memoised on its inputs: **~425 µs to 1–2 µs** on that path,
  which also covers selection drags, focus changes and slide navigation.
- **The parking-lot scan ran in full on decks that have no parking-lot items.** It reconciles on
  every deck change, and most decks have never used the feature. A pre-scan short-circuits it:
  **640 µs to 31 µs**. The guard deliberately covers *both* extraction paths — directive comments
  and checkbox task lists — because the obvious `parking-lot:`-only check would have silently
  dropped every checkbox-derived item.

  Together with PRF-7: **~2.4 ms to ~1.08 ms per keystroke** on an 886-line deck.

- **The benchmark itself was unreliable, and that is the finding worth keeping.**
  `PerformanceProbe` timed a single pass and discarded every result. Back-to-back runs of an
  unchanged binary produced **579 µs and 1.27 ms** for the same measurement — a 2.2× spread, wide
  enough to invent a regression that never existed. Three plausible causes were investigated
  against that noise before it was recognised as noise. The probe now reports the fastest of three
  passes and routes every result through a sink. **Figures in
  [`docs/PERFORMANCE_BASELINE.md`](./docs/PERFORMANCE_BASELINE.md) that predate this change should
  be re-run before being trusted.**

### Added
- **`:markdown-core`, a Gradle module holding the markdown engine.** The parser, block rules,
  layout classifier and slide model, with **zero Compose on its compile classpath** — verified,
  not assumed. It compiles, tests and benchmarks without a UI toolkit, which is what makes the
  parser measurable in isolation and would let a non-desktop front end consume the engine.
  `AnnotationStroke` moved back out to the app module: it was the only type in the slide model
  needing Compose, and it is a drawing concern rather than a parsing one.

  A note for anyone extracting the next module: a module boundary stops Kotlin smart-casting
  nullable `val`s, because another module could in principle change them. That produced 11 compile
  errors here — mechanical to fix, but not free.
- **The editor has a caret.** The source pane used the `String` overload of `TextField`, so the
  application had no handle on selection, caret offset or scroll position for the one control a
  user spends all day in. Two of the three reported defects below were consequences of that one
  gap rather than separate bugs.
- **Selecting a slide moves the editor to it.** Clicking thumbnail #37 in a fifty-slide deck
  used to repaint the preview and leave the source showing line 1. The filmstrip, the grid
  overview and the command palette all inherit this, because all three route through the same
  call.
- **Moving the caret selects the slide.** Typing in slide 12's source selects slide 12 in the
  preview and filmstrip. There is a toggle in the editor header, defaulting to on.
- **Presenter clickers blank the screen.** Forward and back already worked — a clicker is an
  ordinary HID keyboard and <kbd>PageUp</kbd>/<kbd>PageDown</kbd> were bound. The blank-screen
  button was not: it sends <kbd>.</kbd> or <kbd>,</kbd>, the convention the hardware is built
  against, and only <kbd>B</kbd> and <kbd>W</kbd> were bound. Both pairs now work.
- **Undo/redo for structural slide edits.** Deleting a slide was a single click on the
  filmstrip with no way back — the only undo in the application was for annotation strokes.
  Delete, move, duplicate and insert are now reversible in both single-file and project mode
  (<kbd>Ctrl+Z</kbd> / <kbd>Ctrl+Shift+Z</kbd> / <kbd>Ctrl+Y</kbd>, plus toolbar buttons).
- **Presentation toolbar visibility.** The HUD covered the bottom of every slide — the last
  lines of a full-bleed code slide and the deck's own footer — and could not be hidden. It now
  auto-hides when the pointer rests, cycles with <kbd>H</kbd>, and the choice persists.
- **Jump to a slide by number** during a presentation: type the number, press <kbd>Enter</kbd>.
- **CI pipeline** (`.github/workflows/ci.yml`): compile and test on Linux, Windows and macOS,
  with a job that fails the build on any Kotlin compiler warning.

### Fixed
- **Code fences lost their language unless the info string had exactly one supported shape.**
  ` ```js {highlight=2} ` and ` ```python title="demo.py" ` are ordinary markdown that every other
  tool accepts. The parser recognised only `language [1,3-5]`, so anything else fell through: the
  language came back blank, and a blank language defaults to `kotlin` — so JavaScript rendered
  with Kotlin syntax colouring, and any `[1,3-5]` line highlighting on such a fence was discarded.
- **Tilde fences were not code.** A `~~~python` block was parsed as prose by the slide parser and
  styled as prose by the editor. Both now treat `~~~` and ` ``` ` identically.
- **A fence can no longer be closed by the wrong terminator.** Closing now requires the same
  marker, at least as long, and no info string — so a ` ``` ` nested inside a ` ```` ` block stays
  code instead of ending the outer fence.

  All three came from the same root cause: **four separate pieces of code each decided
  independently what opened a fence** — the slide splitter, the parser's fence regex, the block
  rules, and the editor's highlighter — and nothing compared them. They now share `FenceRules`,
  and `FenceLexerAgreementTest` fails if they ever diverge again.
- **`***` and `___` split a deck with no sign of it in the editor.** All three thematic-break
  forms end a slide, but the editor styled only an exact `---`, so the other two broke a slide
  invisibly. Both sides now defer to `ThematicBreakRules`.
- **Math formulas were coloured as prose.** The editor styled the `$$` delimiters and left the
  formula between them as ordinary text, because it tracked no block state. It now tracks `$$`
  exactly as it tracks code fences. A one-line `$$ x = 1 $$` correctly does not open a block.
- **The speaker console answered to no keyboard shortcut at all.** `PresenterView` had no key
  handling, and its window is `alwaysOnTop` — so it is the window a speaker actually looks at
  and the one holding focus for most of a talk. <kbd>H</kbd>, the arrows, blackout, the laser,
  and a presenter clicker's Page Up / Page Down were all silently dead there. The deck's
  dispatch is now shared (`DeckKeyHandler`) and both windows answer to the full set, with a
  window-level handler as a backstop so losing focus inside a window cannot starve it.
- **The editor's find buttons appeared to do nothing.** Search was complete and unit-tested:
  matches were found, the active one was restyled, the count updated. Nothing scrolled, so the
  match was off screen and the only feedback was a badge changing in a corner. ▲/▼ also moved
  focus out of the query field, so <kbd>Enter</kbd> stopped advancing until you clicked back.
  Both fixed; the bar now also states what it is searching, because in project mode it covers
  one file and answering `No matches` without saying so reads as a broken feature.
- **Slide transitions had no effect.** All four `SlideTransition` values were parsed, persisted
  and offered in a picker while the renderer hardcoded a fade. Every value now renders as
  itself, and a per-slide `transition:` directive overrides the deck default.
- **Five documented keyboard shortcuts did not exist.** <kbd>Ctrl+E</kbd>, <kbd>T</kbd>,
  <kbd>Home</kbd>, <kbd>End</kbd> and <kbd>Backspace</kbd> were in the README shortcut table and
  bound nowhere in the source.
- **Exported HTML required internet access.** KaTeX and Mermaid were loaded from a CDN, so an
  exported deck degraded to raw source offline — the situation the export exists for. Maths and
  diagrams are now rendered by Skaldoria at export time and embedded, with the source preserved
  as alt text and a `<details>` fallback.
- **The companion advertised unreachable addresses.** Ranking followed the default route, which
  a hotspot or tether interface never holds, so the pairing QR pointed at the laptop's own
  ethernet. Bluetooth PAN — which carries IP and needs no new code — was additionally excluded
  by a name-based denylist.
- **The slide footer misreported diagram slides**, labelling a sequence diagram
  "Architecture / Flow Diagram" while the diagram's own header said otherwise.

### Known gaps
- Editor ⇄ slide synchronisation and find-result reveal remain open; both wait on the caret
  foundation in [ADR-004](./docs/ADR_EDITOR_SYNC_AND_PRESENTATION_HUD.md) Phase 2.
- The CI workflow has never been executed.
- **Tables written without outer pipes (`a | b` / `---|---`) are unsupported.** Ordinary GFM, and
  neither the parser nor the highlighter handles it — they agree, and both are wrong, so this is a
  missing feature rather than a divergence.
- **Test suites leak `PresentationState` coroutine scopes.** A mutation schedules a debounced
  autosave on the instance's own scope; `dispose()` cancels it, but **19 test files construct a
  `PresentationState` and only 4 dispose it**. A leaked job outlives its test and writes the
  process-wide draft file, which is what made `DraftRecoveryTest` fail intermittently — twice
  during this work, each time passing on re-run.

  `DraftRecoveryTest` now waits out the debounce before clearing, which makes *that* symptom
  deterministic. **The root cause is untouched**: the other 15 files still leak, nothing enforces
  disposal, and any future test reading shared state can be hit the same way. The durable fix is
  disposal discipline, ideally enforced rather than remembered.

## [1.2.0] - 2026-08-05

A correctness and hardening release, following a systematic pre-release review of the codebase.
The invariants established are recorded in [`docs/QUALITY_BASELINE.md`](./docs/QUALITY_BASELINE.md).
Test suite: **70 to 221 tests**.

### Security - companion server
- **Stored XSS fixed.** Audience-submitted text reached the presenter remote and every other
  audience device through `innerHTML`. Both portals now build DOM with `textContent`, and inline
  `onclick` attributes were replaced with listeners so a quote in an id cannot break out.
- **Session authentication added.** A 128-bit `SecureRandom` token, regenerated per server start
  and cleared on stop, gates presenter-scope routes (`/api/action`, `/api/qa/dismiss`). Compared
  in constant time. Delivered in the pairing QR and returned as an `X-Skaldoria-Token` header.
- **Speaker notes are no longer readable by the audience.** `/api/state` returns them empty
  without the token.
- **CSRF closed.** State-changing endpoints are `POST`-only, and all wildcard
  `Access-Control-Allow-*` headers were removed - the previous `*` let any site the presenter
  visited drive the deck.
- **Rate limiting and vote integrity.** Per-device token bucket on write endpoints; polls now
  record one ballot per device (voting again replaces, never stacks); question queue and text
  length are bounded.
- **Denial-of-service surface reduced.** Bounded worker pool (was unbounded), request-line and
  header caps, and an explicit `411` for unsupported chunked encoding.
- **Path traversal fixed.** Deck manifests are untrusted input; slide paths are canonicalised and
  rejected unless inside the project, on both load *and* save.

### Fixed - diagrams
- **Flowcharts follow their real topology.** A layered (Sugiyama-style) layout engine replaces the
  previous renderer, which emitted nodes in parse order as a straight chain - so a branch was
  drawn as a queue. Includes cycle breaking, layer compaction and barycentre crossing reduction.
- **Sequence diagrams render as sequence diagrams.** The old renderer drew a table of
  `From -> To | message` rows with no lifelines or arrows. Rebuilt on a dedicated model with
  lifelines, all eight arrow types, `loop`/`alt`/`else`/`opt`/`par` frames, notes, activation bars
  and self-calls.
- **`subgraph ... end` is supported**, drawn as a labelled frame around its members. Previously
  `subgraph`, `end`, `classDef` and `class` were each registered as *nodes*, so any diagram using
  them rendered phantom boxes for keywords that appear nowhere in the source.
- `[(datastore)]` nodes parse with a clean label and a cylinder shape; the square-bracket branch
  used to win and leave the parentheses in the visible text.
- Mid-link edge labels (`A -- yes --> B`) and `{{hexagon}}` nodes are parsed; both were silently
  dropped, which orphaned nodes and wrecked layout.
- Chained edges (`A --> B --> C`) no longer lose everything after the first pair.
- The `((circle))` node shape is reachable (alternation order); edge labels no longer overlap nodes.
- Diagrams scale to fit instead of clipping.

### Added
- **Images render.** `![](...)` now loads from the deck folder, an absolute path, or an `http(s)`
  URL - off the UI thread, cached, with timeouts and a size cap. Previously images were parsed and
  exported to HTML but never drawn. Unresolvable paths show the alt text and the reason.
- **Network address picker.** The companion QR advertised the first site-local address found,
  which on a machine with VirtualBox/VMware/Hyper-V adapters was a host-only address no phone
  could reach. Detection now prefers the adapter carrying the default route, ranks virtual
  adapters last, and the pairing dialog lets you choose.
- **Version is visible** in every window title, on the welcome screen, in exports and in both
  companion portals, generated from a single `appVersion` in `build.gradle.kts`.
- **Companion test deck** (`examples/companion_test_deck`) - 17 slides covering every layout with
  two live polls, Q&A and a seeded parking lot, for exercising the phone companion end to end.

### Fixed - editing and content
- **Slide operations acted on the wrong slide.** Editing used its own splitter that recognised
  only a literal `---`, disagreeing with the parser about `##` heading splits and `----` rules.
  Boundaries now come from the parser itself, so delete/move are exact.
- **Slide edits in project mode were silently discarded** - they were written to the compiled
  markdown while the project kept the old per-file content.
- **The per-slide editor wrote to the wrong file** whenever any file contained more than one slide.
- **Parking lot deletes now stick.** They were in-memory only, so the `<!-- parking-lot: ... -->`
  comment stayed in the file and the item returned on the next keystroke. Items carry a persisted
  `id:`, and captured questions are written to the deck.
- HTML comments no longer render as literal `<!-- ... -->` text on a slide.
- A paragraph beginning with a number (e.g. `2024 Roadmap Overview`) is no longer promoted to a
  giant KPI slide.
- A second heading in a section no longer leaks its raw `##` markers into the slide.
- Opening a `.mdpres` manifest opens the **project**, not the manifest's own JSON. Classification
  is by validation, not by file extension, so an unrelated `.json` cannot be mistaken for a deck.

### Fixed - export
- **Exported HTML/PDF used the wrong colours.** `Color.value` packs channels in the high bits;
  the exporter read the low 32 bits.
- HTML attribute injection closed (`'` escaped, image URLs sanitised, `javascript:`/`data:` refused).
- PNG export no longer silently drops tables, images and polls; long text is ellipsised instead of
  running off the canvas.
- PDF export no longer deadlocks on an undrained subprocess pipe, and the ZIP stream is closed on
  failure.

### Fixed - performance and correctness
- Audience/poll mutations from HTTP threads are applied inside a Compose snapshot, removing a
  swallowed `ConcurrentModificationException`.
- Autosave is debounced instead of writing the whole document on every keystroke.
- Find/replace no longer re-scans the entire document on every recomposition.
- The talk timer is derived from a monotonic clock instead of counting loop iterations, so it no
  longer drifts; its coroutine scope is cancelled on exit.
- Exports run off the UI thread (PDF export could freeze the app for 15 seconds).
- Theme and editor font size persist across launches; config writes are atomic.
- Audience question ids use `UUID`; project manifests escape JSON; slide files sort naturally
  (`2_x.md` before `10_x.md`).

### Fixed - UI
- Slide-overview search, parking-lot capture box and answer editor no longer clip their
  placeholders: Material 3 text fields enforce a 56.dp minimum and 16.dp vertical padding, so
  pinning them smaller crops the content. Replaced by a shared `CompactTextField`.
- Short parking-lot buttons no longer clip their labels (`contentPadding` reduced rather than
  height forced below the Material minimum).

### Changed
- **Removed deprecated API usage.** The window icon no longer calls the deprecated
  `painterResource(String)` behind a `@Suppress("DEPRECATION")`; the project compiles with zero
  deprecation warnings.
- `runCatching` replaced with explicit `catch (Exception)` in the image pipeline, rethrowing
  `CancellationException` so an aborted load is not reported as a decode failure.
- Unreachable `NodeShape` values and the unused `styleClass` field removed.

### Testing
- Added a **headless rendering harness** (`SlideRenderingTest`, `ImageComposeScene`) that renders
  slides and asserts content actually reaches the canvas. It was validated by reintroducing a
  regression that blanked every slide - unit tests and a clean launch had both missed it.

---

## [1.1.0] - 2026-08-04

### Added
- **Find & Replace in Slide Source Editor**:
  - In-editor search bar with real-time token highlighting and active match focus (<kbd>Ctrl+F</kbd> / <kbd>Ctrl+H</kbd>).
  - Match count indicator with cyclic previous (<kbd>Shift+Enter</kbd>) and next (<kbd>Enter</kbd>) navigation.
  - Search option toggles: Match Case (`Aa`), Whole Word (`\b`), and Regular Expressions (`.*`).
  - Seamless Single Replace and Replace All capabilities directly synchronized with live slide preview.
- **Presentation Parking Lot & Unanswered Questions (Aside)**:
  - Slide-out Parking Lot aside drawer in the editor workspace for capturing unanswered questions and follow-ups.
  - Interactive checkboxes for tracking open vs answered items.
  - Expandable text areas for typing answers and resolution notes.
  - Presenter Console Parking Lot tab with 1-click **Park for Later** action on live audience Q&A items.
  - Markdown persistence supporting `<!-- parking-lot: [ ] Question | Answer | slide:3 -->` directives and task lists. *(Writing back to the deck did not actually work until 1.2.0.)*
  - 1-click **Copy Markdown** action to export the follow-up checklist to the clipboard.
- **Algorithmic Speaker Rhythm & Pacing Gauge**:
  - Live pacing drift computation formula: $\Delta t = t_{elapsed} - \left( \frac{T_{target}}{N_{total}} \right) \cdot i_{current}$.
  - Real-time Presenter HUD visual gauge with color-coded status badges: Green (On Track), Cyan (Ahead), Amber (Behind), Red (Overtime).
- **LaTeX Mathematical Formula Engine**:
  - Recursive-descent brace matching supporting arbitrarily nested fractions (`\frac{a}{b}`), roots, subscripts, and superscripts.
  - Complete mapping for Greek mathematical glyphs (`\alpha`, `\beta`, `\Delta`, `\Omega`, `\pi`) and operators.
- **Restricted Corporate Themes & Access Code Gate**:
  - Interactive `UnlockCorporateThemeDialog` with instant live theme activation upon validation.
- **WCAG 2.1 AA Adaptive Contrast Science**:
  - `AdaptiveContrastEnforcer` and `ColorScience` engines calculating relative luminance and adjusting HSL lightness.
  - Guarantees $CR \ge 4.5:1$ across all light surfaces, eliminating low-contrast light gray syntax collisions.
- **Pure Kotlin QR Code Generator & Visual Pairing**:
  - Zero-dependency ISO/IEC 18004 Model 2 QR generator (`QrCodeGenerator`) rendering directly on GPU-accelerated Compose canvases (`QrCodeView`).
  - Interactive QR switcher in `RemotePairingDialog` allowing instant smartphone scanning for both Speaker Remote and Audience Portal.
  - Scannable high-contrast QR badge embedded directly into live `PollSlide` presentation footers for frictionless audience participation.
- **Zero-Dependency Native Socket Companion Server**:
  - Re-engineered HTTP/1.1 micro-server using standard `java.net.ServerSocket` in `java.base`, eliminating all `com.sun.net.httpserver` JPMS module errors across minimal JREs.
  - Sub-millisecond cold boot latency and 0 KB external footprint.
  - Multi-threaded executor with automatic port-fallback across 50 ports and ephemeral fallback.
  - CORS preflight (`OPTIONS`) handling and resilient error boundaries. *(The wildcard CORS headers were removed in 1.2.0 - they were a CSRF vector.)*
  - Added `/api/parking-lot/add` endpoint for remote companion integration.
  - Documented architectural decisions and protocol evaluations in [ADR-001](./docs/ADR_COMPANION_SERVER_ARCHITECTURE.md).

---

## [1.0.0] - 2026-08-04

### Added
- **Core Presentation Engine**:
  - Pure standard Markdown parser with automatic layout heuristics (Hero Title, Split Text/Code, Split Text/Media, Quote, Data Table, Metrics, Bullets).
  - 120 FPS GPU-accelerated slide transitions (Crossfade, Slide Horizontal, Slide Vertical, Zoom Scale).
  - Speaker notes extraction via standard HTML comments (`<!-- note: ... -->`).
- **Multi-File Projects (`.skaldoria` / `.mdpres`)**:
  - Support for modular slide decks split across multiple `.md` files.
  - One-click slide file creation and automatic indexing.
  - Per-slide isolated editing studio and compiled full-deck overview toggle.
- **Studio & Editor Workspace**:
  - Live split editor with real-time responsive slide preview.
  - Dynamic font zoom controls (`Ctrl++`, `Ctrl+-`, `Ctrl+0`).
  - Rich interactive tooltips on all controls.
  - Interactive filmstrip with slide thumbnails and add-slide action cards.
- **Presenter & Stage Controls**:
  - Dedicated dual-window speaker console with elapsed clocks, live timers, current slide notes, and next slide previews.
  - Live canvas annotation mode (`W` key) with pens, highlighters, laser pointer, and clear board.
  - Blackout mode (`B` key) and theme switcher (`T` key).
  - Spotlight Command Palette (`Ctrl+K`) with global keyword search across titles, code, and speaker notes.
- **Export & Portability**:
  - Standalone self-contained HTML deck generator with responsive navigation.
  - Native standalone Windows packaging (`.exe`, `.msi`) with embedded runtime.
