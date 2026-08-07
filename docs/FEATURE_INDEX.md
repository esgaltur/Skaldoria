# Feature Index — Long-Term Product Roadmap

**Version:** 1.3.0 · **Drafted:** 2026-08-06 · **Revised:** 2026-08-07 · **Status:** Living document

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
| **AUT-03** find reveal | `findNext()` advanced an index; nothing scrolled to the match. The buttons looked dead. **Closed 2026-08-06** — and reopened the same day without anyone noticing. See the correction directly below. | `EditorSession`'s reveal token; the guard that was supposed to prove it is the subject of the correction |

### The AUT-03 row above was wrong for a day, and the guard is why

**2026-08-07.** The row claimed the fix was proved by a guard that "asserts the *rendered pane
moved*, not that a token changed". It rendered, and it moved, and **the feature was still
broken**: in the running application, pressing next match scrolled the source pane by
**zero pixels** for its entire shipped life. The counter advanced, the filmstrip jumped to the
right slide, the preview followed — every signal except the pane the user is reading.

The cause was that revealing a match re-lays out the text (the active match restyles), which
changed a `LaunchedEffect` key, which cancelled the scroll animation the reveal had just
started; the "already handled" flag had been set before the suspend, so the retry refused.
Recorded as **EDT-6**.

**Why the render guard could not see it.** `EditorWorkspaceRenderingTest` mutates the state and
*then* renders into a fresh scene. The reveal therefore always arrives **before the first
composition**, with nothing yet measured and no previous reveal handled — so there is no
re-layout to interrupt it. That configuration is unreachable in the UI, where `Ctrl+F` opens the
bar into a composition that is already running and already measured.

**The lesson this document already carried was not enough.** "Assert the user-visible outcome,
not the intermediate variable" was satisfied — the guard asserted pixels. The missing half is
*the state the user is actually in*: *rendering a fresh scene is not driving a live one.*
`FindRevealScrollTest` keeps one scene open and steps it, and reported `0.0%` movement across
three consecutive presses before the fix.

**A second defect was hiding behind the first.** Even once scrolled, the match had no caret:
`MarkdownSourceField` had no `FocusRequester` at all, and an unfocused Compose text field draws
neither cursor nor selection, so only the focus-independent highlight overlay rendered. Closing
the bar now returns focus with the caret on the match (**EDT-7**). That half is guarded at state
level only — an `ImageComposeScene` has no platform text-input session, so `requestFocus()`
returns cleanly while the field still reports itself unfocused, which no pixel assertion can
work around. Logged in `RENDERING_STATUS.md` under what the harness cannot see.

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
| **AUT-03** | Find reveal — scroll the match into view | ✅ | S | AUT-05 | EDT-4/**EDT-6**/**EDT-7**. Shipped 2026-08-06 and **was broken from the day it shipped until 2026-08-07** — see the correction below. Also: focus returns to the query field after ▲/▼ so Enter keeps cycling, the bar states its scope, the first match is taken from the caret rather than from 0, and closing the bar hands the caret back to the editor on the match (EDT-7). ADR-004 Phase 3. |
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
| **AUT-17** | GFM tables without outer pipes | ✅ | S | — | **Shipped 2026-08-07.** `TableRules` is now the one authority — the parser, the delimiter filter in `flushTable` and the editor's highlighter had three different opinions. `TableRule` recognises a header by the delimiter row that follows it, via a one-line lookahead (`SectionContext.nextLine`). The editor styles the delimiter row but not the header/body of a pipe-less table: it has no lookahead and adding one touches the per-keystroke path, so the asymmetry is pinned in `LineRuleAgreementTest` rather than left to be rediscovered. Original note: | `a \| b` over `---\|---` is ordinary GFM and **neither the parser nor the highlighter handles it** — `TableRule` matches only the separator row, via `contains("-\|-")`, so header and body stay prose and no table is assembled. The two agree, which is why no divergence test caught it; it is a missing feature, not a defect. From `MARKDOWN_UNIFICATION_PLAN.md`. |
| **AUT-18** | Deck-wide find & replace in project mode | 📋 | M | AUT-03 | Find searches `currentEditorText`, so in per-slide project mode it covers **one file**. The bar says so, which is honest but not a substitute. ADR-004 Phase 6, deferred there and still not a commitment. |
| **AUT-19** | Find results list — every match, with its slide | 📋 | M | AUT-03 | Stepping ▲/▼ answers "where is the next one"; it never answers "how many, and where". Cheap now that `findMatches` and `SlideSourceLocator` both exist, and it makes a large deck searchable rather than merely steppable. |
| **AUT-20** | Repeat the last search without the bar (`F3` / `Ctrl+G`) | ✅ | S | AUT-03 | **Shipped 2026-08-07.** `Shift+F3` / `Ctrl+Shift+G` step backwards. A no-op before a first search rather than opening an empty bar. In the README table and guarded by `DocumentedShortcutsTest`. Original note: | Newly sensible: EDT-7 hands the caret back on close, so "close the bar, keep searching" is now a coherent flow instead of a dead end. |

---

## DEL — Delivery & presentation

| ID | Feature | Status | Size | Depends | Notes |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **DEL-01** | Honour the transition model | ✅ | S | — | `TransitionResolver` + exhaustive `transitionSpecFor`. All four values now render. |
| **DEL-02** | HUD show/hide | ✅ | S | — | `HudVisibility` (AUTO/PINNED/HIDDEN), `H` binding, idle fade, HUD button, recovery hint. Persisted across launches (DED-2). |
| **DEL-03** | **Incremental reveals (bullet builds)** | 📋 | L | — | The most-requested feature of any presentation tool that lacks it. Code blocks already support `highlightedLines` step highlighting (FR-DIAG / §3.7), so the *concept* exists for code but not for bullets. Needs a directive, a per-slide step counter, and step-aware navigation in both windows and the companion. |
| **DEL-04** | Rehearsal mode with per-slide timing | 📋 | M | DEL-11 | The pacing engine computes whether you are ahead or behind against a target. It cannot tell you *which slides* cost you the time. Record per-slide dwell, show it afterwards. **Now has somewhere to put the answer:** DEL-11 gave slides a declared budget, so rehearsal can write measured timings back into the deck as `<!-- pace: … -->` — the Parking Lot already proves that write-back path. That turns this from a report into a feedback loop. |
| **DEL-11** | **Per-slide time budgets** | ✅ | M | — | **Shipped 2026-08-07.** `<!-- pace: 90s -->` (also `time:`, `budget:`). Drift is measured against the sum of declared budgets; undeclared slides split the remainder, so a deck declaring nothing behaves *exactly* as before — asserted, not assumed. Over-commitment is reported in the presenter console rather than silently rescaled, because `pace: 90s` has to mean ninety seconds. **Fixed a correctness flaw in the headline feature:** dividing the target by the slide count allotted a title card and a code walkthrough the same time, so the gauge read *behind* within the first minute of almost every real talk. |
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
| **DIA-01** | State diagrams | ✅ | L | — | **Shipped 2026-08-07.** Own model + parser, then projected onto the flowchart pipeline by `DiagramAdapters`, so it inherits MMD-1 layout, MMD-10 framing, DIA-07 styling and DIA-08 direction rather than getting a fourth layout engine. Verified by rendering. **Approximated:** members render as label lines, not ruled compartments, and relationship kinds are edge labels, not distinct arrowheads — structure and reading are right, UML/Chen iconography is not drawn yet. `[*]` is a boundary, not a state; composite `state X { … }` becomes an MMD-10 frame. |
| **DIA-02** | Class diagrams | ✅ | L | — | **Shipped 2026-08-07.** Own model + parser, then projected onto the flowchart pipeline by `DiagramAdapters`, so it inherits MMD-1 layout, MMD-10 framing, DIA-07 styling and DIA-08 direction rather than getting a fourth layout engine. Verified by rendering. **Approximated:** members render as label lines, not ruled compartments, and relationship kinds are edge labels, not distinct arrowheads — structure and reading are right, UML/Chen iconography is not drawn yet. Connector precedence is load-bearing: `--` is a prefix of `-->` and matching in the wrong order changes the *meaning*, not the validity. |
| **DIA-03** | ER diagrams | ✅ | L | — | **Shipped 2026-08-07.** Own model + parser, then projected onto the flowchart pipeline by `DiagramAdapters`, so it inherits MMD-1 layout, MMD-10 framing, DIA-07 styling and DIA-08 direction rather than getting a fourth layout engine. Verified by rendering. **Approximated:** members render as label lines, not ruled compartments, and relationship kinds are edge labels, not distinct arrowheads — structure and reading are right, UML/Chen iconography is not drawn yet. `\|\|--o{` is four facts in six characters; the glyph pairs are mirrored, so cardinality resolves by side. |
| **DIA-04** | Gantt charts | ✅ | L | — | **Shipped 2026-08-07.** The one that is *not* a graph, so it has its own timeline view. `GanttSchedule` resolves dates purely, so the arithmetic is unit-tested rather than eyeballed, and `after <id>` chains resolve in declaration order. **ISO `YYYY-MM-DD` only** — any other `dateFormat` returns null and the source is shown, because inferring that `01/02/2026` is February the first is wrong half the world over. |
| **DIA-05** | Nested subgraphs | 📋 | M | — | Currently flattened; a node joins the innermost group that declares it. MMD-10 reserves cross-axis bands per group, which is the mechanism nesting would extend. |
| **DIA-06** | Diagram footer reports the parsed type | ✅ | S | — | `SlideFooterLabel`, with the Mermaid parser injected so `core/` keeps no UI dependency. |
| **DIA-07** | Honour `classDef` / `style` / `linkStyle` | ✅ | M | — | **Shipped 2026-08-07.** `DiagramStyling` carries them; `NodeCard` and the edge painter apply them. Resolution is two-phase because Mermaid imposes no ordering — `class a big` may precede the `classDef big …` it names, and `linkStyle default` needs an edge count that does not exist until parsing ends. Verified by rendering: the colours reach pixels, and the render caught what the 16 unit tests could not — a node's id sublabel used `theme.textMuted`, invisible on a bright declared fill, now derived against the fill itself. |
| **DIA-08** | Layout direction beyond LR/TD | ✅ | S | — | **Was already implemented and mismarked.** `FlowDirection` parses all four, `ParsedDiagram` carries it, and `FlowchartScene.arrange` mirrors the laid-out scene for `RL`/`BT` — but **nothing asserted any of it** and this row said 📋, so the shipped state was: built, unguarded, reported absent. `FlowDirectionLayoutTest` now pins the axis, the reversal and the untouched cross axis (2026-08-07). |
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
| **THM-05** | **Theme-aware mouse pointer** | ✅ | S | — | **Shipped 2026-08-07.** The OS draws the pointer in the user's desktop colour, so a dark arrow disappears on a dark deck and a light one on a light deck — and the app had no say in it. Same arrow shape, fill and outline derived from the theme background through `ColorScience`, so every current and future palette is covered without a hand-set token. **The outline is the load-bearing part:** the pointer sits on whatever pixel is under it — a screenshot, a code block — not on the theme background, which is why a single themed colour is not enough. Applied in the presentation window; the laser's blank cursor still wins while the laser is on. |
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
| **AUD-08** | **Verify BLE presenter clickers work** | ✅ | S | — | "Likely works today with zero code" was **half right**, and the wrong half was the expensive one. Forward/back did work in the deck window. Two gaps: the blank-screen button sends `.` / `,` (the PowerPoint convention the hardware targets), not `B` / `W`; and **the speaker console answered to no key at all** (KEY-1) — its window is `alwaysOnTop`, so it is the one holding focus for most of a talk, and a clicker pointed at it did nothing. Both fixed. `PresenterClickerTest` (CLK-1) asserts the virtual key codes; `FullscreenDeckKeyTest` sends them into a real composition. A physical clicker remains a hardware claim on the manual script. ADR-005 Phase 0. |
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
| **PLT-01** | **CI pipeline** | 🟡 | S | — | **Manual-dispatch pipeline shipped 2026-08-07; automatic triggers still deferred — 🟡 for exactly that reason.** `.github/workflows/ci.yml` runs the two `verify.ps1` commands (`desktopTest :skaldoria-markdown:test`, then `-PwarningsAsErrors`) on `ubuntu-latest`, and `workflow_dispatch` is its **only** trigger: the deferral was on **cost, not principle**, and a `push:`/`pull_request:` trigger bills hosted minutes on every commit whether or not anyone wanted an answer, whereas a button bills only when pressed. Keeping the local script as the same two commands is what made the port transcription rather than design — as predicted here. **What is still missing is the part that mattered:** nothing checks a push, so `verify.ps1` on the developer's machine remains the gate, and a per-commit trigger waits on a budget or a public repository. A headless runner also verifies *less* than that machine (`PLT-08`); the `render_tests: xvfb` input closes some of that gap. History: an earlier workflow was written, never executed on a runner, and deleted by `566e4b7` (a commit labelled as a README change — the deletion looked accidental but matched the decision). Its one non-obvious finding is preserved in PLT-08. |
| **PLT-02** | Verify macOS and Linux packaging | 📋 | M | PLT-08 | `.dmg`, `.pkg`, `.deb`, `.rpm` targets are declared and unproven. `PLT-01` does not cover this — the manual workflow runs tests on `ubuntu-latest`, not packaging on three OSes — so it stays a manual pass on real machines, and the first attempt found PLT-08 rather than a packaging result. Note the `.dmg`/`.pkg` targets ship **no icon**: only `windows { iconFile }` and `linux { iconFile }` are configured, and there is no `.icns` in `src/desktopMain/resources/icons`. |
| **PLT-03** | UI localisation | 🕓 | L | — | Every string is inline today. |
| **PLT-04** | Screen-reader semantics and keyboard-only operation | 📋 | M | — | The project holds itself to WCAG 2.1 AA for *contrast* and has real machinery for it. Contrast is one clause of one guideline; navigability is untested. |
| **PLT-05** | Crash reporting / diagnostics bundle | 📋 | M | — | Related: `F-03` in the refactoring backlog notes `remoteServerError` is an overloaded error channel. |
| **PLT-06** | Auto-update | 🕓 | L | PLT-02 | Was gated on PLT-01 too; `PLT-01` now ships tests on demand, not artefacts, so a release is still cut and verified by hand — `scripts/release.ps1` / `scripts/build_linux.sh`. |
| **PLT-07** | Command palette coverage for every action | 📋 | S | — | The palette exists; whether it reaches all commands should be audited as new features land. |
| **PLT-08** | **Render guards must skip, not fail, without a display** | ✅ | S | — | **Shipped 2026-08-07.** `RenderEnvironment` skips via a JUnit assumption when `GraphicsEnvironment.isHeadless()`, overridable with `-PskipRenderTests` (forwarded to the forked test JVM, which a bare `-D` is not). The blanket `@Ignore` is gone and **`FullscreenDeckKeyTest`'s 8 tests run again**. Skips report as skipped, never as passed. Original note: | Building the Linux packages under WSL fails the `ImageComposeScene` suites, because there is no display for Skia to target — the reason `FullscreenDeckKeyTest` currently carries a blanket `@Ignore`. That `@Ignore` was added inside an unrelated commit with no note, so **8 passing tests covering the presenter window's entire keyboard are silently inert on developer machines that *do* have a display** — including the guard `FEATURE_INDEX` itself credits with finding that `H`, the arrows, blackout and the clicker were all dead. Replace it with a headless-conditional skip (`GraphicsEnvironment.isHeadless`, or a `skaldoria.skipRenderTests` property) so the guards run wherever they can and stand down where they cannot. |
| **PLT-09** | A packaging path that does not need a display | ✅ | S | PLT-08 | **Shipped 2026-08-07.** `scripts/build_linux.sh` ran `./gradlew desktopTest` before packaging — the exact command that failed under WSL. It now detects a missing `DISPLAY`/`WAYLAND_DISPLAY`, says so, and passes `-PskipRenderTests`. Documented in CONTRIBUTING, including why `@Ignore` is the wrong answer. Original note: | Follows from the same session: producing installers should not require the render suites to run at all. A `-x desktopTest` packaging recipe, or a Gradle task that packages without the graphical guards, documented in CONTRIBUTING. |

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
`OUT-01` offline HTML export ·
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
>
> **And it caught something else the same day.** `AUD-08` was sized `S` on the reasoning that a
> clicker is an HID keyboard and the codes were already bound — which `AppCommandsTest`,
> `KeyBindingsTest` and the new `PresenterClickerTest` all confirm. All three assert that the
> *registry resolves*. None of them could notice that `PresenterView` never called it: 828
> lines, no key handling, `alwaysOnTop`. Writing `FullscreenDeckKeyTest` — which the ADR asked
> for and nobody had written — took ten minutes and found that <kbd>H</kbd>, the arrows,
> blackout and the clicker were all dead in the window a speaker spends the talk looking at.
>
> The recurring shape, now stated three ways: **a table that resolves is not a key that
> arrives, an index that advances is not a match that is visible, and a model that parses is
> not a pixel that is drawn.** Every guard this project adds should end at the user.

### Now — make the guards tell the truth

**Re-planned 2026-08-07.** The previous tier was "prove the build works off this machine" and
led with CI; automatic CI is deferred on cost, and what exists is a manual-dispatch workflow
(`PLT-01`). What replaced it in this tier is sharper and came out of the same week: **three
separate guards in this codebase were green while the thing they guard was broken or switched
off.** That is the risk CI was meant to cover, and CI does not cover it — every one of the three
passes on a runner too, dispatched or not.

| | Item | Note |
| :--- | :--- | :--- |
| 1 | **`PLT-08` headless-conditional render guards** | The highest-value item in the document. A blanket `@Ignore` added in an unrelated commit has 8 keyboard tests inert on every machine, to work around a WSL run with no display. Fix the cause and the guards come back everywhere they can run. |
| 2 | **Audit the render guards for live-composition coverage** | `EditorWorkspaceRenderingTest` mutates-then-renders, so it is structurally blind to any defect that needs an already-running composition — which is how `AUT-03` shipped broken. `FindRevealScrollTest` shows the shape; the other render suites have not been checked. Internal quality, so it belongs in `REFACTORING_BACKLOG.md`, but it gates trust in every ✅ in this document. |
| 3 | `PLT-09` packaging without a display | Unblocks `PLT-02` on the machine the owner actually packages from. |
| 4 | `AUT-06` line-number gutter | Newly cheap: `AUT-05` landed with an explicit `ScrollState` and `onTextLayout`, which is exactly the foundation a gutter needs. Option A would not have provided it. |

### Next — remove the sharpest edges

`AUT-19` find results list · `AUT-20` repeat-search shortcut · `AUT-17` pipe-less GFM tables ·
`AUT-10` drag-and-drop reordering · `THM-01` font themes · `AUT-12` clipboard image paste ·
`AUD-10` SoftAP pairing guidance · `MED-02` SVG images · `DEL-04` rehearsal timings *(now cheap — DEL-11 shipped the storage)* ·
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
| `PLT-01` **automatic** CI triggers | A budget for runner minutes, or the repository going public. The workflow itself exists and is dispatched by hand; only `push:`/`pull_request:` waits on the trigger, because that is the part that costs money per commit. |

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
| [`PERFORMANCE_BASELINE.md`](./PERFORMANCE_BASELINE.md) | What the per-keystroke paths cost, measured. Read it before sizing anything that runs while the user types — and note the incremental-parse item, which is the largest open performance question. |
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
