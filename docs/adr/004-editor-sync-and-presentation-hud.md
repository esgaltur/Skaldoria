# Architecture Decision Record (ADR)
## ADR-004: Editor Caret Model, Slide Synchronisation, and Presentation HUD Visibility

### Status
**Implemented** (2026-08-06) — Phases 1–5. Phase 6 remains deferred.

| Phase | Item | State |
| :--- | :--- | :--- |
| 1 | HUD visibility (`DEL-02`, HUD-1/2/3) | ✅ **Shipped** — `HudVisibility`, `H` binding, idle fade, persisted (DED-2) |
| 2 | Caret foundation (`AUT-05`, EDT-1/EDT-5) | ✅ **Shipped** — `EditorSession`, `TextFieldValue`, clamped selection |
| 3 | Find reveal (`AUT-03`, EDT-4) | ✅ **Shipped** — reveal token, focus return, scope stated in the bar |
| 4 | Slide → editor sync (`AUT-02`, EDT-3) | ✅ **Shipped** — `SlideSourceLocator`, reveal from `goToSlide`/`next`/`prev` |
| 5 | Editor → slide sync (EDT-2) | ✅ **Shipped** — `slideIndexAtOffset`, follow-caret toggle (default on) |
| 6 | Deck-wide search | ⬜ Deferred — unchanged, and still not a commitment |

> **What changed against the plan, and why.**
>
> **The composable took Option B, not Option A.** The alternatives table below chose the
> Material `TextField` and its built-in cursor-following scroll, with Option B — an explicit
> `ScrollState` plus `onTextLayout` — as the escalation if that proved imprecise. Option B was
> taken directly, because the reveal that matters most is a search hit and *the find bar holds
> focus while it fires*: a field's built-in cursor-following scroll is not specified to run for
> an unfocused field, so Option A risked shipping the original defect under a new mechanism.
> Scrolling explicitly also places the revealed line a quarter of the way down the viewport
> rather than merely somewhere inside it, which settles the "technically correct and feels
> wrong" risk recorded below. The cost is reproducing the Material container by hand, which is
> a `Box` in `EditorWorkspace.kt` and touches nothing in `core/` or `PresentationState`.
>
> **Ownership is exactly as specified.** Text derived, selection stored, `TextFieldValue`
> rebuilt every composition and never remembered. That half of the decision is what prevents
> the caret-jump regression and it was not varied.
>
> **One gap the plan did not anticipate.** Revealing a search match moved the source pane and
> left the preview and filmstrip behind, so the editor and the deck disagreed about where the
> user was. `revealCurrentMatch()` now selects the match's slide as well — through
> `SlideNavigator` directly, so it cannot publish a further reveal and EDT-2 still holds.

> This ADR records three reported defects,
> establishes that **two of the three share a single root cause**, and proposes the structure
> that removes that class of defect rather than patching each symptom. Implementation order is
> deliberately *not* the order the defects were reported in; the reasoning is in
> [Plan](#plan).

---

### Contents

- [The three reports](#the-three-reports)
- [Context](#context)
- [Problem A — the editor has no cursor model](#problem-a--the-editor-has-no-cursor-model)
- [Problem B — search is complete except for the part the user can see](#problem-b--search-is-complete-except-for-the-part-the-user-can-see)
- [Problem C — the presentation HUD is unconditional](#problem-c--the-presentation-hud-is-unconditional)
- [Decision](#decision)
- [Alternatives considered](#alternatives-considered)
- [Plan](#plan)
- [Invariants introduced](#invariants-introduced)
- [Guards](#guards)
- [Risks and failure modes](#risks-and-failure-modes)
- [Relationship to ADR-003](#relationship-to-adr-003)
- [Out of scope](#out-of-scope)

---

### The three reports

| # | Reported as | Actually |
| :--- | :--- | :--- |
| 1 | "No synchronisation with the markdown editor when I move to another slide." | The editor cannot be scrolled or positioned programmatically at all. |
| 2 | "The presentation toolbar sometimes sits over the text, e.g. source. I need to hide/show it." | The HUD is an unconditional overlay with no visibility state and no reserved space. |
| 3 | "Search in the editor does not work, the buttons do nothing." | Search works and is unit-tested. Nothing scrolls to the match, so it is invisible. |

**Reports 1 and 3 are the same defect.** Both are consequences of a single missing capability,
described in [Problem A](#problem-a--the-editor-has-no-cursor-model). Report 2 is unrelated and
independently fixable.

---

### Context

The studio window (`ui/screens/EditorWorkspace.kt`) is a three-pane layout: markdown source on
the left, live 16:9 slide preview on the right, thumbnail filmstrip along the bottom. The
presentation window (`ui/screens/FullscreenDeck.kt`) is a separate `Window` hosting the deck
full-bleed with a floating control HUD.

Two existing invariants constrain any solution here and must not be re-litigated:

- **COR-1** — slide boundaries come from `Slide.sourceLineRange`, produced by the parser.
  Nothing may re-derive them with its own splitter. Everything this ADR proposes for mapping
  between a slide and a source region consumes that range.
- **Markdown is the system of record** — every mutation is written to the deck markdown before
  it is considered applied. A caret model must not become a second, competing source of truth
  for the editor's text.

---

### Problem A — the editor has no cursor model

#### What the code does today

`EditorWorkspace.kt:210` binds the source pane with the **`String` overload** of Material 3's
`TextField`:

```kotlin
TextField(
    value = state.currentEditorText,          // String
    onValueChange = { state.updateEditorContent(it) },
    visualTransformation = MarkdownVisualTransformation(...),
    ...
)
```

The `String` overload does not surface `TextFieldValue`. Consequently the application has **no
handle on selection, caret offset, or scroll position** for the one control that most needs
them. This is not a missing feature in the sync code; the sync code has nowhere to attach.

#### Why slide selection does not move the editor

`state.goToSlide(index)` (`PresentationState.kt:678`) sets `currentSlideIndex` and clears the
blackout/whiteout/grid flags. That is all it can do. The filmstrip's
`.clickable { state.goToSlide(idx) }` therefore repaints the preview and moves the selection
border, and the source pane does not move — for a fifty-slide deck the editor is still showing
line 1 while the preview shows slide 37.

There are two distinct modes with different symptoms, and they are frequently conflated:

| Mode | `currentEditorText` resolves to | Symptom on slide change |
| :--- | :--- | :--- |
| Single-file (`MARKDOWN SOURCE`) | the whole deck (`markdownText`) | Editor does not move. **This is the reported bug.** |
| Project + per-slide (`isPerSlideEditorMode`) | that slide's own file | Content *does* swap, but the caret lands at an undefined offset in the new document. |

So the feature partially exists in project mode by accident of the content swap, and does not
exist at all in the mode most users are in. Any fix must cover both.

#### Why the reverse direction is also missing

The complementary behaviour — put the caret in slide 7's text, have the preview and filmstrip
follow — is not implementable today for the same reason: without `TextFieldValue` there is no
caret offset to map back. This is the half of "synchronisation" that makes the editor feel
live, and it is worth building, but it introduces a feedback-loop hazard covered in
[Risks](#risks-and-failure-modes).

#### The mapping itself is already available

Nothing new needs to be parsed. `Slide.sourceLineRange` is an inclusive line range into the
source, maintained by the parser, and `SlideDocument` already slices by it. Converting a line
range to a character offset is arithmetic over `markdown.lines()`. Both directions belong in a
pure, Compose-free unit under `core/`, matching the Tier A pattern that ADR-002 identified as
the one the codebase already does well.

---

### Problem B — search is complete except for the part the user can see

This is the most instructive of the three, because **the suite is green and the feature is
unusable.**

What exists and is correct:

- `PresentationState.findMatches` — `derivedStateOf`, cached under PRF-3, honouring
  case-sensitivity, whole-word and regex modes.
- `findNext()` / `findPrevious()` / `replaceCurrent()` / `replaceAll()` — all implemented.
- `MarkdownVisualTransformation` — already paints every match, and paints the *active* match
  differently (amber fill, black text, extra-bold) with `OffsetMapping.Identity`, so offsets
  are exact.
- `EditorFindBar` — a complete find/replace UI including match count, options and shortcuts.
- `EditorFindAndReplaceTest` — passing.

What is missing is one line of behaviour:

```kotlin
fun findNext() {
    val matches = findMatches
    if (matches.isNotEmpty()) {
        currentMatchIndex = (currentMatchIndex + 1) % matches.size
    }
    //  ← nothing tells the editor viewport to reveal matches[currentMatchIndex]
}
```

`findNext()` advances an index. The transformation duly re-styles the new active match. **The
match is off-screen**, because the viewport never moved — and it cannot be moved, for the
reason established in Problem A. The only on-screen feedback is the badge changing from
`3 of 12` to `4 of 12`, in a corner the user is not looking at. From the user's seat, the
button did nothing.

`EditorFindAndReplaceTest` asserts `currentMatchIndex` transitions and cycles correctly. It
passes. It will keep passing no matter how broken the user-visible behaviour is, because the
thing it asserts is genuinely working.

> This is the identical failure shape to **OVF-1**, the blanking regression, which QUALITY_
> BASELINE already records: *"A passing unit suite and a clean launch are not evidence that a
> slide draws correctly."* The same sentence applies verbatim to find-and-replace. The guard
> proposed in [Guards](#guards) is written to fail on the *reveal*, not on the index.

#### Secondary search defects found while tracing this

Worth fixing alongside, but none of them is the reported symptom:

1. **Scope is undisclosed.** In project + per-slide mode, search covers only the current
   slide's file, never the deck. The sole hint is the placeholder text *"Find in slide
   source…"*. A user searching a fifty-slide deck for a term on slide 40 gets `No matches` and
   reasonably concludes search is broken. The scope must be *stated* in the bar, and
   deck-wide search should be offered.
2. **Focus is lost on button click.** Clicking ▲/▼ moves focus out of the query field, so the
   `Enter` / `Shift+Enter` handlers bound in `EditorFindBar.kt:115` stop firing until the user
   clicks back into the field. This compounds the "buttons don't work" impression.
3. **Search always restarts at match 0.** Opening the bar with the caret at slide 30 jumps to
   the first match in the document. Once a caret exists (Problem A), the first `findNext()`
   should target the first match *at or after the caret*.
4. **`replaceCurrent()` re-parses the whole deck per replacement** via `updateEditorContent`.
   Acceptable for a single replace; noted so nobody builds a "replace in selection" loop on
   top of it.

---

### Problem C — the presentation HUD is unconditional

`FullscreenDeck.kt:158` places the control bar as a sibling of the slide inside the root `Box`:

```kotlin
Row(
    modifier = Modifier
        .align(Alignment.BottomCenter)
        .padding(bottom = 24.dp)
        ...
```

The slide occupies the full box (`fillMaxSize()`, inset 28.dp/20.dp). The HUD is drawn on top
of it, unconditionally, for the entire presentation. It is roughly 44.dp tall and sits 24.dp
from the bottom, so it occludes approximately the bottom **68.dp band** of the rendered slide —
which is exactly where a `FULL_CODE` slide's last lines land, and also where `SlideSurface`
draws its own slide-number footer (`SlideSurface.kt:131`). The HUD covers the deck's footer as
well as the content.

There is no `isHudVisible` state, no keyboard shortcut, and no auto-hide. The user's assessment
that the HUD itself is good is worth taking literally: **the control set is not the problem,
its permanence is.** The fix should preserve it exactly and change only when it is on screen.

The key handler at `FullscreenDeck.kt:58` currently consumes `Ctrl+K`, `Ctrl+Z`, and the bare
letters `B`, `W`, `G`, `L`, `P`, `C`, plus arrows/space/page keys and `Esc`/`F11`. **`H` is
free** and is the conventional binding for this.

---

### Decision

**1. Give the editor a caret and viewport model, by migrating the source pane to the
`TextFieldValue` overload — with the text itself remaining derived, not stored.**

The classic hazard in this migration is holding `TextFieldValue` as the source of truth
alongside the existing text, which produces a caret that jumps to the end of the document on
every keystroke. The design avoids it by splitting ownership:

- **Text** stays where it is. `markdownText` / the project's per-file content remain the system
  of record; `currentEditorText` stays a derived read.
- **Selection only** is new state (`editorSelection: TextRange`), owned by a cohesive
  `EditorSession` collaborator rather than added as loose properties on `PresentationState` —
  see [Relationship to ADR-003](#relationship-to-adr-003).
- The composable reconstructs `TextFieldValue(text = state.currentEditorText, selection =
  state.editorSelection)` on each composition, and `onValueChange` writes the text through the
  existing `updateEditorContent(...)` and the selection to `editorSelection`.

This keeps the "markdown is the system of record" invariant intact and introduces no second
authority for content.

**2. Introduce an explicit *reveal request*, distinct from selection.**

A `revealRequest: RevealTarget?` (or a target plus a monotonically increasing token) is
published by *explicit navigation only*: `goToSlide`, `next`, `prev`, `findNext`,
`findPrevious`. The composable consumes it in a `LaunchedEffect`, sets the selection, and lets
the field scroll the caret into view. Caret movement caused by the user **never** publishes a
reveal request. This separation is the mechanism that prevents the feedback loop described in
[Risks](#risks-and-failure-modes), and it is the load-bearing decision of this ADR.

**3. Map between slide and offset in a pure `core/` unit, driven by `Slide.sourceLineRange`.**

New: `core/document/SlideSourceLocator.kt`, Compose-free, with both directions —
`offsetOfSlide(markdown, slide)` and `slideIndexAtOffset(markdown, slides, offset)` — so the
mapping is unit-testable without a renderer, and COR-1 is honoured by construction.

**4. Make HUD visibility a three-state, persisted preference.**

`HudVisibility { AUTO, PINNED, HIDDEN }` — `AUTO` (default) shows the HUD and fades it out
after an idle period, revealing it again on pointer movement; `PINNED` is today's behaviour;
`HIDDEN` is keyboard-only. `H` cycles it, a command-palette entry mirrors it, and the choice
persists via `ConfigManager` following the DED-2 pattern.

**5. Do not reserve layout space for the HUD.**

The HUD stays an overlay. Reserving space would shrink the slide canvas, changing the
fit-to-canvas scale and making the on-screen deck geometrically different from the exported
one. See [Alternatives](#alternatives-considered).

---

### Alternatives considered

#### For the editor (Problem A / B)

| Option | Assessment |
| :--- | :--- |
| **A. `TextFieldValue` + selection-driven reveal** (chosen) | Smallest change that unblocks both defects. Keeps `MarkdownVisualTransformation` exactly as is. Relies on the field's built-in "keep the cursor visible" scrolling. |
| **B. `BasicTextField` inside an explicit `verticalScroll`, with `onTextLayout`** | More work, full control. Enables scrolling a slide's first line *to the top of the viewport* rather than merely into view, and later enables a line-number gutter and slide-boundary markers. **Adopt if A's scroll behaviour proves imprecise** — see Risks. |
| **C. Migrate to `BasicTextField` + `TextFieldState` (the "BTF2" API)** | **Rejected for now.** It is the modern API and offers first-class scroll control, but its transformation model (`OutputTransformation`) is aimed at input reshaping, not arbitrary span restyling, so `MarkdownVisualTransformation` — the syntax highlighter, contrast-enforced and load-bearing — would have to be rewritten against a different mechanism. That is a large, risky migration to buy a scroll offset. Recorded here so it is not attempted opportunistically; the exact 1.7.3 capability should be verified before anyone revisits. |

#### For the HUD (Problem C)

| Option | Assessment |
| :--- | :--- |
| **Toggle + auto-hide-on-idle** (chosen) | Preserves the control set the user values. Auto-hide addresses the common case with no interaction; the toggle covers the deliberate case. |
| **Toggle only** | Acceptable but leaves the default state overlapping content, so most users still hit the reported problem before discovering the shortcut. |
| **Reserve layout space so the HUD never overlaps** | **Rejected.** Shrinks the 16:9 canvas and alters the fit-to-canvas scale, so the projected deck no longer matches the exported deck. Also defeats the purpose of a full-bleed presentation window. |
| **Move the HUD off the slide (e.g. a side rail)** | **Rejected.** Trades a bottom-edge collision for a side-edge one and discards a control layout the user explicitly rates highly. |

---

### Plan

Implementation order is **not** the order the defects were reported in. Problem C is fully
independent and low-risk, so it ships first and delivers visible value immediately, while
Problems A and B share a foundation that must be laid carefully. Each phase leaves the
application fully working.

| Phase | Work | Depends on | Risk |
| :--- | :--- | :--- | :--- |
| **1** | **HUD visibility.** `HudVisibility` enum + state, `H` binding, idle timer, command-palette entry, `ConfigManager` persistence. | — | Low |
| **2** | **Caret foundation.** Migrate the source pane to the `TextFieldValue` overload; add `editorSelection` to `PresentationState`; clamp selection when the underlying text changes. No user-visible feature. | — | **Highest — see Risks** |
| **3** | **Search reveal.** `findNext`/`findPrevious` publish a reveal request for the active match; return focus to the query field after a button click; state the search scope in the bar. | 2 | Low |
| **4** | **Slide → editor sync.** `SlideSourceLocator.offsetOfSlide`; `goToSlide`/`next`/`prev` publish a reveal request. Covers both single-file and project modes. | 2, 3 | Medium |
| **5** | **Editor → slide sync.** `SlideSourceLocator.slideIndexAtOffset`; a "follow caret" toggle, default on; tint the non-active slide region in the editor via the existing transformation. | 4 | Medium — loop hazard |
| **6** | **Deck-wide search** (deferred). Search `markdownText` in project mode and map a hit back to file + offset, switching the active slide file as needed. | 4, 5 | Medium |

Phase 2 is the one to be careful with. It touches the single control the user types into all
day, and its failure mode — a caret that jumps to the end of the document on every keystroke —
is the kind of regression that makes the application unusable rather than merely wrong. It
should land as its own commit, with no behaviour change, so it can be reverted independently.

---

### Invariants introduced

Following the QUALITY_BASELINE convention: identifiers are permanent, appear in code comments
beside the logic they constrain, and are never renumbered.

| ID | Area | Invariant |
| :--- | :--- | :--- |
| **EDT-1** | Editor | The editor's text is derived from the deck; only *selection* is editor-owned state. There is never a second authority for content. |
| **EDT-2** | Editor | A reveal request is published by explicit navigation only. Caret movement never publishes one. |
| **EDT-3** | Editor | Slide ⇄ offset mapping derives from `Slide.sourceLineRange`. Nothing re-derives slide boundaries. (Extends COR-1.) |
| **EDT-4** | Editor | Every match-navigation action scrolls its match into view. Changing the active match index without revealing it is the defect, not the feature. |
| **EDT-5** | Editor | When the underlying text changes (slide-file swap, replace-all), the selection is clamped to the new length before it reaches the field. |
| **HUD-1** | Presentation | The HUD can always be brought back from any hidden state without a mouse. |
| **HUD-2** | Presentation | HUD visibility never changes the slide canvas size or its fit-to-canvas scale. |
| **HUD-3** | Presentation | The progress indicator is not part of the HUD and is unaffected by its visibility. |

---

### Guards

The project's rule is that a regression test must **fail before the fix**. Each guard below is
written against the user-visible behaviour, not the internal index — that is the whole lesson
of Problem B.

| Invariant | Guard | Confirmed to fail before the fix by |
| :--- | :--- | :--- |
| EDT-3 | `SlideSourceLocatorTest` — round-trip offset ↔ slide index across `---`-delimited, `#`/`##` heading-split, and `----` rule decks (the three shapes COR-1 identified as divergent). | The unit not existing. |
| EDT-4 | `EditorRevealTest` — `findNext()`/`findPrevious()` must publish a reveal target equal to the active match, and a *second* reveal of the same match must still fire. | Emptying `revealCurrentMatch()`: 9 of 14 tests fail. |
| EDT-2 | `EditorRevealTest` — moving the caret must **not** change the reveal token; `goToSlide`/`next`/`prev` must. | Making `EditorSession.moveCaret` publish a reveal: the two loop-guard tests fail. |
| EDT-1 | `EditorRevealTest` — typing mid-document leaves the caret where it was typed, and a whole-deck replacement is reflected in the editor. | — (a state-level floor; the field itself is checked by hand, see the manual script) |
| EDT-5 | `EditorRevealTest` — switching to a shorter document with a far-out selection must clamp, not throw. | Selection state not existing. |
| EDT-4 | `EditorWorkspaceRenderingTest` — the **rendered** source pane must differ after jumping to slide 40, and after finding a match on the last slide. | The pane being pixel-identical in both renders before Phase 4. |
| HUD-2 | A headless `ImageComposeScene` render, in the style of `SlideRenderingTest`: the slide's rendered content pixels must be **identical** with the HUD shown and hidden. | HUD visibility not being modelled. |
| HUD-1 | `HudVisibilityTest` — `H` cycles visibility from every state back to a visible one. | No binding existing. |

`EditorWorkspaceRenderingTest` is the guard that matters most, and it is the one the plan did
not ask for. Every state-level test in `EditorRevealTest` would pass against a composable that
published reveal requests into a void — which is a more sophisticated version of the exact
defect this ADR was written about. Only a render shows that the pane in front of the user
moved. It renders 30 frames with an advancing clock, because a single frame composes and runs
effects but never lets the scroll animation start, and so can only ever show the pane *before*
the reveal.

`EditorFindAndReplaceTest` stays as is — it correctly guards the matching model, which is not
what is broken. It should gain a comment pointing at `EditorRevealTest`, so the next reader
does not conclude from its greenness that find-and-replace is covered.

---

### Risks and failure modes

**The caret-jump regression (Phase 2).** If `TextFieldValue` is held in a `remember` that is
re-seeded from `currentEditorText` on each recomposition, the caret resets to the end of the
document on every keystroke. This is the single most likely way to break the editor, and it is
why EDT-1 splits ownership: text derived, selection stored. Phase 2 must land alone and be
exercised by hand — typing in the middle of a long document — before Phase 3 builds on it.

**The synchronisation feedback loop (Phase 5).** Forward sync moves the caret; reverse sync
reads the caret and changes the slide; the slide change re-triggers forward sync. Left
unguarded this oscillates or fights the user's cursor. EDT-2 is the mechanism that prevents
it — reverse sync updates `currentSlideIndex` *without* publishing a reveal request — and it
must be asserted by test, not assumed.

**Scroll precision (Phase 3/4).** Option A relies on the field's built-in cursor-following
scroll, which guarantees the caret is *visible*, not that it is *well-placed*. Revealing a
slide may land its first line at the very bottom of the viewport, which is technically correct
and feels wrong. If that proves to be the experience, escalate to Option B — an explicit
`ScrollState` plus `onTextLayout`, scrolling to `getLineTop(line)` — which is a contained
change to the composable and does not disturb anything in `core/` or `PresentationState`.

**Auto-hide versus pen mode (Phase 1).** Pen and laser modes generate continuous pointer
motion, which would hold the HUD open permanently under a naive idle timer. Motion consumed by
the annotation overlay should not reset the HUD timer.

**Fullscreen discoverability (Phase 1).** A user who lands in `HIDDEN` with no memory of `H`
has no visible way back. HUD-1 is stated for exactly this reason; a brief on-screen hint when
the HUD first auto-hides is the cheapest way to satisfy it.

---

### Relationship to ADR-003

[ADR-003](./003-god-object-decomposition.md) was drafted the same day and finds that
`PresentationState` is a god object — 1323 lines, 60 properties, 38 of them Compose state — and
proposes reducing it to a thin facade over cohesive collaborators.

This ADR would otherwise add three more properties to exactly that class, making its finding
measurably worse. The two are therefore reconciled as follows, and the reconciliation is not
optional:

- The new editor state (`editorSelection`, `revealRequest`, `followCaret`) lands as a single
  cohesive `EditorSession` collaborator — the same shape ADR-003 proposes — exposed through
  `PresentationState` if a facade is wanted. It is **not** three loose `mutableStateOf`
  properties bolted onto a 60-property class.
- `HudVisibility` belongs with the presentation/delivery state, not with editor state.
- `SlideSourceLocator` is a pure `core/` unit, which is the boundary ADR-003 and ADR-002 both
  push toward, so it adds nothing to the god object at all.

Neither ADR blocks the other. If ADR-003 is implemented first, this work slots into the
collaborator it creates; if this work goes first, `EditorSession` is one collaborator ADR-003
no longer has to extract. The only outcome to avoid is implementing this ADR *ignoring*
ADR-003, which is why it is recorded here rather than left to be noticed at review.

---

### Out of scope

Deliberately excluded, to keep the change reviewable:

- Rewriting `MarkdownVisualTransformation` or migrating to the `TextFieldState` API
  (Alternative C).
- Line numbers, a folding gutter, or slide-boundary markers in the editor margin — all become
  *possible* under Option B, none is required by the three reports.
- Restructuring the HUD's control set. The user's assessment of it is that it is correct; this
  ADR changes only when it is drawn.
- Deck-wide search (Phase 6) is scoped and sequenced here but is not part of the commitment.
