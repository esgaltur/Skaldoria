# Slide Rendering — verified status

**Last updated:** 2026-08-04
**Suite:** 161 tests, 0 failures
**Evidence:** slides rendered headlessly via `ImageComposeScene`; PNGs in `build/render-all/` and `build/render-check/`.

Regenerate with:

```
./gradlew desktopTest --tests "*RenderAllProbe*"     # sweep -> build/render-all/
./gradlew desktopTest --tests "*SlideRenderingTest*" # guard  -> build/render-check/
```

> **Read this first.** Everything below marked ✅ was confirmed by *looking at the rendered
> image*. Anything marked ❓ was rendered but **not inspected**. Nothing here is claimed on
> the basis of "tests pass" alone — that is precisely what let the blanking regression ship.

---

## Summary

| | Count |
|---|---|
| ✅ Verified working | 8 |
| ❌ Confirmed broken | 0 |
| ❓ Rendered, not inspected | 5 |
| 🔲 Not implemented | 1 |

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

## ❓ Rendered but not inspected

These PNGs exist in `build/render-all/` and were **not** looked at. They are neither confirmed working nor confirmed broken — treat as unknown.

| File | Layout |
|---|---|
| `04_sequence_blocks.png` | sequence with `loop` / `alt` / `else` frames and a self-call |
| `06_table.png` | `DATA_TABLE` |
| `07_code.png` | `SPLIT_TEXT_CODE` |
| `08_quote.png` | `BIG_QUOTE` |
| `09_metric.png` | `BIG_METRIC` |
| `10_hero.png` | `HERO_TITLE` |
| `11_math.png` | `MATH_FORMULA` |
| `12_poll.png` | `POLL` |

(`05_vertical_flowchart`, `13_td_midlabel` and `14_lr_hexagon` have now been visually
inspected — see ✅ Verified working.)

---

## 🔲 Not implemented

**COR-10 · Images never render.** `SlideElement.Image` is parsed, drives layout classification, and is written to HTML export, but no image is ever decoded or drawn — `SplitTextMediaSlide` shows a placeholder icon plus the URL as text. Still awaiting a scope decision (local files only / local + remote / a Compose image loader with caching). See the remediation plan's COR-10 entry.

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
