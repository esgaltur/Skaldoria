# Feature Index — Long-Term Product Roadmap

**Version:** 1.2.0 · **Drafted:** 2026-08-06 · **Status:** Living document

The catalogue of what Skaldoria could become. One row per candidate feature, each with a
permanent identifier, so a feature can be referenced in an issue, a commit, or a code comment
years from now and still mean the same thing.

**Nothing here is a commitment.** Inclusion means "considered and worth recording", not
"scheduled". Rejections are kept with their reasoning, so a settled question is not re-opened
by someone who was not in the room.

---

## Contents

- [How to use this document](#how-to-use-this-document)
- [Identifier namespace](#identifier-namespace)
- [The highest-value finding: features already paid for](#the-highest-value-finding-features-already-paid-for)
- [AUT — Authoring & editor](#aut--authoring--editor)
- [DEL — Delivery & presentation](#del--delivery--presentation)
- [DIA — Diagrams](#dia--diagrams)
- [THM — Theming & design](#thm--theming--design)
- [MED — Media](#med--media)
- [OUT — Export & interoperability](#out--export--interoperability)
- [AUD — Audience & companion](#aud--audience--companion)
- [PRJ — Deck & project management](#prj--deck--project-management)
- [PLT — Platform & quality](#plt--platform--quality)
- [Horizons](#horizons)
- [Rejected, with reasons](#rejected-with-reasons)
- [Related documents](#related-documents)

---

## How to use this document

Each row carries:

| Field | Meaning |
| :--- | :--- |
| **ID** | Permanent. Never renumbered, never reused, even if the feature is dropped. |
| **Status** | ✅ shipped · 🟡 partial · 📋 proposed · 🕓 deferred · ❌ rejected |
| **Size** | `S` ≤ half a day · `M` 1–2 days · `L` 3+ days · `XL` multi-week |
| **Depends** | Other IDs that must land first. |

**🟡 partial** is the most important status in this document. It means the model, the parser, or
the UI for a feature exists — and the last mile that makes it reach the user does not. These
are cheap to finish and they are already confusing users, so they outrank most new work.

**Scope of this document.** Product features only. Internal quality work lives in
[`REFACTORING_BACKLOG.md`](./REFACTORING_BACKLOG.md); the three current defects live in
[ADR-004](./ADR_EDITOR_SYNC_AND_PRESENTATION_HUD.md) and its
[improvement plan](./IMPROVEMENT_PLAN_EDITOR_SYNC_AND_HUD.md).

---

## Identifier namespace

Identifier prefixes already in use across the project, so new ones do not collide:

| Prefix | Owner | Meaning |
| :--- | :--- | :--- |
| `SEC` `OVF` `MMD` `COR` `EXP` `PRF` `DED` `R` | `QUALITY_BASELINE.md` | Invariants |
| `EDT` `HUD` | ADR-004 | Invariants |
| `F-nn` | `REFACTORING_BACKLOG.md` | Refactoring items |
| `FR-*` | `FUNCTIONAL_SPECIFICATION.md` | Shipped functional requirements |
| **`AUT` `DEL` `DIA` `THM` `MED` `OUT` `AUD` `PRJ` `PLT`** | **this document** | **Feature candidates** |

Note `EXP` is taken by export *invariants*, which is why export *features* here use `OUT`.

---

## The pattern worth remembering: features already paid for

**Resolved 2026-08-06 — kept because the *pattern* keeps recurring, and the next survey should
look for it first.**

Surveying the codebase for this roadmap turned up work that was complete everywhere except
where the user meets it: a correct model with no last mile. Three instances, all found by
reading the code rather than the README, and all small once found.

| Was | Symptom | Closed by |
| :--- | :--- | :--- |
| **DEL-01** transitions | `SlideTransition` had four values, the parser accepted `transition: zoom`, `DeckProject` persisted it and the TopBar offered a picker — and `FullscreenDeck` hardcoded `fadeIn() togetherWith fadeOut()`. Picking "Zoom Scale" gave you a fade. | `TransitionResolver` + an exhaustive `transitionSpecFor` |
| **AUT-01** shortcuts | The README documented `Ctrl+E`, `T`, `Home`/`End`, `Backspace`. None was bound — there was no `Key.E`, `Key.T`, `Key.MoveHome`, `Key.MoveEnd` or `Key.Backspace` anywhere in `src/desktopMain`. | All five bound, now guarded by `DocumentedShortcutsTest` |
| **AUT-03** find reveal | `findNext()` advanced an index; nothing scrolled to the match. The buttons looked dead. **Closed 2026-08-06**, once `AUT-05` laid the caret foundation. | `EditorSession`'s reveal token, guarded by `EditorWorkspaceRenderingTest` — which asserts the *rendered pane moved*, not that a token changed |

### It reopened once, and took three other features with it

Converting both key handlers to the `AppCommands` registry enumerated the commands by *reading*
the old `when` blocks. An audit of this document against the source found that it dropped:

| Lost | Feature it broke |
| :--- | :--- |
| `T`, `Home`, `End`, `Backspace` | **AUT-01** — reopened |
| `H` | **DEL-02** — the HUD could not be toggled |
| digits + `Enter` | **DEL-08** — `SlideNumberEntry` still *rendered its overlay*, and nothing could populate it |
| `Ctrl+Z`, `Ctrl+Shift+Z`, `Ctrl+Y` in the studio window | **AUT-04** — structural undo/redo unreachable by keyboard |

Four ✅ rows in this document described features the user could no longer reach. The registry's
own test stayed green throughout, because it asserts the table is *self-consistent* — no
duplicate chords, no shadowing — which remained true the entire time.

**A ✅ that nothing can reach is worse than a 📋**, because it stops anyone looking.

`DocumentedShortcutsTest` now checks the registry against the **README table** and against the
shipped-feature list, rather than against itself, and was verified to fail on the broken code
before being kept. Digit entry is handled *ahead of* the registry in `FullscreenDeck`, because
typing `27` is a mode rather than a chord and the registry structurally cannot express it.

### How this class of defect survives

Each one had a **passing test suite around it.** `EditorFindAndReplaceTest` asserts
`currentMatchIndex` advances — which it does — and will keep passing however broken the visible
behaviour is. The transition picker had a model, a parser, persistence and a UI, and no test
anywhere asked whether the animation changed.

This is the same failure shape `QUALITY_BASELINE` already records for **OVF-1**, the regression
that blanked every slide while the suite stayed green. The countermeasure is written into the
guards added since: assert the *user-visible* outcome, never the intermediate variable.

### Where to look next

Two candidates were noticed while working and are not yet investigated:

- **`SlideElement.Poll` and `Table` in the PNG export.** `DeckExporter` draws Mermaid diagrams
  as a `g.drawString("MERMAID DIAGRAM: [...]")` placeholder rather than a diagram. EXP-3 claims
  every slide element reaches the image export; that claim deserves re-testing now that
  `ElementImageRenderer` exists and could render them properly.
- **`Slide.customBackground`** is parsed and stored. Whether it reaches any renderer is
  unverified — see `THM-07`.

---

## AUT — Authoring & editor

The editor is the least-developed surface in the application relative to how much time a user
spends in it. It is a single `TextField` with a syntax-highlighting `VisualTransformation`.

| ID | Feature | Status | Size | Depends | Notes |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **AUT-01** | Bind the four documented-but-absent shortcuts | ✅ | S | — | `Ctrl+E`, `Home`, `End`, `Backspace`, `T`. `T` is bound in the presentation window only — a bare letter in the studio window would race the markdown editor for the keystroke. |
| **AUT-02** | Editor ⇄ slide synchronisation | ✅ | M | AUT-05 | Both directions. Selecting a slide reveals its source (`SlideSourceLocator`, EDT-3); moving the caret selects the slide, behind a follow-caret toggle that defaults on (EDT-2). ADR-004 Phases 4–5. |
| **AUT-03** | Find reveal — scroll the match into view | ✅ | S | AUT-05 | EDT-4. Also: focus returns to the query field after ▲/▼, the bar states its scope, and the first match is taken from the caret rather than from 0. ADR-004 Phase 3. |
| **AUT-04** | **Undo/redo for structural edits** | ✅ | M | — | `DeckHistory` + snapshot/restore in `PresentationState`. Covers delete, move, duplicate, insert in both single-file and project mode; `Ctrl+Z` / `Ctrl+Shift+Z` / `Ctrl+Y` plus TopBar buttons. Does **not** cover free-text editing (the `TextField` has its own undo). |
| **AUT-05** | Caret & selection model | ✅ | M | — | `EditorSession` — selection stored, text derived (EDT-1), clamped on change (EDT-5). The pane took ADR-004's **Option B** (`BasicTextField` + explicit `ScrollState` + `onTextLayout`) rather than Option A, so the reveal works while the *find bar* holds focus; this also unblocks AUT-06's gutter, which Option A would not have. |
| **AUT-06** | Line-number gutter with slide-boundary markers | 📋 | M | AUT-05 | Makes deck structure legible in a long single-file deck. |
| **AUT-07** | Outline panel — navigate by heading | 📋 | M | AUT-05 | Complements the filmstrip for text-heavy decks. |
| **AUT-08** | Directive autocomplete | 📋 | M | AUT-05 | The directive language (`<!-- note: -->`, `> note:`, `<!-- poll: -->`, `layout:`, `transition:`, mermaid fences) is rich and entirely undiscoverable while typing. |
| **AUT-09** | Inline diagnostics | 📋 | L | AUT-08 | Unparseable mermaid, unresolvable image paths and malformed polls currently surface only as rendered output. COR-10 already produces a *reason* for image failures — surface it at the source line. |
| **AUT-10** | Drag-and-drop slide reordering | 📋 | M | — | Filmstrip today offers ◀ ▶ buttons only; reordering slide 40 to position 2 is 38 clicks. |
| **AUT-11** | Formatting shortcuts (`Ctrl+B`, `Ctrl+I`, `Ctrl+K` link) | 📋 | S | AUT-05 | Needs selection, hence the dependency. |
| **AUT-12** | Paste image from clipboard | 📋 | M | — | Write into the deck folder, insert `![](name.png)`. Removes the most tedious step in authoring a visual deck. |
| **AUT-13** | Editor-only / preview-only / split layout modes | 📋 | S | — | The 1 : 1.3 split is fixed today. |
| **AUT-14** | Draggable splitter between editor and preview | 📋 | S | — | |
| **AUT-15** | Slide-source folding | 🕓 | L | AUT-06 | |
| **AUT-16** | Multi-caret / column selection | 🕓 | L | AUT-05 | Editor-nerd feature; low value for deck authoring. |

---

## DEL — Delivery & presentation

| ID | Feature | Status | Size | Depends | Notes |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **DEL-01** | Honour the transition model | ✅ | S | — | `TransitionResolver` + exhaustive `transitionSpecFor`. All four values now render. |
| **DEL-02** | HUD show/hide | ✅ | S | — | `HudVisibility` (AUTO/PINNED/HIDDEN), `H` binding, idle fade, HUD button, recovery hint. Persisted across launches (DED-2). |
| **DEL-03** | **Incremental reveals (bullet builds)** | 📋 | L | — | The most-requested feature of any presentation tool that lacks it. Code blocks already support `highlightedLines` step highlighting (FR-DIAG / §3.7), so the *concept* exists for code but not for bullets. Needs a directive, a per-slide step counter, and step-aware navigation in both windows and the companion. |
| **DEL-04** | Rehearsal mode with per-slide timing | 📋 | M | — | The pacing engine (FR-PRES-06) computes whether you are ahead or behind against a target. It cannot tell you *which slides* cost you the time. Record per-slide dwell, show it afterwards. |
| **DEL-05** | Auto-advance / kiosk loop | 📋 | S | — | Booths, lobby screens, unattended demos. |
| **DEL-06** | "Starting soon" / countdown holding screen | 📋 | S | — | |
| **DEL-07** | Presentation zoom / spotlight a slide region | 📋 | M | — | Pairs with the existing laser pointer for dense diagrams and code. |
| **DEL-08** | Jump-to-slide by number during presentation | ✅ | S | — | `SlideNumberEntry` + on-screen feedback. Type `27` `Enter`; `Esc` cancels. |
| **DEL-09** | Freeze / hold current slide while navigating ahead | 🕓 | M | — | Presenter reads ahead; audience display stays put. |
| **DEL-10** | Per-slide transition override honoured | ✅ | S | DEL-01 | `transition:` on a slide beats the deck default. |

---

## DIA — Diagrams

The known-limitations table in `QUALITY_BASELINE.md` is the honest source for this section.

| ID | Feature | Status | Size | Depends | Notes |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **DIA-01** | State diagrams | 📋 | L | — | FR-DIAG-05 lists these as explicitly out of scope today; they fall back to showing source. |
| **DIA-02** | Class diagrams | 📋 | L | — | As above. Most valuable of the four for this tool's audience. |
| **DIA-03** | ER diagrams | 🕓 | L | — | As above. |
| **DIA-04** | Gantt charts | 🕓 | L | — | As above. Weakest fit — a Gantt on a slide is usually a picture. |
| **DIA-05** | Nested subgraphs | 📋 | M | — | Currently flattened; a node joins the innermost group that declares it. MMD-10 reserves cross-axis bands per group, which is the mechanism nesting would extend. |
| **DIA-06** | Diagram footer reports the parsed type | ✅ | S | — | `SlideFooterLabel`, with the Mermaid parser injected so `core/` keeps no UI dependency. |
| **DIA-07** | Honour `classDef` / `style` / `linkStyle` | 📋 | M | — | FR-DIAG-08 parses and deliberately discards these. Colour-coded flowcharts are a common authoring need. |
| **DIA-08** | Layout direction beyond LR/TD | 📋 | S | — | `RL` and `BT` are not recognised. |
| **DIA-09** | Manual layout hints (rank/position pinning) | 🕓 | L | — | Escape hatch when the auto-layout is wrong. Defer until DIA-01/02 show whether it is needed. |

---

## THM — Theming & design

A design proposal already exists:
[`superpowers/specs/2026-08-04-beamer-like-themes-design.md`](./superpowers/specs/2026-08-04-beamer-like-themes-design.md).

| ID | Feature | Status | Size | Depends | Notes |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **THM-01** | Font themes | 📋 | M | — | Every layout hardcodes `FontFamily.SansSerif`. A serif deck is impossible today. Smallest useful slice of the Beamer proposal. |
| **THM-02** | Outer themes — headline/footline/sidebar chrome | 📋 | L | THM-01 | The `SlideSurface` footer is baked in; there is no headline and no frame-title band. |
| **THM-03** | Inner themes — block frames, bullet glyphs, title page | 📋 | L | THM-01 | |
| **THM-04** | Named presets composing colour + outer + inner + font | 📋 | M | THM-02, THM-03 | The "Warsaw / Madrid" idea that motivated the spec. |
| **THM-05** | Custom theme editor in-app | 📋 | L | — | The Adaptive Contrast Enforcer and `ThemePaletteValidator` already exist to keep a hand-rolled palette WCAG-compliant, so the safety net is built. |
| **THM-06** | Theme from a file in the deck project | 📋 | M | THM-05 | Version-controlled brand themes beside the markdown. |
| **THM-07** | Per-slide background images | 📋 | M | MED-02 | `customBackground` is parsed already — check whether it reaches the renderer before sizing this. |
| **THM-08** | Deck logo / brand mark placement | 📋 | S | THM-02 | Recurring corporate requirement. |

---

## MED — Media

| ID | Feature | Status | Size | Depends | Notes |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **MED-01** | **Video** | 📋 | XL | — | A stated known limitation. Genuinely expensive: Compose Desktop has no built-in video surface, so this means a native player integration or an embedded runtime — and it would breach the "runtime dependencies limited to Compose, coroutines and the markdown parser" NFR. Needs its own ADR before any work. |
| **MED-02** | SVG images | 📋 | M | — | Raster only today. Diagrams and logos exported from design tools are usually SVG. |
| **MED-03** | Animated GIF | 🕓 | M | — | |
| **MED-04** | Image sizing / positioning directives | 📋 | S | — | `![alt](src)` has no width, alignment or crop control. |
| **MED-05** | Audio narration per slide | 🕓 | L | MED-01 | Only meaningful alongside DEL-05 for unattended playback. |
| **MED-06** | Asset panel — see and manage deck images | 📋 | M | PRJ-03 | |

---

## OUT — Export & interoperability

| ID | Feature | Status | Size | Depends | Notes |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **OUT-01** | **Offline self-contained HTML export** | ✅ | M | — | Maths and diagrams are rendered by the app at export time via `ImageComposeScene` and embedded as `data:` URIs; both CDN dependencies removed. Chose rendering over vendoring ~3.5 MB of KaTeX + Mermaid — the app is already the renderer, and vendoring would have shipped a second implementation of what it can already draw. Source is kept as `alt` text and a `<details>` fallback. |
| **OUT-02** | PPTX export | 🕓 | XL | — | Frequently asked for, rarely satisfying: the fidelity gap between a Compose-rendered slide and an OOXML shape tree is large. An image-per-slide PPTX is `M` and honest; a native-shape PPTX is `XL` and will disappoint. |
| **OUT-03** | Import from Marp / reveal.js | 📋 | M | — | Both are markdown-based; the adapter is a directive translation. Cheapest credible migration path onto Skaldoria. |
| **OUT-04** | Import from PPTX | ❌ | — | — | See [Rejected](#rejected-with-reasons). |
| **OUT-05** | Speaker-notes-only export | 📋 | S | — | Handout for the presenter or for a translator. |
| **OUT-06** | Export the deck as a static site | 📋 | M | OUT-01 | Once the HTML is self-contained this is mostly packaging. |
| **OUT-07** | Post-talk report — questions, polls, parking lot, timings | 📋 | M | DEL-04 | The data is already collected and thrown away at exit. Genuinely differentiating. |

---

## AUD — Audience & companion

| ID | Feature | Status | Size | Depends | Notes |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **AUD-01** | Push updates (WebSocket/SSE) instead of polling | 📋 | L | — | ADR-001 names this as *the* condition under which the hand-rolled-server decision should be revisited, and `KTOR_MIGRATION_TRADEOFFS.md` is the pre-written analysis. Do not start this without re-reading both. |
| **AUD-02** | Open-text responses / word cloud | 📋 | M | — | Polls are fixed-option today. SEC-1 and SEC-5 already establish the input-handling pattern. |
| **AUD-03** | Quiz mode with correct answers and scoring | 📋 | M | AUD-02 | Training and workshop use. |
| **AUD-04** | Audience reactions (emoji stream) | 📋 | S | — | Cheap engagement signal; reuses the poll transport. |
| **AUD-05** | Question upvote sorting and moderation queue | 📋 | S | — | `AudienceQuestion` already carries `upvotes` and `isAnswered`. Check what the presenter UI does with them before sizing. |
| **AUD-06** | Attendance count / session analytics | 🕓 | M | — | Privacy implications; needs a decision on what is retained. |
| **AUD-07** | Companion UI localisation | 🕓 | M | PLT-03 | |
| **AUD-08** | **Verify BLE presenter clickers work** | ✅ | S | — | Confirmed: forward/back already worked — a clicker is an HID keyboard and `PageUp`/`PageDown` were bound. The survey found **one** gap: the blank-screen button sends `.` / `,` (the PowerPoint convention the hardware targets), not `B` / `W`, so it did nothing. Both added. `PresenterClickerTest` (CLK-1) asserts the virtual key codes; a physical clicker is still a hardware claim and stays on the manual script. ADR-005 Phase 0. |
| **AUD-09** | **Link ranking for non-LAN interfaces** | ✅ | M | — | `LinkRanking` + `LinkKind`, extracted pure so it is testable. LNK-A and LNK-B both fixed: a hotspot/tether/PAN link now outranks an unreachable routed adapter, and Bluetooth PAN is classified as a transport rather than denylisted. |
| **AUD-10** | **SoftAP pairing guidance** | 📋 | M | AUD-09 | Detect the isolated-network case, explain the remedy per platform, never show a QR that cannot work. The only path that serves the *audience* portal without venue infrastructure. ADR-005 Phase 2. |
| **AUD-11** | Wi-Fi credential QR (`WIFI:T:WPA;…`) | 📋 | S | AUD-10 | Phone joins the hotspot by camera, then chains to the portal QR. `QrCodeGenerator.encode` already takes arbitrary text — no generator change. ADR-005 Phase 3. |
| **AUD-12** | Automated hotspot creation | 🕓 | L | AUD-10 | Per-platform native integration; the only step that would compromise ADR-001's portability argument. Explicitly last and optional. ADR-005 Phase 4. |
| **AUD-13** | Bluetooth transport (speak BT from Java) | ❌ | — | — | See [Rejected](#rejected-with-reasons). |

---

## PRJ — Deck & project management

| ID | Feature | Status | Size | Depends | Notes |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **PRJ-01** | Move a slide between files with >1 slide per file | 📋 | M | — | Known limitation: currently a no-op rather than a partial write, which is the safe failure but still a failure. COR-2/COR-3 govern the fix. |
| **PRJ-02** | Deck templates / new-deck scaffolding | 📋 | S | — | `.skills/markdown-presentation/examples/` already holds three well-built decks that no in-app flow surfaces. |
| **PRJ-03** | Reusable slide library across decks | 📋 | L | PRJ-02 | Boilerplate slides — title, agenda, disclaimer, thank-you. |
| **PRJ-04** | Search across all decks | 🕓 | M | AUT-03 | |
| **PRJ-05** | Recent decks on the welcome screen | 📋 | S | — | |
| **PRJ-06** | Deck-level metadata (author, event, date) surfaced in chrome | 📋 | S | THM-02 | |
| **PRJ-07** | Git integration — diff a deck visually | 🕓 | XL | — | Compelling but enormous. The format is already git-friendly, which delivers most of the value for free. |

---

## PLT — Platform & quality

| ID | Feature | Status | Size | Depends | Notes |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **PLT-01** | **CI pipeline** | 🟡 | S | — | `.github/workflows/ci.yml` — compile + test on Linux/Windows/macOS (Xvfb for the headless render guards), with test reports and rendered PNGs uploaded. **The zero-warning job is present but currently vacuous:** this toolchain emits no Kotlin warnings at all — verified by planting an unused local *and* an unused private function and seeing zero `w:` lines, with and without `allWarningsAsErrors`. The gate cannot fire until that is understood. Never executed on a runner. |
| **PLT-02** | Verify macOS and Linux packaging | 📋 | M | PLT-01 | `.dmg`, `.pkg`, `.deb`, `.rpm` targets are declared and unproven. |
| **PLT-03** | UI localisation | 🕓 | L | — | Every string is inline today. |
| **PLT-04** | Screen-reader semantics and keyboard-only operation | 📋 | M | — | The project holds itself to WCAG 2.1 AA for *contrast* and has real machinery for it. Contrast is one clause of one guideline; navigability is untested. |
| **PLT-05** | Crash reporting / diagnostics bundle | 📋 | M | — | Related: `F-03` in the refactoring backlog notes `remoteServerError` is an overloaded error channel. |
| **PLT-06** | Auto-update | 🕓 | L | PLT-01, PLT-02 | |
| **PLT-07** | Command palette coverage for every action | 📋 | S | — | The palette exists; whether it reaches all commands should be audited as new features land. |

---

## Horizons

An opinionated ordering. The principle: **finish what is already built before building more**,
then remove the sharpest edges, then extend.

> **Revised 2026-08-06.** The original "Now" tier has shipped. What follows is the re-planned
> ordering; see [Shipped so far](#shipped-so-far) for what moved.

### Shipped so far

Every 🟡 "already paid for" item is closed, plus the two highest-risk gaps, plus the whole
editor track.

`DEL-01`/`DEL-10` transitions · `AUT-01` the five missing shortcuts · `DEL-02` HUD visibility ·
`DEL-08` jump to slide · `DIA-06` diagram footer · `AUT-04` undo/redo · `AUD-09` link ranking ·
`OUT-01` offline HTML export · `PLT-01` CI *(written, never executed)* ·
**`AUT-05` caret foundation · `AUT-03` find reveal · `AUT-02` editor ⇄ slide sync ·
`AUD-08` presenter clickers**

Suite: **235 → 575 tests**, zero compiler warnings throughout.

> **The editor track's lesson, for the next survey.** ADR-004 chose Option A (the Material
> `TextField`'s built-in cursor-following scroll) and named Option B as the escalation. Option A
> would have shipped a reveal that fires while the *find bar* holds focus — an unfocused field
> is not specified to scroll — and the find buttons would have gone on looking dead, under a
> new mechanism, with a green suite. The pattern this document keeps rediscovering held again:
> **the guard has to render the pane, not read the token.**
> `EditorWorkspaceRenderingTest` is the one that would have caught it.

### Now — proving the build works off this machine

The editor track is done. What is left in this tier is entirely about trusting the build.

| | Item | Note |
| :--- | :--- | :--- |
| 1 | **`PLT-01` run CI** | Written but never executed. Push and watch it go green before trusting it — an unrun workflow is a guess. Needs a push, so it is the user's call, not a code change. |
| 2 | `PLT-02` verify macOS/Linux packaging | Four installer formats are declared and unproven; CI makes this checkable. Needs runners this machine does not have. |
| 3 | `AUT-06` line-number gutter | Newly cheap: `AUT-05` landed with an explicit `ScrollState` and `onTextLayout`, which is exactly the foundation a gutter needs. Option A would not have provided it. |

### Next — remove the sharpest edges

`AUT-10` drag-and-drop reordering · `THM-01` font themes · `AUT-12` clipboard image paste ·
`AUD-10` SoftAP pairing guidance · `MED-02` SVG images · `DEL-04` rehearsal timings ·
`MED-04` image sizing directives · `AUT-11` formatting shortcuts *(now unblocked — `AUT-05`
shipped the selection it needed)*

### Later — extend the product

`DEL-03` incremental reveals · `DIA-01`/`DIA-02` state and class diagrams · `THM-02`–`THM-04`
Beamer-style structural themes · `AUD-02`/`AUD-03` open text and quizzes · `OUT-07` post-talk
report · `AUT-08`/`AUT-09` autocomplete and diagnostics · `DIA-05` nested subgraphs ·
`DIA-07` Mermaid style directives

### Watchlist — revisit when a trigger fires

| Item | Trigger |
| :--- | :--- |
| `AUD-01` push transport | Polling becomes a measured bottleneck. Re-read ADR-001 and the Ktor analysis first. |
| `MED-01` video | Enough demand to justify breaching the dependency-surface NFR. Needs its own ADR. |
| `OUT-02` PPTX | Someone is willing to accept image-per-slide fidelity. |
| `PRJ-07` visual git diff | Never, unless the project acquires a second maintainer. |

---

## Rejected, with reasons

| ID | Feature | Why not |
| :--- | :--- | :--- |
| **AUD-13** | Bluetooth as an application transport | The JVM has no Bluetooth API, so this means three per-platform native integrations — the largest portability regression available to this project, against an ADR-001 decision resting on `java.base`-only portability across six installer formats. Browsers cannot speak RFCOMM at all and Web Bluetooth is Chrome-only and absent from iOS, so the audience portal would require a native mobile app. Topology caps around seven devices against a 200+ NFR. **Bluetooth PAN is not rejected** — it carries IP, the OS owns the radio, and it works with no server change: see `AUD-09` and [ADR-005](./ADR_COMPANION_LINK_ESTABLISHMENT.md). |
| — | Native companion mobile app | Would unlock BLE and destroy the feature's premise: the audience portal works *because* it is a URL — no install, no store, no trust decision, no release train. |
| — | Internet relay / cloud rendezvous for the companion | Inverts the product — a server we operate, an account model, a privacy surface, and a hard internet dependency — and still fails the venue-with-no-network case it is meant to fix. |
| **OUT-04** | Import from PPTX | PPTX has no clean mapping onto a markdown source of truth. Any importer produces either lossy markdown that disappoints, or a pile of images that defeats the point of the tool. `OUT-03` (Marp/reveal.js) gets the migration benefit at a fraction of the cost, because those formats are already markdown. |
| — | Cloud sync / hosted decks | Contradicts the product: local-first, markdown-on-disk, git-friendly, zero-account. Users who want sync already have git. |
| — | Real-time collaborative editing | Would require a server, identity, and CRDT conflict resolution — inverting every architectural decision this project has made. Git branches and `PRJ-07` cover the realistic need. |
| — | Plugin / extension system | Premature. There is no evidence of demand, and a plugin API freezes internal structure that `REFACTORING_BACKLOG.md` argues should still change. Revisit if third parties ever ask. |
| — | AI slide generation | Deliberately out of scope. The input format is markdown; any tool the user prefers can write markdown, and the deck stays theirs. Building it in would add a network dependency, an API key, and a support burden for something already solved outside the app. |

---

## Related documents

| Document | Covers |
| :--- | :--- |
| [`QUALITY_BASELINE.md`](./QUALITY_BASELINE.md) | Invariants that constrain every feature here. Read the known-limitations table before sizing anything. |
| [`FUNCTIONAL_SPECIFICATION.md`](../FUNCTIONAL_SPECIFICATION.md) | What already ships, as `FR-*` requirements. |
| [`ADR-004`](./ADR_EDITOR_SYNC_AND_PRESENTATION_HUD.md) + [plan](./IMPROVEMENT_PLAN_EDITOR_SYNC_AND_HUD.md) | `AUT-02`, `AUT-03`, `AUT-05`, `DEL-02`. |
| [`ADR-003`](./ADR_GOD_OBJECT_DECOMPOSITION.md) + [`REFACTORING_BACKLOG.md`](./REFACTORING_BACKLOG.md) | Internal quality — the other axis. Several features here get cheaper after it. |
| [`ADR-001`](./ADR_COMPANION_SERVER_ARCHITECTURE.md) + [Ktor analysis](./KTOR_MIGRATION_TRADEOFFS.md) | Mandatory reading before `AUD-01`. |
| [`ADR-005`](./ADR_COMPANION_LINK_ESTABLISHMENT.md) | `AUD-08`–`AUD-13`. Companion connectivity when no shared LAN exists. |
| [Beamer themes spec](./superpowers/specs/2026-08-04-beamer-like-themes-design.md) | `THM-01`–`THM-04`. |

---

## Maintenance

- **Never renumber.** A dropped feature keeps its ID and gains a ❌ with a reason.
- **Promote 🟡 aggressively.** A partial feature is worse than an absent one: it is documented,
  discoverable, and broken.
- **Re-survey after each release.** This document was built by reading the code, not the
  README — which is how `DEL-01` and `AUT-01` were found. Repeat that method.
