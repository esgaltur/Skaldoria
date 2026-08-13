# Slide Rendering — verified status

**Last updated:** 2026-08-06
**Suite:** 541 tests, 0 failures
**Architecture:** ✅ **ADR-002 implemented** (steps 1–6) + code quality refactored — Geometry refactored to pure, testable `arrange()` functions with centralized `DesignTokens`; cognitive complexity reduced via extraction; build stable and verified
**Evidence:** slides rendered headless via `ImageComposeScene`; PNGs in `build/render-all/` and `build/render-check/`.

Regenerate with:

```
./gradlew :skaldoria-presentation:desktopTest --tests "*RenderAllProbe*"     # sweep -> build/render-all/
./gradlew :skaldoria-presentation:desktopTest --tests "*SlideRenderingTest*" # guard  -> build/render-check/
```

> **Read this first.** Everything below marked ✅ was confirmed by *looking at the rendered
> image*. Anything marked ❓ was rendered but **not inspected**. Nothing here is claimed on
> the basis of "tests pass" alone — that is precisely what let the blanking regression ship.

---

## Summary

| | Count |
|---|---|
| ✅ Verified working | 17 |
| ⚠️ Renders, with a defect | 1 |
| ❓ Rendered, not inspected | 0 |
| 🔲 Not implemented | 0 |

**2026-08-06:** the eight outstanding ❓ cases were regenerated and **looked at**. All eight
render; two carry defects that only a human eye would catch, recorded below.

---

## What the harness cannot see

| Case | Why |
|---|---|
| **The mouse pointer (THM-05)** | Composited by the window system, never by Skia, so a themed cursor appears in no `ImageComposeScene` frame. The colour choice is pure and guarded by `PointerContrastTest` (contrast ratio achieved, not branch taken); **that the arrow is drawn is a manual check.** |
| **Keyboard focus in a text field (EDT-7)** | `ImageComposeScene` has no platform text-input session. `FocusRequester.requestFocus()` returns without throwing and the field still reports `isFocused == false`, so the focused-vs-unfocused container tint never changes and no pixel assertion about a caret can hold. Measured while fixing EDT-7, not assumed. The state contract is guarded by `EditorRevealTest`; **the drawn caret is a manual check.** |

---

## ✅ Verified working

| Case | Evidence | Notes |
|---|---|---|
| **Flowchart topology** | `render-check/flowchart.png` | Star renders correctly: `Markdown Studio → Skaldoria Core` with three branches fanning out. MMD-1's layered layout is doing its job. |
| **Flowchart overflow** | `render-all/02_overflow_flowchart.png` | 11-node graph (7 workers + 4-node chain) fits entirely; edges stay aligned with nodes at reduced scale. |
| **Flowchart edge labels** | `render-check/flowchart.png` | `Compile AST`, `Direct 120 FPS`, `WebSocket Sync`, `Auto Pacing` all complete and clear of the nodes. Lane gap is sized to the widest measured label. |
| **Sequence diagram (normal size)** | `render-check/sequence.png` | Real lifelines, participant headers with `as` aliases resolved, directional arrows, dashed replies. |
| **Bullet overflow** | `render-all/01_overflow_bullets.png` | 25 bullets all visible, full width. Was 9 of 25 with 16 silently dropped. |
| **Sequence diagram overflow (R-1 fixed)** | `render-all/03_overflow_sequence.png` | All 14 messages visible (0-13). Previously messages 9-13 were clipped at the bottom. FitToCanvas now properly shrinks the diagram to fit, and SequenceDiagramView reports its intrinsic size. |
| **Mid-link arrow labels + hexagons (MMD-7/8)** | `render-all/13_td_midlabel.png`, `14_lr_hexagon.png` | `A -- Yes --> B` edges are captured (previously dropped, orphaning target nodes) and `{{hexagon}}` nodes parse with a clean label. Both real-world SCS presentation diagrams render fully connected. |
| **Vertical flowchart (TD)** | `render-all/05_vertical_flowchart.png` | `flowchart TD` with a diamond decision node: `Yes`/`No` branches are distinct and every node is connected. |

---

## ❌ Confirmed broken — FIXED ✅

### ✅ R-1 · Sequence diagram clips when there are many messages · FIXED

**Evidence:** `render-all/03_overflow_sequence.png` — all 14 messages now render (0-13 fully visible).

**Fix Applied:** `SequenceDiagramView` now:
1. Reports its intrinsic size instead of `fillMaxSize()` by computing total width/height from participant count and message count
2. Is wrapped in `FitToCanvas` to uniformly scale when content exceeds available space
3. Uses proper Compose size modifiers to define the Box dimensions before rendering the Canvas

**Status:** ✅ RESOLVED

### ✅ R-2 · Diagram header was hardcoded · FIXED

**Evidence:** Previously "MERMAID ARCHITECTURE DIAGRAM" appeared for all diagram types; now correctly shows "SEQUENCE DIAGRAM" or "ARCHITECTURE FLOWCHART".

**Fix Applied:** `MermaidDiagramCanvas` now computes `diagramTypeLabel` dynamically:
```kotlin
val diagramTypeLabel = remember(sequence, diagram.type) {
    when {
        sequence != null && !sequence.isEmpty -> "SEQUENCE DIAGRAM"
        diagram.type == "flowchart" -> "ARCHITECTURE FLOWCHART"
        else -> "MERMAID DIAGRAM"
    }
}
```

**Status:** ✅ RESOLVED

### ✅ LR-1 · Horizontal flowchart edge labels stacked / nodes appeared "cut" · FIXED

**Reported symptoms:** In an LR netting diagram the four edge labels stacked on top of
each other ("labels are above"); in a TD decision chart the top/bottom nodes looked "cut"
and the `F -- Yes --> G` node had *no connection at all*.

**Real root cause (two bugs, not label positioning):**
1. **Dropped edges.** The parser's arrow token only matched the `A -->|label| B` form. Mermaid's
   mid-link form `A -- Yes --> B` (and `== x ==>`, `-. x .->`) was silently ignored, so those
   edges never made it into the graph. A dropped edge orphans its target node; `FlowchartLayoutEngine`
   then places the disconnected component overlapping others — which reads as a "cut" node.
2. **Mangled `{{hexagon}}` nodes.** The single-brace `{diamond}` branch captured `{Existing…`
   and left a stray `}`, corrupting the node label.

**Fix Applied:**
- `MermaidParser` (`MermaidDiagramCanvas.kt`): added `ARROW_MIDLABEL_TOKEN` + `matchArrow()` so
  `-- text -->`, `== text ==>`, `-. text .->` are parsed (dashed inferred from the `.`), and added a
  `{{…}}` group to `NODE_TOKEN` (ordered before `{…}`, same lesson as `((circle))` before `(round)`).
- `FlowchartGraphView.drawEdge`: horizontal labels now sit at the **geometric midpoint** of the edge
  (uses *both* endpoints, so it separates fan-out and fan-in alike), with **collision avoidance** —
  a shared `placedLabels` rect list nudges any label that would overlap an already-placed one along
  the cross axis. This fixes both fan-in stacking (LR netting) and tightly-spaced fan-out
  compression (the `render-check/flowchart.png` three-way branch).

**Regression tests:** `MermaidParserTest` — *mid-arrow edge labels are captured*, *real-world TD
decision chart keeps every edge and node*, *double-brace hexagon nodes parse with a clean label*.

**Status:** ✅ RESOLVED

### ✅ TEXT-CLIP · Node labels truncated in diagrams · FIXED

**Issue:** Long node labels were silently clipped without any indication.

**Fix Applied:** Added text overflow handling to NodeCard:
- Main label: `maxLines = 3, overflow = TextOverflow.Ellipsis`
- ID label: `maxLines = 1, overflow = TextOverflow.Ellipsis`

This allows multi-line labels with proper ellipsis for overflow instead of silent clipping.

**Status:** ✅ RESOLVED

---

## ⚠️ Renders, with a defect

### A poll slide silently drops everything except the poll

**Evidence:** `render-all/12_poll.png`, generated from a deck containing `- Vote now`.

The poll itself is correct: lettered option badges, per-option counts and percentages, the
pairing QR, the audience URL and a running total. The bullet is **absent**, because
`PollSlide` does `slide.elements.filterIsInstance<SlideElement.Poll>().firstOrNull()` and
renders nothing else.

Same class as EXP-3, where tables, images and polls vanished from the image export: the parser
produces the elements, the layout quietly discards them. An author who adds context bullets to
a poll slide sees them disappear with no indication.

---

## ✅ Verified working (2026-08-06 pass)

| Case | Evidence | Notes |
|---|---|---|
| **Data table** | `render-all/06_table.png` | Header row, three data rows, alternating row shading, columns aligned, footer intact. |
| **Split text & code** | `render-all/07_code.png` | Kotlin syntax highlighting, line numbers, language badge, window chrome. *Cosmetic:* the bullet column is bottom-weighted while the code sits top-right, so the two halves look unbalanced. |
| **Big quote** | `render-all/08_quote.png` | Serif italic quote, decorative opening mark, attribution in its own pill. |
| **Big metric** | `render-all/09_metric.png` | `120 FPS` at display size with the label beneath, centred in its card. |
| **Hero title** | `render-all/10_hero.png` | Eyebrow pill, title, subtitle and the trailing paragraph all present and centred. |
| **Math formula** | `render-all/11_math.png` | `Δt = t_elapsed − T/N · i` — Greek delta, subscript, a real fraction bar with numerator over denominator, centre dot. Matches the probe input exactly (which has no subscripts inside the fraction). |
| **Live poll** | `render-all/12_poll.png` | See the defect above; the poll element itself renders correctly. |
| **Sequence blocks** | `render-all/04_sequence_blocks.png` | `loop` frame and label, `participant … as` aliases resolved, solid calls versus dashed replies, the `--x` terminator as an ✕, the `U->>U` self-call as a loop-back — and `else failure` now a dashed divider with `[failure]` in the corner, where it used to draw a centred box indistinguishable from a note. |

---

## ✅ Images — implemented and verified (COR-10)

`![](…)` now renders. Local paths resolve against the deck folder, absolute paths and `file:`
URLs work, and `http(s)` sources are fetched with timeouts and a 24 MB mid-stream ceiling.
Decoding runs off the UI thread and is cached by path + mtime.

**Verified visually** on `examples/companion_test_deck` slide 16, in both states:
- with `assets/pairing.png` present — real pixels, aspect preserved, fitted to the panel;
- with the asset absent — alt text plus `File not found: .\assets\pairing.png`, rather than a
  blank panel.

Other schemes (`data:`, `javascript:`, `ftp:`) are refused at resolution. Covered by
`ImageResolverTest` (11 cases).

---

## What changed to get here

### Initial fixes (OVF-1/OVF-2)

1. **Reverted `FitToCanvas` from `SlideSurface`.** It measured with `maxHeight = Constraints.Infinity`; every layout sizes its content area with `Modifier.weight(1f)`, which collapses to zero height under an unbounded main axis. This blanked *every* slide — bullets, diagrams, tables — leaving only titles.
2. **Applied `FitToCanvas` where it is actually safe** — around intrinsically-sized content only: `FlowchartGraphView`, and the bullet column inside `BulletListSlide`.
3. **Two-pass measurement in `FitToCanvas`.** Uniform scaling shrinks width as well as height, so a full-width list became a narrow centred strip. Content is re-measured at `width / scale`.
4. **Top-left transform origin.** Centring an unscaled placeable and scaling about its centre placed oversized content off-screen. Placement now happens in scaled coordinates.
5. **Lowered the shrink floor** from `0.5` to `0.25`. At `0.5` the content stopped shrinking while still overflowing.
6. **Flowchart edge labels** moved into the lane gap, and the gap is sized to the widest measured label.

### R-1 Fix (Sequence diagram overflow)

7. **SequenceDiagramView now computes intrinsic size.** Before wrapping it in `FitToCanvas`, it measures the total width needed (participants × columnWidth) and height needed (header + rows × ROW_HEIGHT). The Canvas is now a child of a properly-sized Box, not `fillMaxSize()`.
8. **Wrapped in FitToCanvas.** Once the view reports its natural bounds, `FitToCanvas` can safely shrink it when it overflows, just as it does for flowcharts.

### R-2 Fix (Diagram type label)

9. **Dynamic diagram type labeling.** `MermaidDiagramCanvas` now switches on the diagram type and sequence diagram presence to set the correct label, instead of hardcoding "MERMAID ARCHITECTURE DIAGRAM".

### MMD-7/8 Fix (mid-link arrows, hexagons, label collision)

10. **Mid-link arrow labels parsed.** `MermaidParser` now recognises `A -- text -->`, `A == text ==>` and `A -. text .-> B` in addition to `-->|text|`. Previously these edges were silently dropped, orphaning their target nodes and wrecking the layout so nodes appeared "cut".
11. **`{{hexagon}}` nodes parsed.** Added a double-brace group ahead of the single-brace `{diamond}` branch so hexagon labels are no longer captured with a stray `}`.
12. **Edge-label collision avoidance.** Horizontal labels sit at the edge's geometric midpoint (separating fan-in and fan-out), and a per-render `placedLabels` list nudges overlapping labels apart along the cross axis so tightly-stacked branches stay readable.

## Guard against recurrence

`SlideRenderingTest` renders slides via `ImageComposeScene` and asserts content pixels exceed a title-only floor. **It was validated by reintroducing the blanking regression** — all four tests fail with messages like *"flowchart drew only 1623 content pixels — the diagram is missing"*. A regression test that does not fail on the regression is worthless, so this one was checked.

Its limitation is worth stating: it detects *"nothing drawn"*, not *"drawn wrong"*. R-1 (clipped sequence messages) passes it, because a clipped diagram still puts plenty of pixels on screen, until `FitToCanvas` forces the whole diagram to fit. Visual inspection remains necessary for correctness, but now the common overflow case (too many rows/nodes) is automatically corrected.
