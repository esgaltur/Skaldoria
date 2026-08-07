# Improvement Plan — Editor Synchronisation, Find Reveal, and HUD Visibility

**Version:** 1.3.0 · **Drafted:** 2026-08-06 · **Status:** Phases 1–5 shipped, Phase 6 deferred · **Suite:** 235 → 575 tests, 0 failures

> **Phases 1–5 are done.** Phase 1 (HUD visibility) went further than planned — it is persisted
> across launches, which the original plan listed as out of reach. Phases 2–5 landed together
> rather than as five commits; the sequencing argument below still holds and is kept because it
> is *why* the work is safe, not merely how it was scheduled.
>
> Two things differ from the plan and are recorded in
> [ADR-004 § Status](./adr/004-editor-sync-and-presentation-hud.md#status): the composable took
> **Option B** (explicit `ScrollState` + `onTextLayout`) rather than Option A, and revealing a
> search match now also selects the match's slide.
>
> **Phase 6 (deck-wide search) remains deferred** and is still not a commitment.

Companion to [ADR-004](./adr/004-editor-sync-and-presentation-hud.md). The ADR records *why*; this
document is the *what, in what order, and how we know it worked*. Where the two disagree, the
ADR wins and this file is stale.

---

## Contents

- [Scope](#scope)
- [Why this order](#why-this-order)
- [Phase 1 — HUD visibility](#phase-1--hud-visibility)
- [Phase 2 — Caret foundation](#phase-2--caret-foundation)
- [Phase 3 — Find reveal](#phase-3--find-reveal)
- [Phase 4 — Slide → editor sync](#phase-4--slide--editor-sync)
- [Phase 5 — Editor → slide sync](#phase-5--editor--slide-sync)
- [Phase 6 — Deck-wide search (deferred)](#phase-6--deck-wide-search-deferred)
- [Files touched, by phase](#files-touched-by-phase)
- [Test plan](#test-plan)
- [Definition of done](#definition-of-done)
- [Manual verification script](#manual-verification-script)

---

## Scope

Three reported defects:

1. Selecting a different slide does not move the markdown editor to that slide.
2. The presentation HUD overlays slide content and cannot be hidden.
3. The editor's find buttons appear to do nothing.

**1 and 3 share one root cause** — the source pane uses the `String` overload of `TextField`
(`EditorWorkspace.kt:210`), so the application has no caret, selection or scroll handle. 2 is
independent.

Not in scope: rewriting `MarkdownVisualTransformation`, migrating to the `TextFieldState` API,
line-number gutters, or changing which controls the HUD contains.

---

## Why this order

The reported priority was sync → HUD → search. The implementation order is HUD → foundation →
search → sync, for two reasons:

- **Phase 1 is independent and low-risk**, so it ships user-visible value on day one without
  waiting on the risky work.
- **Phases 3–5 all sit on Phase 2**, which touches the control the user types into all day.
  Phase 2 is landed alone, with no behaviour change, so it can be reverted without taking any
  feature with it.

Phase 3 lands before Phase 4 because it is the smaller consumer of the same foundation — it
proves the reveal mechanism on one call site before four more depend on it.

---

## Phase 1 — HUD visibility

**Goal.** The presentation HUD stops covering slide content, without changing which controls it
holds. Guards HUD-1, HUD-2, HUD-3.

### Tasks

| # | Task |
| :--- | :--- |
| 1.1 | Add `enum class HudVisibility { AUTO, PINNED, HIDDEN }`. `AUTO` is the default. |
| 1.2 | Add `hudVisibility` to the delivery/presentation state, with a `cycleHudVisibility()` that advances `AUTO → PINNED → HIDDEN → AUTO`. |
| 1.3 | Bind bare `H` in the `FullscreenDeck` key handler (`FullscreenDeck.kt:58`). `H` is currently unused — the handler consumes `Ctrl+K`, `Ctrl+Z`, `B`, `W`, `G`, `L`, `P`, `C`, arrows/space/page, `Esc`/`F11`. |
| 1.4 | Wrap the HUD `Row` (`FullscreenDeck.kt:158`) in an `AnimatedVisibility` driven by an `isHudOnScreen` derived value. |
| 1.5 | Implement the `AUTO` idle timer: show on pointer movement, hide after ~3s idle. Motion consumed by `SlideAnnotationOverlay` must **not** reset the timer, or pen/laser mode holds the HUD open permanently. |
| 1.6 | Add a command-palette entry mirroring the toggle, so it is reachable without the shortcut. |
| 1.7 | Show a one-off hint ("Press H for controls") the first time the HUD auto-hides in a session. Satisfies HUD-1. |
| 1.8 | Persist `hudVisibility` through `ConfigManager`, following the DED-2 pattern used for theme and editor font size. |

### Constraints

- **HUD-2** — the slide canvas must not resize. The HUD stays an overlay; do not convert the
  root `Box` into a `Column` that reserves space. Reserving space changes the fit-to-canvas
  scale, so the projected deck would stop matching the exported deck.
- **HUD-3** — the 3.dp `LinearProgressIndicator` at `FullscreenDeck.kt:356` is not part of the
  HUD and stays visible in every state.
- `SlideSurface` draws its own slide-number footer (`SlideSurface.kt:131`). Hiding the HUD must
  reveal it, not leave both hidden.

### Acceptance

- On a `FULL_CODE` slide, no HUD pixel overlaps the last line of the code block once hidden.
- `H` returns the HUD from every state without a mouse.
- The choice survives an application restart.

---

## Phase 2 — Caret foundation

**Goal.** The editor gains a caret and selection model. **No user-visible behaviour changes.**
Guards EDT-1, EDT-5.

This is the highest-risk phase in the plan. It lands as its own commit.

### Tasks

| # | Task |
| :--- | :--- |
| 2.1 | Create an `EditorSession` collaborator holding `editorSelection: TextRange`. Do **not** add loose properties to `PresentationState` — see [ADR-004 § Relationship to ADR-003](./adr/004-editor-sync-and-presentation-hud.md#relationship-to-adr-003). |
| 2.2 | Switch `EditorWorkspace.kt:210` from the `String` overload to the `TextFieldValue` overload. |
| 2.3 | Reconstruct the value each composition as `TextFieldValue(text = state.currentEditorText, selection = session.editorSelection)`. Text stays derived; only selection is stored. |
| 2.4 | In `onValueChange`, route `.text` through the existing `updateEditorContent(...)` and `.selection` into the session. |
| 2.5 | Clamp the selection to the new text length whenever the underlying text changes — slide-file swap in per-slide mode, `replaceAll`, file open. EDT-5. |

### The failure mode to avoid

Holding `TextFieldValue` as a second source of truth — typically a `remember` re-seeded from
`currentEditorText` — makes the caret jump to the end of the document on every keystroke. This
is the single most likely way to break the editor. Ownership is split precisely to prevent it:

- **Text** — `markdownText` / per-file project content remain the system of record.
  `currentEditorText` (`PresentationState.kt:175`) stays a derived read.
- **Selection** — the only new state.

### Acceptance

- Typing in the middle of a 500-line deck leaves the caret where it was. Verified by hand.
- `./gradlew desktopTest` stays at 235 passing.
- No behaviour differs from the previous commit in any other respect.

---

## Phase 3 — Find reveal

**Goal.** The find buttons visibly do something. Guards EDT-4.

### Root cause recap

`findNext()` (`PresentationState.kt:244`) advances `currentMatchIndex` and stops.
`MarkdownVisualTransformation` (`MarkdownVisualTransformation.kt:287`) correctly re-styles the
new active match — off-screen, because nothing scrolls. The matching model is complete and
correct; only the reveal is missing.

### Tasks

| # | Task |
| :--- | :--- |
| 3.1 | Add `revealRequest` to `EditorSession`: a target offset plus a monotonically increasing token, so two reveals to the same offset still fire. |
| 3.2 | Have `findNext()` / `findPrevious()` publish a reveal for `findMatches[currentMatchIndex].first`. |
| 3.3 | In `EditorWorkspace`, consume the request in a `LaunchedEffect(token)`: set the selection to the match range and let the field scroll the caret into view. |
| 3.4 | Return focus to the query field after the ▲/▼ buttons (`EditorFindBar.kt:207`, `:223`) are clicked, so the `Enter` / `Shift+Enter` handlers at `EditorFindBar.kt:115` keep firing. |
| 3.5 | State the search scope in the find bar. In project + per-slide mode the search covers only the current file — today the sole hint is the placeholder *"Find in slide source…"*, so a deck-wide search returns `No matches` and reads as a broken feature. |
| 3.6 | Once a caret exists, target the first match **at or after the caret** when the bar opens, rather than always match 0. |

### Acceptance

- With a match on line 400 of a 500-line deck, pressing ▼ scrolls it into view.
- Pressing `Enter` repeatedly after clicking ▼ keeps advancing.
- The bar states whether it is searching the slide or the deck.

---

## Phase 4 — Slide → editor sync

**Goal.** Selecting a slide prepares that slide for editing. Guards EDT-3.

### Tasks

| # | Task |
| :--- | :--- |
| 4.1 | Create `core/document/SlideSourceLocator.kt` — pure, Compose-free — with `offsetOfSlide(markdown, slide): Int`, derived from `Slide.sourceLineRange` (`SlideModels.kt:147`). **Do not** re-derive slide boundaries; COR-1 forbids it and `SlideDocument.sliceOf` (`SlideDocument.kt:42`) is the existing precedent. |
| 4.2 | Publish a reveal request from `goToSlide()` (`PresentationState.kt:678`), `next()` and `prev()`. |
| 4.3 | Cover both editor modes: in single-file mode reveal the offset within the whole deck; in project + per-slide mode the file swap already changes content, so reveal offset 0 and clamp. |
| 4.4 | Confirm the filmstrip click path (`EditorWorkspace.kt:374`) and the slide-grid overview both route through `goToSlide` and therefore inherit the behaviour. |

### Acceptance

- Clicking thumbnail #37 in a 50-slide deck scrolls the source pane to slide 37.
- Works for `---`-delimited, `#`/`##` heading-split, and `----` rule decks alike.

---

## Phase 5 — Editor → slide sync

**Goal.** Moving the caret selects the matching slide. Guards EDT-2.

### Tasks

| # | Task |
| :--- | :--- |
| 5.1 | Add `slideIndexAtOffset(markdown, slides, offset): Int` to `SlideSourceLocator`. |
| 5.2 | On caret movement, set `currentSlideIndex` — **without** publishing a reveal request. |
| 5.3 | Add a "follow caret" toggle, default on, so the behaviour can be turned off. |
| 5.4 | Tint the non-active slide's region in the editor, reusing the range-styling `MarkdownVisualTransformation` already performs for search matches. |

### The loop hazard

Forward sync moves the caret → reverse sync reads it and changes the slide → the slide change
re-triggers forward sync. **EDT-2 is the guard**: a reveal request is published by explicit
navigation only, never by caret movement. This must be asserted by test, not assumed.

### Acceptance

- Clicking into slide 12's text selects slide 12 in the preview and filmstrip.
- No oscillation: clicking a thumbnail then typing does not fight the cursor.

---

## Phase 6 — Deck-wide search (deferred)

Scoped but not committed. Search `markdownText` in project mode, map a hit back to file +
offset, and switch the active slide file to reach it. Depends on Phases 4 and 5.

---

## Files touched, by phase

| File | 1 | 2 | 3 | 4 | 5 |
| :--- | :-: | :-: | :-: | :-: | :-: |
| `ui/screens/FullscreenDeck.kt` | ● | | | | |
| `ui/components/CommandPalette.kt` | ● | | | | |
| `config/ConfigManager.kt` | ● | | | | |
| `state/PresentationState.kt` | ● | ● | ● | ● | ● |
| `state/EditorSession.kt` *(new)* | | ● | ● | ● | ● |
| `ui/screens/EditorWorkspace.kt` | | ● | ● | ● | ● |
| `ui/components/EditorFindBar.kt` | | | ● | | |
| `core/document/SlideSourceLocator.kt` *(new)* | | | | ● | ● |
| `ui/editor/MarkdownVisualTransformation.kt` | | | | | ● |

---

## Test plan

The project's rule is that a regression test **must fail before the fix**. Every guard below is
written against user-visible behaviour, not an internal index — which is the entire lesson of
defect 3.

| Guard | Asserts | Verified to fail before the fix by |
| :--- | :--- | :--- |
| `SlideSourceLocatorTest` | Round-trip offset ↔ slide index across `---`, `#`/`##`, and `----` decks — the three shapes COR-1 identified as divergent. Plus CRLF source, where summing line lengths drifts one character per line. | The unit not existing. |
| `EditorRevealTest` · reveal on find | `findNext()`/`findPrevious()` publish a reveal equal to the active match; a repeat reveal of the same match still fires. | Emptying `revealCurrentMatch()` — 9 of 14 fail. |
| `EditorRevealTest` · no loop | Caret movement does **not** change the reveal token; `goToSlide`/`next`/`prev` do. | Making `moveCaret` publish a reveal — both loop guards fail. |
| `EditorRevealTest` · caret jump | Typing mid-document leaves the caret where it was typed, not at the end. | — (state-level floor; see the manual script) |
| `EditorRevealTest` · clamp | Switching to a shorter document with a far-out selection clamps rather than throwing. | Selection state not existing. |
| **`EditorWorkspaceRenderingTest`** | The **rendered** source pane draws at all, differs after jumping to slide 40, and differs after finding a match on the last slide. | The pane being pixel-identical in both renders. |
| `HudVisibilityTest` | `H` cycles visibility from every state back to a visible one. | No binding existing. |
| HUD-2 render guard | Headless `ImageComposeScene` render, in the style of `SlideRenderingTest`: slide content pixels are **identical** with the HUD shown and hidden. | HUD visibility not being modelled. |

### Why the render guard was added

It is not in the original plan, and it is the one that would have caught a plausible bad
outcome. Every assertion in `EditorRevealTest` passes against a composable that publishes
reveal requests nothing consumes — a more sophisticated version of the defect this whole
document is about. `EditorWorkspaceRenderingTest` renders the studio window over 30 frames with
an advancing clock (one frame composes and runs effects but never lets the scroll animation
start) and compares the source pane's pixels. The rendered PNGs land in `build/render-check/`
and **were looked at**, per `RENDERING_STATUS.md`'s rule.

### Existing tests

`EditorFindAndReplaceTest` stays as it is — it correctly guards the *matching* model, which is
not what is broken. It should gain a comment pointing at `EditorRevealTest`, so the next reader
does not infer from its greenness that find-and-replace is covered end to end. Its passing
while the feature is unusable is the same failure shape QUALITY_BASELINE records for OVF-1.

---

## Definition of done

A phase is done when all of the following hold:

- [ ] `./gradlew desktopTest` passes, with the new guards included.
- [ ] Each new guard was confirmed to **fail** against the pre-change code.
- [ ] New invariant identifiers appear as comments beside the logic they constrain.
- [ ] `docs/QUALITY_BASELINE.md` gains the EDT-* / HUD-* entries and its suite count is updated.
- [ ] The manual script below passes for the phase's rows.
- [ ] Zero new Kotlin compiler warnings — the project is at zero today.

### Documentation debt — ✅ cleared 2026-08-06

Three documents disagreed with the actual suite size and with each other (221 / 221 / 204
against a real 235). All now read **472**, and `CHANGELOG.md`'s "70 to 221" is left alone
because it is a historical statement about the 1.2.0 release, not a claim about today.

**The recurring hazard, not the individual numbers:** a hardcoded count in prose goes stale the
moment anyone adds a test, and nothing fails when it does. If it drifts a third time, derive it
in CI rather than correcting it by hand again.

---

## Manual verification script

Rendering and editing are verified by using them, not by a green suite. Run against
`examples/companion_test_deck` and a single-file deck of 500+ lines.

| # | Step | Expected | Phase |
| :--- | :--- | :--- | :--- |
| 1 | Present a `FULL_CODE` slide. | HUD auto-hides; the last code line is fully visible. | 1 |
| 2 | Move the mouse. | HUD returns. | 1 |
| 3 | Press `H` three times. | Cycles pinned → hidden → auto; always recoverable. | 1 |
| 4 | Enter pen mode and draw. | HUD does not flicker or stick open. | 1 |
| 5 | Restart the app, present again. | HUD state is remembered. | 1 |
| 6 | Type in the middle of a long deck. | Caret does not jump to the end. | 2 |
| 7 | `Ctrl+F`, search a term near the end. | The match scrolls into view. | 3 |
| 8 | Click ▼, then press `Enter`. | Advances; focus was not lost. | 3 |
| 9 | Open a project deck, search a term on another slide's file. | The bar states the scope; no silent `No matches`. | 3 |
| 10 | Click thumbnail #37. | Source pane scrolls to slide 37. | 4 |
| 11 | Repeat on a heading-split deck. | Same behaviour. | 4 |
| 12 | Click into slide 12's source. | Preview and filmstrip follow. | 5 |
| 13 | Alternate thumbnail clicks and typing. | No oscillation; the cursor is never fought. | 5 |
